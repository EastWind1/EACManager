use axum::Router;
use tower_http::trace::TraceLayer;

use crate::attach::service::spawn_cleaner;
use crate::error::AppResult;
use crate::security::bootstrap;
use crate::state::AppState;

pub async fn create_app(state: AppState) -> AppResult<Router> {
    // 公开接口（登录、初始化、健康检查）由认证中间件按路径白名单放行
    let app = Router::new()
        .merge(Router::new().route("/api/health", axum::routing::get(|| async { "OK" })))
        .merge(crate::user::router())
        .merge(crate::company::router())
        .merge(crate::bill::router())
        .merge(crate::reimburse::router())
        .merge(crate::attach::router())
        .merge(crate::security::router())
        .layer(axum::middleware::from_fn({
            let state = state.clone();
            // 用闭包注入状态，避免 Request 跨 await 让中间件 future 失去 Send
            move |request, next| {
                let state = state.clone();
                async move { crate::security::middleware::authenticate(state, request, next).await }
            }
        }))
        .layer(TraceLayer::new_for_http())
        .with_state(state);

    Ok(app)
}

pub async fn run(state: AppState) -> AppResult<()> {
    // 权限同步与管理员初始化
    bootstrap(&state).await?;

    let port = state.config.server.port;
    // 定时清理瞬时附件
    spawn_cleaner(state.clone());

    let app = create_app(state).await?;

    let addr = format!("0.0.0.0:{port}");
    let listener = tokio::net::TcpListener::bind(&addr).await?;
    tracing::info!("Server listening on {}", addr);
    axum::serve(listener, app).await?;
    Ok(())
}
