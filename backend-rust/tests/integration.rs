//! 集成测试：需要可达的 PostgreSQL。
//!
//! 测试库 `billmanager_rust_test` 每次运行整体重建；用例串行执行，HTTP 层与业务层共用同一套
//! 路由与中间件，验证与 Java/Go 一致的对外契约。

use std::sync::OnceLock;

use axum::Router;
use axum::body::Body;
use axum::http::{Method, Request, StatusCode, header};
use billmanager::config::{Config, DatabaseConfig};
use billmanager::server::create_app;
use billmanager::state::AppState;
use serde_json::{Value, json};
use tower::ServiceExt;

const TEST_DB: &str = "billmanager_rust_test";

/// 各用例共享同一个应用实例；连接池绑定了创建它的 runtime，因此全部场景必须跑在同一个
/// runtime 里（见文件末尾的 integration_contract），否则连接会随旧 runtime 一起失效。
static APP: OnceLock<Router> = OnceLock::new();

async fn app() -> Router {
    if let Some(app) = APP.get() {
        return app.clone();
    }
    let app = setup(TEST_DB).await.expect("初始化测试应用失败");
    if APP.set(app.clone()).is_err() {
        panic!("测试应用只能初始化一次");
    }
    app
}

async fn setup(db_name: &str) -> Result<Router, String> {
    // 让失败用例能看到服务端错误日志
    let _ = tracing_subscriber::fmt()
        .with_env_filter(
            tracing_subscriber::EnvFilter::try_from_default_env()
                .unwrap_or_else(|_| tracing_subscriber::EnvFilter::new("warn")),
        )
        .try_init();
    // 测试库独立于 Java/Go 使用的 dump 库
    unsafe { std::env::set_var("DB_NAME", db_name) };
    if std::env::var("JWT_SECRET").is_err() {
        unsafe {
            std::env::set_var(
                "JWT_SECRET",
                "test-only-jwt-key-4f6c1d2a9b8e7c3d5a1f0e9d8c7b6a5f",
            )
        };
    }
    let mut config = Config::load()?;
    // 附件目录放在 target 下，避免依赖系统临时目录的权限
    let temp = std::path::Path::new(env!("CARGO_TARGET_TMPDIR")).join("attachment");
    if let Err(error) = std::fs::remove_dir_all(&temp) {
        if error.kind() != std::io::ErrorKind::NotFound {
            return Err(format!("清理附件目录失败 {}: {error}", temp.display()));
        }
    }
    std::fs::create_dir_all(&temp)
        .map_err(|error| format!("创建附件目录失败 {}: {error}", temp.display()))?;
    config.attachment.path = temp.to_string_lossy().to_string();

    recreate_schema(&config, db_name).await?;
    let state = AppState::new(config)
        .await
        .map_err(|error| error.to_string())?;
    // 与 server::run 一致：先同步权限目录并建好 ADMIN/USER 角色
    billmanager::security::bootstrap(&state)
        .await
        .map_err(|error| error.to_string())?;
    let router = create_app(state).await.map_err(|error| error.to_string())?;
    Ok(router)
}

/// 确保测试库存在，随后清空 schema 并重建表
async fn recreate_schema(config: &Config, db_name: &str) -> Result<(), String> {
    let mut admin_config = config.db.clone();
    admin_config.name = "postgres".into();
    let admin = sqlx::postgres::PgPoolOptions::new()
        .max_connections(2)
        .connect(&dsn(&admin_config))
        .await
        .map_err(|e| e.to_string())?;
    let exists: Option<String> =
        sqlx::query_scalar("SELECT datname FROM pg_database WHERE datname = $1")
            .bind(db_name)
            .fetch_optional(&admin)
            .await
            .map_err(|e| e.to_string())?;
    if exists.is_none() {
        sqlx::query(&format!("CREATE DATABASE {db_name}"))
            .execute(&admin)
            .await
            .map_err(|e| e.to_string())?;
    }
    drop(admin);

    let pool = sqlx::postgres::PgPoolOptions::new()
        .max_connections(2)
        .connect(&dsn(&config.db))
        .await
        .map_err(|e| e.to_string())?;
    sqlx::query("DROP SCHEMA public CASCADE")
        .execute(&pool)
        .await
        .map_err(|e| e.to_string())?;
    sqlx::query("CREATE SCHEMA public")
        .execute(&pool)
        .await
        .map_err(|e| e.to_string())?;

    let schema = include_str!("schema.sql");
    for statement in schema.split(';') {
        let statement: String = statement
            .lines()
            .filter(|line| !line.trim_start().starts_with("--"))
            .collect::<Vec<_>>()
            .join(
                "
",
            );
        let statement = statement.trim();
        if statement.is_empty() {
            continue;
        }
        sqlx::query(statement).execute(&pool).await.map_err(|e| {
            format!(
                "建表语句失败: {e}
语句: {}",
                statement.chars().take(200).collect::<String>()
            )
        })?;
    }
    drop(pool);
    Ok(())
}

fn dsn(config: &DatabaseConfig) -> String {
    format!(
        "postgres://{}:{}@{}:{}/{}",
        config.username, config.password, config.host, config.port, config.name
    )
}

fn data(response: &Value) -> &Value {
    &response["data"]
}

/// 发起一次 HTTP 请求，返回状态码与解析后的 JSON
async fn call(
    app: &Router,
    method: Method,
    uri: &str,
    token: Option<&str>,
    body: Body,
) -> (StatusCode, Value) {
    let is_get = method == Method::GET;
    let mut builder = Request::builder().method(method).uri(uri);
    // 空字符串表示匿名请求，不要生成 "Bearer " 这样的残留头
    if let Some(token) = token.filter(|value| !value.is_empty()) {
        builder = builder.header(header::AUTHORIZATION, format!("Bearer {token}"));
    }
    if !is_get {
        builder = builder.header(header::CONTENT_TYPE, "application/json");
    }
    let request = builder.body(body).expect("构造请求失败");
    let response = app.clone().oneshot(request).await.expect("请求执行失败");
    let status = response.status();
    let bytes = axum::body::to_bytes(response.into_body(), usize::MAX)
        .await
        .expect("读取响应体失败");
    let value = serde_json::from_slice(&bytes).unwrap_or(Value::Null);
    (status, value)
}

async fn get(app: &Router, uri: &str, token: &str) -> (StatusCode, Value) {
    call(app, Method::GET, uri, Some(token), Body::empty()).await
}

async fn post_json(app: &Router, uri: &str, token: &str, payload: Value) -> (StatusCode, Value) {
    call(
        app,
        Method::POST,
        uri,
        Some(token),
        Body::from(payload.to_string()),
    )
    .await
}

async fn put_json(app: &Router, uri: &str, token: &str, payload: Value) -> (StatusCode, Value) {
    call(
        app,
        Method::PUT,
        uri,
        Some(token),
        Body::from(payload.to_string()),
    )
    .await
}

async fn delete_json(app: &Router, uri: &str, token: &str, payload: Value) -> (StatusCode, Value) {
    call(
        app,
        Method::DELETE,
        uri,
        Some(token),
        Body::from(payload.to_string()),
    )
    .await
}

const ADMIN_USERNAME: &str = "rustAdmin";
const ADMIN_PASSWORD: &str = "RustTest12345";

/// 初始化首个管理员并登录，返回 token
async fn admin_token(app: &Router) -> String {
    let (status, response) = get(app, "/api/user/initialization", "").await;
    assert_eq!(status, StatusCode::OK, "初始化状态查询失败: {response}");
    if data(&response)["required"].as_bool().unwrap_or(false) {
        let (status, response) = post_json(
            app,
            "/api/user/initialization",
            "",
            json!({
                "username": ADMIN_USERNAME,
                "password": ADMIN_PASSWORD,
                "name": "Rust测试管理员",
                "phone": "",
                "email": ""
            }),
        )
        .await;
        assert_eq!(status, StatusCode::OK, "初始化管理员失败: {response}");
    }
    let token = login(app, ADMIN_USERNAME, ADMIN_PASSWORD).await;
    assert!(!token.is_empty());
    token
}

/// 登录：token 只放在 httpOnly cookie 里，响应体是用户资料
async fn login(app: &Router, username: &str, password: &str) -> String {
    let request = Request::builder()
        .method(Method::POST)
        .uri("/api/user/token")
        .header(header::CONTENT_TYPE, "application/json")
        .body(Body::from(
            json!({ "username": username, "password": password }).to_string(),
        ))
        .expect("构造登录请求失败");
    let response = app.clone().oneshot(request).await.expect("登录请求失败");
    let status = response.status();
    let set_cookie = response
        .headers()
        .get(header::SET_COOKIE)
        .and_then(|value| value.to_str().ok())
        .unwrap_or_default()
        .to_string();
    let bytes = axum::body::to_bytes(response.into_body(), usize::MAX)
        .await
        .unwrap();
    let body: Value = serde_json::from_slice(&bytes).unwrap_or(Value::Null);
    assert_eq!(status, StatusCode::OK, "登录失败: {body}");
    let token = set_cookie
        .split(';')
        .find_map(|part| part.trim().strip_prefix("X-Auth-Token="))
        .expect("登录响应缺少 X-Auth-Token cookie")
        .to_string();
    assert!(
        set_cookie.contains("Path=/api") && set_cookie.contains("HttpOnly"),
        "cookie 属性不符合契约: {set_cookie}"
    );
    token
}

/// 建一张金额合法的服务单
fn bill_payload() -> Value {
    json!({
        "type": "INSTALL",
        "projectName": "集成测试项目",
        "projectAddress": "集成测试地址",
        "projectContact": "",
        "projectPhone": "",
        "totalAmount": 2000.0,
        "orderDate": "2026-01-01T00:00:00Z",
        "details": [
            { "device": "测试设备", "quantity": 2, "unitPrice": 1000.0, "subtotal": 2000.0, "remark": "" }
        ],
        "attachments": []
    })
}

// ---------------------------------------------------------------- 认证与初始化

async fn initialization_and_login_flow(app: &Router) {
    // 空库：未登录访问受保护接口返回 428，且带 initializationRequired
    let (status, response) = get(app, "/api/serviceBill/query", "").await;
    assert_eq!(status, StatusCode::PRECONDITION_REQUIRED);
    assert_eq!(data(&response)["initializationRequired"], json!(true));

    let token = admin_token(app).await;
    assert!(!token.is_empty());

    // 错误密码
    let (status, response) = post_json(
        app,
        "/api/user/token",
        "",
        json!({ "username": ADMIN_USERNAME, "password": "WrongPassword1" }),
    )
    .await;
    assert_eq!(status, StatusCode::BAD_REQUEST);
    assert_eq!(response["message"], json!("用户名或密码错误"));

    // 重复初始化被拒绝
    let (status, _) = post_json(
        app,
        "/api/user/initialization",
        "",
        json!({ "username": "another", "password": "Another12345", "name": "x" }),
    )
    .await;
    assert_eq!(status, StatusCode::BAD_REQUEST);

    // 登出
    let (status, _) = put_json(app, "/api/user/logout", &token, json!({})).await;
    assert_eq!(status, StatusCode::OK);
}

async fn permission_required_per_action(app: &Router) {
    let token = admin_token(app).await;

    // 管理员能看到完整权限目录，按模块分组
    let (status, response) = get(app, "/api/security/permissions", &token).await;
    assert_eq!(status, StatusCode::OK, "权限目录: {response}");
    let permissions = data(&response).as_object().expect("权限目录应为对象");
    assert!(permissions.contains_key("serviceBill"));
    assert!(permissions.contains_key("reimburse"));

    // 建一个只有 USER 角色的普通用户
    let (status, response) = post_json(
        app,
        "/api/user/",
        &token,
        json!({
            "username": "plainUser",
            "password": "PlainPass1234",
            "name": "普通用户",
            "phone": "13800138000",
            "email": "",
            "roles": [{ "code": "USER" }]
        }),
    )
    .await;
    assert_eq!(status, StatusCode::OK, "创建用户失败: {response}");

    let plain = login(app, "plainUser", "PlainPass1234").await;

    // 无 serviceBill:query → 403
    let (status, response) = post_json(
        app,
        "/api/serviceBill/query",
        &plain,
        json!({ "pageIndex": 0, "pageSize": 10 }),
    )
    .await;
    assert_eq!(status, StatusCode::FORBIDDEN);
    assert_eq!(response["message"], json!("没有权限"));

    // 无 user:create → 403
    let (status, _) = post_json(
        app,
        "/api/user/",
        &plain,
        json!({ "username": "hacker", "password": "HackerPass123", "name": "x" }),
    )
    .await;
    assert_eq!(status, StatusCode::FORBIDDEN);

    // 缺 token → 401
    let (status, response) = get(app, "/api/user/", "").await;
    assert_eq!(status, StatusCode::UNAUTHORIZED);
    assert_eq!(response["message"], json!("未登录"));

    // 非管理员只能看到自己
    let (status, _response) = get(app, "/api/user/?pageIndex=0&pageSize=10", &plain).await;
    assert_eq!(status, StatusCode::FORBIDDEN, "普通用户没有 user:query");
}

async fn user_crud_validates_and_protects_admin(app: &Router) {
    let token = admin_token(app).await;

    // 密码强度不足
    let (status, response) = post_json(
        app,
        "/api/user/",
        &token,
        json!({ "username": "weakUser", "password": "short", "name": "x" }),
    )
    .await;
    assert_eq!(status, StatusCode::BAD_REQUEST);
    assert!(response["message"].as_str().unwrap().contains("密码"));

    // 用户名重复
    let (status, _response) = post_json(
        app,
        "/api/user/",
        &token,
        json!({ "username": ADMIN_USERNAME, "password": "Another12345", "name": "x" }),
    )
    .await;
    assert_eq!(status, StatusCode::BAD_REQUEST);

    // 不能禁用自己
    let (status, response) = delete_json(
        app,
        &format!("/api/user/{ADMIN_USERNAME}"),
        &token,
        json!({}),
    )
    .await;
    assert_eq!(status, StatusCode::BAD_REQUEST);
    assert_eq!(response["message"], json!("不能禁用自己"));

    // 用户名不可修改
    let (status, response) = put_json(
        app,
        "/api/user/",
        &token,
        json!({ "id": 1, "username": "renamed", "name": "改名" }),
    )
    .await;
    assert_eq!(status, StatusCode::BAD_REQUEST);
    assert_eq!(response["message"], json!("用户名不能修改"));
}

// ---------------------------------------------------------------- 公司

async fn company_crud(app: &Router) {
    let token = admin_token(app).await;

    let (status, response) = post_json(
        app,
        "/api/company/",
        &token,
        json!({
            "name": "测试公司",
            "contactName": "张三",
            "contactPhone": "13800138000",
            "email": "a@b.com",
            "address": "地址"
        }),
    )
    .await;
    assert_eq!(status, StatusCode::OK, "创建公司失败: {response}");
    let id = data(&response)["id"].as_i64().expect("缺少公司 id");

    let (status, response) = get(app, "/api/company/?pageIndex=0&pageSize=10", &token).await;
    assert_eq!(status, StatusCode::OK);
    assert!(data(&response)["totalCount"].as_i64().unwrap_or(0) >= 1);

    let (status, response) = put_json(
        app,
        "/api/company/",
        &token,
        json!({
            "id": id,
            "name": "测试公司改",
            "contactName": "李四",
            "contactPhone": "13900139000",
            "email": "",
            "address": ""
        }),
    )
    .await;
    assert_eq!(status, StatusCode::OK, "更新公司失败: {response}");
    assert_eq!(data(&response)["contactName"], json!("李四"));

    // 手机号格式错误
    let (status, response) = post_json(
        app,
        "/api/company/",
        &token,
        json!({ "name": "格式公司", "contactPhone": "123" }),
    )
    .await;
    assert_eq!(status, StatusCode::BAD_REQUEST);
    assert_eq!(response["message"], json!("联系电话格式不正确"));

    // 分页参数只给一个 → 报错
    let (status, response) = get(app, "/api/company/?pageIndex=0", &token).await;
    assert_eq!(status, StatusCode::BAD_REQUEST);
    assert_eq!(response["message"], json!("分页参数不能只有一个为空"));

    // 禁用
    let (status, _) = call(
        app,
        Method::DELETE,
        &format!("/api/company/{id}"),
        Some(&token),
        Body::empty(),
    )
    .await;
    assert_eq!(status, StatusCode::OK);
    let (status, response) = get(app, "/api/company/?pageIndex=0&pageSize=50", &token).await;
    assert_eq!(status, StatusCode::OK);
    let found = data(&response)["items"]
        .as_array()
        .map(|items| items.iter().any(|item| item["id"] == json!(id)))
        .unwrap_or(false);
    assert!(!found, "已禁用公司不应出现在启用列表");
}

// ---------------------------------------------------------------- 服务单

async fn service_bill_lifecycle(app: &Router) {
    let token = admin_token(app).await;

    let (status, response) = post_json(app, "/api/serviceBill/", &token, bill_payload()).await;
    assert_eq!(status, StatusCode::OK, "创建服务单失败: {response}");
    let id = data(&response)["id"].as_i64().expect("缺少单据 id");
    assert!(data(&response)["number"].as_str().unwrap().starts_with('S'));
    assert_eq!(data(&response)["state"], json!("CREATED"));
    // 未指定负责人时默认指派给当前用户
    assert_eq!(
        data(&response)["assignees"].as_array().map(|a| a.len()),
        Some(1)
    );

    // 明细金额与总金额必须自洽
    let mut bad = bill_payload();
    bad["details"][0]["subtotal"] = json!(1500.0);
    let (status, response) = post_json(app, "/api/serviceBill/", &token, bad).await;
    assert_eq!(status, StatusCode::BAD_REQUEST);
    assert_eq!(response["message"], json!("明细金额有误"));

    let mut bad = bill_payload();
    bad["totalAmount"] = json!(1.0);
    let (status, response) = post_json(app, "/api/serviceBill/", &token, bad).await;
    assert_eq!(status, StatusCode::BAD_REQUEST);
    assert_eq!(response["message"], json!("总金额有误"));

    // 详情
    let (status, response) = get(app, &format!("/api/serviceBill/{id}"), &token).await;
    assert_eq!(status, StatusCode::OK);
    assert_eq!(data(&response)["projectName"], json!("集成测试项目"));
    assert_eq!(
        data(&response)["details"].as_array().map(|d| d.len()),
        Some(1)
    );

    // 列表查询
    let (status, response) = post_json(
        app,
        "/api/serviceBill/query",
        &token,
        json!({ "projectName": "集成测试", "pageIndex": 0, "pageSize": 10 }),
    )
    .await;
    assert_eq!(status, StatusCode::OK);
    assert!(data(&response)["totalCount"].as_i64().unwrap_or(0) >= 1);

    // 状态机 CREATED → PROCESSING → PROCESSED → FINISHED
    let (status, _) = put_json(app, "/api/serviceBill/process", &token, json!([id])).await;
    assert_eq!(status, StatusCode::OK);
    let (status, response) = put_json(
        app,
        "/api/serviceBill/processed",
        &token,
        json!({ "ids": [id], "processedDate": "2026-03-01T00:00:00Z" }),
    )
    .await;
    assert_eq!(status, StatusCode::OK);
    assert_eq!(data(&response)["successCount"], json!(1));
    let (status, _) = put_json(
        app,
        "/api/serviceBill/finish",
        &token,
        json!({ "ids": [id], "finishedDate": "2026-04-01T00:00:00Z" }),
    )
    .await;
    assert_eq!(status, StatusCode::OK);

    let (_status, response) = get(app, &format!("/api/serviceBill/{id}"), &token).await;
    assert_eq!(data(&response)["state"], json!("FINISHED"));
    assert!(data(&response)["processedDate"].is_string());
    assert!(data(&response)["finishedDate"].is_string());

    // 非法流转只记入 failCount，不影响其它项
    let (status, response) = put_json(app, "/api/serviceBill/process", &token, json!([id])).await;
    assert_eq!(status, StatusCode::OK);
    assert_eq!(data(&response)["failCount"], json!(1));
    assert!(
        data(&response)["results"][0]["message"]
            .as_str()
            .unwrap()
            .contains("非创建状态")
    );

    // 回退时清空对应日期列
    let (status, response) =
        put_json(app, "/api/serviceBill/cancel-finish", &token, json!([id])).await;
    assert_eq!(status, StatusCode::OK);
    assert_eq!(data(&response)["successCount"], json!(1));
    let (status, _) = put_json(
        app,
        "/api/serviceBill/cancel-processed",
        &token,
        json!([id]),
    )
    .await;
    assert_eq!(status, StatusCode::OK);
    let (status, _) = put_json(app, "/api/serviceBill/cancel-process", &token, json!([id])).await;
    assert_eq!(status, StatusCode::OK);

    let (_status, response) = get(app, &format!("/api/serviceBill/{id}"), &token).await;
    assert_eq!(data(&response)["state"], json!("CREATED"));
    assert!(data(&response)["processedDate"].is_null());
    assert!(data(&response)["finishedDate"].is_null());

    // 指派 / 取消指派
    let (status, response) = put_json(
        app,
        "/api/serviceBill/assign",
        &token,
        json!({ "ids": [id], "assigneeIds": [] }),
    )
    .await;
    assert_eq!(status, StatusCode::OK, "取消指派失败: {response}");
    assert_eq!(data(&response)["successCount"], json!(1));
    let (_status, response) = get(app, &format!("/api/serviceBill/{id}"), &token).await;
    assert_eq!(
        data(&response)["assignees"].as_array().map(|a| a.len()),
        Some(0)
    );

    // 统计
    let (status, response) = get(app, "/api/serviceBill/countByState", &token).await;
    assert_eq!(status, StatusCode::OK);
    assert!(data(&response)["CREATED"].as_i64().unwrap_or(0) >= 1);
    let (status, response) = get(app, "/api/serviceBill/totalAmountGroupByMonth", &token).await;
    assert_eq!(status, StatusCode::OK);
    assert!(data(&response).is_array());

    // 只有 CREATED 可删除
    let (status, response) = delete_json(app, "/api/serviceBill/", &token, json!([id])).await;
    assert_eq!(status, StatusCode::OK);
    assert_eq!(data(&response)["successCount"], json!(1));
    let (status, response) = get(app, &format!("/api/serviceBill/{id}"), &token).await;
    assert_eq!(status, StatusCode::BAD_REQUEST);
    assert_eq!(response["message"], json!("单据不存在"));
}

async fn service_bill_delete_requires_created_state(app: &Router) {
    let token = admin_token(app).await;

    let (status, response) = post_json(app, "/api/serviceBill/", &token, bill_payload()).await;
    assert_eq!(status, StatusCode::OK);
    let id = data(&response)["id"].as_i64().unwrap();
    let (status, _) = put_json(app, "/api/serviceBill/process", &token, json!([id])).await;
    assert_eq!(status, StatusCode::OK);

    let (status, response) = delete_json(app, "/api/serviceBill/", &token, json!([id])).await;
    assert_eq!(status, StatusCode::OK);
    assert_eq!(data(&response)["failCount"], json!(1));
    assert!(
        data(&response)["results"][0]["message"]
            .as_str()
            .unwrap()
            .contains("非创建状态不能删除")
    );

    // 手填单号不能包含路径分隔符
    let mut payload = bill_payload();
    payload["number"] = json!("BILL/TEST");
    let (status, response) = post_json(app, "/api/serviceBill/", &token, payload).await;
    assert_eq!(status, StatusCode::BAD_REQUEST);
    assert_eq!(response["message"], json!("单号不能包含 / \\"));
}

async fn service_bill_update_preserves_state(app: &Router) {
    let token = admin_token(app).await;

    let (status, response) = post_json(app, "/api/serviceBill/", &token, bill_payload()).await;
    assert_eq!(status, StatusCode::OK);
    let mut bill = data(&response).clone();
    let id = bill["id"].as_i64().unwrap();

    let (status, _) = put_json(app, "/api/serviceBill/process", &token, json!([id])).await;
    assert_eq!(status, StatusCode::OK);

    bill["projectName"] = json!("更新后的项目");
    bill["details"][0]["quantity"] = json!(3);
    bill["details"][0]["subtotal"] = json!(3000.0);
    bill["totalAmount"] = json!(3000.0);
    let (status, response) = put_json(app, "/api/serviceBill/", &token, bill).await;
    assert_eq!(status, StatusCode::OK, "更新服务单失败: {response}");
    assert_eq!(data(&response)["projectName"], json!("更新后的项目"));
    // 更新不改变状态
    assert_eq!(data(&response)["state"], json!("PROCESSING"));
    assert_eq!(
        data(&response)["details"].as_array().map(|d| d.len()),
        Some(1)
    );
    assert_eq!(data(&response)["totalAmount"], json!(3000.0));
}

async fn service_bill_export_returns_zip(app: &Router) {
    let token = admin_token(app).await;

    let (status, response) = post_json(app, "/api/serviceBill/", &token, bill_payload()).await;
    assert_eq!(status, StatusCode::OK);
    let id = data(&response)["id"].as_i64().unwrap();

    let request = Request::builder()
        .method(Method::POST)
        .uri("/api/serviceBill/export")
        .header(header::AUTHORIZATION, format!("Bearer {token}"))
        .header(header::CONTENT_TYPE, "application/json")
        .body(Body::from(json!([id]).to_string()))
        .expect("构造导出请求失败");
    let response = app.clone().oneshot(request).await.expect("导出失败");
    if response.status() != StatusCode::OK {
        let bytes = axum::body::to_bytes(response.into_body(), usize::MAX)
            .await
            .unwrap();
        panic!("导出失败: {}", String::from_utf8_lossy(&bytes));
    }
    assert_eq!(
        response.headers().get(header::CONTENT_TYPE).unwrap(),
        "application/octet-stream"
    );
    let bytes = axum::body::to_bytes(response.into_body(), usize::MAX)
        .await
        .unwrap();
    assert_eq!(&bytes[..2], b"PK", "导出内容不是 zip");
}

// ---------------------------------------------------------------- 报销单

fn reimburse_payload() -> Value {
    json!({
        "summary": "测试报销",
        "totalAmount": 1000.0,
        "reimburseDate": "2026-02-01T00:00:00Z",
        "remark": "备注",
        "details": [{ "name": "明细项目", "amount": 1000.0 }],
        "attachments": []
    })
}

async fn reimburse_lifecycle(app: &Router) {
    let token = admin_token(app).await;

    let (status, response) = post_json(app, "/api/reimburse/", &token, reimburse_payload()).await;
    assert_eq!(status, StatusCode::OK, "创建报销单失败: {response}");
    let id = data(&response)["id"].as_i64().unwrap();
    assert!(data(&response)["number"].as_str().unwrap().starts_with('R'));
    assert_eq!(data(&response)["state"], json!("CREATED"));

    let (status, response) = get(app, &format!("/api/reimburse/{id}"), &token).await;
    assert_eq!(status, StatusCode::OK);
    assert_eq!(data(&response)["summary"], json!("测试报销"));

    let (status, response) = post_json(
        app,
        "/api/reimburse/query",
        &token,
        json!({ "summary": "测试报销", "pageIndex": 0, "pageSize": 10 }),
    )
    .await;
    assert_eq!(status, StatusCode::OK);
    assert!(data(&response)["totalCount"].as_i64().unwrap_or(0) >= 1);

    // CREATED → PROCESSING → FINISHED
    let (status, response) = put_json(app, "/api/reimburse/process", &token, json!([id])).await;
    assert_eq!(status, StatusCode::OK);
    assert_eq!(data(&response)["successCount"], json!(1));
    let (status, _) = put_json(app, "/api/reimburse/finish", &token, json!([id])).await;
    assert_eq!(status, StatusCode::OK);
    let (_status, response) = get(app, &format!("/api/reimburse/{id}"), &token).await;
    assert_eq!(data(&response)["state"], json!("FINISHED"));

    // 非法流转
    let (status, response) = put_json(app, "/api/reimburse/process", &token, json!([id])).await;
    assert_eq!(status, StatusCode::OK);
    assert_eq!(data(&response)["failCount"], json!(1));

    // 回退
    let (status, response) =
        put_json(app, "/api/reimburse/cancel-finish", &token, json!([id])).await;
    assert_eq!(status, StatusCode::OK);
    assert_eq!(data(&response)["successCount"], json!(1));
    let (status, _) = put_json(app, "/api/reimburse/cancel-process", &token, json!([id])).await;
    assert_eq!(status, StatusCode::OK);
    let (_status, response) = get(app, &format!("/api/reimburse/{id}"), &token).await;
    assert_eq!(data(&response)["state"], json!("CREATED"));

    // 更新：前端会回传单号，单号变更时要重新校验
    let (status, detail) = get(app, &format!("/api/reimburse/{id}"), &token).await;
    assert_eq!(status, StatusCode::OK);
    let mut update = data(&detail).clone();
    update["summary"] = json!("更新后的摘要");
    update["totalAmount"] = json!(2000.0);
    update["details"][0]["amount"] = json!(2000.0);
    let (status, response) = put_json(app, "/api/reimburse/", &token, update).await;
    assert_eq!(status, StatusCode::OK, "更新报销单失败: {response}");
    assert_eq!(data(&response)["summary"], json!("更新后的摘要"));
    assert_eq!(data(&response)["totalAmount"], json!(2000.0));

    // 统计
    let (status, response) = get(app, "/api/reimburse/countByState", &token).await;
    assert_eq!(status, StatusCode::OK);
    assert!(data(&response)["CREATED"].as_i64().unwrap_or(0) >= 1);
    let (status, response) = get(app, "/api/reimburse/totalAmountGroupByMonth", &token).await;
    assert_eq!(status, StatusCode::OK);
    assert!(data(&response).is_array());

    // 导出
    let (status, _) = post_json(app, "/api/reimburse/export", &token, json!([id])).await;
    assert_eq!(status, StatusCode::OK);

    // 删除
    let (status, response) = delete_json(app, "/api/reimburse/", &token, json!([id])).await;
    assert_eq!(status, StatusCode::OK);
    assert_eq!(data(&response)["successCount"], json!(1));
}

// ---------------------------------------------------------------- 附件

fn multipart_body(boundary: &str, namespace: Option<&str>, filename: &str, content: &str) -> Body {
    let mut body = String::new();
    if let Some(namespace) = namespace {
        body.push_str(&format!(
            "--{boundary}\r\nContent-Disposition: form-data; name=\"namespace\"\r\n\r\n{namespace}\r\n"
        ));
    }
    body.push_str(&format!(
        "--{boundary}\r\nContent-Disposition: form-data; name=\"files\"; filename=\"{filename}\"\r\n\
         Content-Type: text/plain\r\n\r\n{content}\r\n--{boundary}--\r\n"
    ));
    Body::from(body)
}

async fn upload(
    app: &Router,
    token: &str,
    namespace: Option<&str>,
    filename: &str,
) -> (StatusCode, Value) {
    let boundary = "----eacrustboundary";
    let request = Request::builder()
        .method(Method::POST)
        .uri("/api/attachment/upload")
        .header(header::AUTHORIZATION, format!("Bearer {token}"))
        .header(
            header::CONTENT_TYPE,
            format!("multipart/form-data; boundary={boundary}"),
        )
        .body(multipart_body(
            boundary,
            namespace,
            filename,
            "attach content",
        ))
        .expect("构造上传请求失败");
    let response = app.clone().oneshot(request).await.expect("上传失败");
    let status = response.status();
    let bytes = axum::body::to_bytes(response.into_body(), usize::MAX)
        .await
        .unwrap();
    (
        status,
        serde_json::from_slice(&bytes).unwrap_or(Value::Null),
    )
}

async fn attachment_upload_download_and_persist(app: &Router) {
    let token = admin_token(app).await;

    let (status, response) = upload(app, &token, Some("SERVICE_BILL"), "服务单附件.txt").await;
    assert_eq!(status, StatusCode::OK, "上传失败: {response}");
    let attach_id = data(&response)[0]["id"].as_i64().expect("缺少附件 id");
    assert_eq!(data(&response)[0]["name"], json!("服务单附件.txt"));
    // relativePath 不得出现在响应里
    assert!(data(&response)[0].get("relativePath").is_none());

    // 下载：文件名来自数据库，type 参数只是前端回传
    let (status, _) = get(
        app,
        &format!("/api/attachment/?id={attach_id}&name=x.txt&type=OTHER"),
        &token,
    )
    .await;
    assert_eq!(status, StatusCode::OK, "下载失败");

    // 绑定到服务单后翻成 PERSISTENT
    let mut payload = bill_payload();
    payload["attachments"] =
        json!([{ "id": attach_id, "name": "服务单附件.txt", "type": "OTHER" }]);
    let (status, response) = post_json(app, "/api/serviceBill/", &token, payload).await;
    assert_eq!(status, StatusCode::OK, "创建带附件的服务单失败: {response}");
    let bill_id = data(&response)["id"].as_i64().unwrap();
    assert_eq!(
        data(&response)["attachments"].as_array().map(|a| a.len()),
        Some(1)
    );

    // 已提交的附件不能再挂到第二张单据
    let mut reuse = bill_payload();
    reuse["attachments"] = json!([{ "id": attach_id, "name": "服务单附件.txt", "type": "OTHER" }]);
    let (status, response) = post_json(app, "/api/serviceBill/", &token, reuse).await;
    assert_eq!(status, StatusCode::BAD_REQUEST);
    assert!(
        response["message"]
            .as_str()
            .unwrap()
            .contains("附件不存在或已被提交"),
        "意外消息: {response}"
    );

    // 移除附件：关联行与附件记录一并删除
    let (status, response) = get(app, &format!("/api/serviceBill/{bill_id}"), &token).await;
    assert_eq!(status, StatusCode::OK);
    let mut bill = data(&response).clone();
    bill["attachments"] = json!([]);
    let (status, response) = put_json(app, "/api/serviceBill/", &token, bill).await;
    assert_eq!(status, StatusCode::OK, "更新失败: {response}");
    assert_eq!(
        data(&response)["attachments"].as_array().map(|a| a.len()),
        Some(0)
    );

    let (status, _) = get(app, &format!("/api/attachment/?id={attach_id}"), &token).await;
    assert_eq!(status, StatusCode::BAD_REQUEST);
}

async fn attachment_upload_rejects_bad_input(app: &Router) {
    let token = admin_token(app).await;

    // 缺命名空间
    let (status, response) = upload(app, &token, None, "a.txt").await;
    assert_eq!(status, StatusCode::BAD_REQUEST);
    assert_eq!(response["message"], json!("附件命名空间不能为空"));

    // 文件名带路径分隔符
    let (status, response) = upload(app, &token, Some("TEST_ATTACH"), "a/b.txt").await;
    assert_eq!(status, StatusCode::BAD_REQUEST);
    assert!(
        response["message"]
            .as_str()
            .unwrap()
            .contains("文件名不能包含")
    );

    // 命名空间必须是简单标识符
    let (status, response) = upload(app, &token, Some("../escape"), "a.txt").await;
    assert_eq!(status, StatusCode::BAD_REQUEST);
    assert_eq!(response["message"], json!("附件命名空间不合法"));
}

// ---------------------------------------------------------------- 角色与权限

async fn role_and_permission_management(app: &Router) {
    let token = admin_token(app).await;

    let (status, response) = post_json(
        app,
        "/api/security/role",
        &token,
        json!({ "code": "OPS", "name": "运维", "sortOrder": 10 }),
    )
    .await;
    assert_eq!(status, StatusCode::OK, "创建角色失败: {response}");
    let role_id = data(&response)["id"].as_i64().unwrap();

    // 编码必须大写
    let (status, _) = post_json(
        app,
        "/api/security/role",
        &token,
        json!({ "code": "ops", "name": "x" }),
    )
    .await;
    assert_eq!(status, StatusCode::BAD_REQUEST);

    let (status, response) = get(app, "/api/security/roles", &token).await;
    assert_eq!(status, StatusCode::OK);
    assert!(data(&response).as_array().unwrap().len() >= 2);

    // 取全部权限 id
    let (status, response) = get(app, "/api/security/permissions", &token).await;
    assert_eq!(status, StatusCode::OK);
    let mut permission_ids: Vec<i64> = Vec::new();
    for entries in data(&response).as_object().unwrap().values() {
        for entry in entries.as_array().unwrap() {
            permission_ids.push(entry["id"].as_i64().unwrap());
        }
    }
    assert!(!permission_ids.is_empty());

    let (status, response) = put_json(
        app,
        &format!("/api/security/role/{role_id}/permissions"),
        &token,
        json!(permission_ids),
    )
    .await;
    assert_eq!(status, StatusCode::OK, "授权失败: {response}");
    assert_eq!(
        data(&response).as_array().unwrap().len(),
        permission_ids.len()
    );

    // 未知权限被拒绝
    let (status, response) = put_json(
        app,
        &format!("/api/security/role/{role_id}/permissions"),
        &token,
        json!([99999999]),
    )
    .await;
    assert_eq!(status, StatusCode::BAD_REQUEST);
    assert!(
        response["message"]
            .as_str()
            .unwrap()
            .contains("存在未知权限")
    );

    // 删除不存在的角色
    let (status, response) = call(
        app,
        Method::DELETE,
        "/api/security/role/999999",
        Some(&token),
        Body::empty(),
    )
    .await;
    assert_eq!(status, StatusCode::BAD_REQUEST);
    assert_eq!(response["message"], json!("角色不存在"));

    // 删除刚建的角色
    let (status, _) = call(
        app,
        Method::DELETE,
        &format!("/api/security/role/{role_id}"),
        Some(&token),
        Body::empty(),
    )
    .await;
    assert_eq!(status, StatusCode::OK);
}
// ---------------------------------------------------------------- 统一入口

/// 所有场景共用一个 runtime 与一份连接池，按顺序执行，失败即停并打印场景名。
#[test]
fn integration_contract() {
    let runtime = tokio::runtime::Builder::new_multi_thread()
        .worker_threads(2)
        .enable_all()
        .build()
        .expect("创建测试 runtime 失败");
    runtime.block_on(async {
        let app = &app().await;
        run(
            "initialization_and_login_flow",
            initialization_and_login_flow(app),
        )
        .await;
        run(
            "permission_required_per_action",
            permission_required_per_action(app),
        )
        .await;
        run(
            "user_crud_validates_and_protects_admin",
            user_crud_validates_and_protects_admin(app),
        )
        .await;
        run("company_crud", company_crud(app)).await;
        run("service_bill_lifecycle", service_bill_lifecycle(app)).await;
        run(
            "service_bill_delete_requires_created_state",
            service_bill_delete_requires_created_state(app),
        )
        .await;
        run(
            "service_bill_update_preserves_state",
            service_bill_update_preserves_state(app),
        )
        .await;
        run(
            "service_bill_export_returns_zip",
            service_bill_export_returns_zip(app),
        )
        .await;
        run("reimburse_lifecycle", reimburse_lifecycle(app)).await;
        run(
            "attachment_upload_download_and_persist",
            attachment_upload_download_and_persist(app),
        )
        .await;
        run(
            "attachment_upload_rejects_bad_input",
            attachment_upload_rejects_bad_input(app),
        )
        .await;
        run(
            "role_and_permission_management",
            role_and_permission_management(app),
        )
        .await;
    });
}

async fn run(name: &str, scenario: impl std::future::Future<Output = ()>) {
    println!("--- 场景: {name}");
    scenario.await;
    println!("--- 通过: {name}");
}
