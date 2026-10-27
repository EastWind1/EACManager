# AGENTS.md

电梯空调服务单管理系统：工单/报销单、OCR 导入、附件管理。Java 与 Go 两个后端共享 PostgreSQL 与 Vue 3 前端。
细节以代码为准，这里只记架构边界与容易踩错的约定。

## 架构

| | Java | Go |
|---|---|---|
| 栈 | Spring Boot 4.1 / Java 25 / JPA + Hibernate 7 | Fiber v3 + GORM |
| 端口 | 8080 | 8080 |
| 部署 | `deploy/compose-java.yml` | `deploy/compose.yml` |

两者暴露同一套 `/api`，响应统一 `{code, message, data}`，模块划分一致：Java 是
`controller → service → repository → model`（MapStruct 映射），Go 每个域一个包，内含
`handler.go` / `service.go` / `store.go` / `model.go`。

部署：`deploy/build.sh` 打包三者（Maven 解包 jar、交叉编译 Go、复制 Vite dist），compose 跑
postgres + backend + Caddy（静态文件、`/api` 反代、Let's Encrypt）。

## 认证与授权

- 公开接口只有 `POST /api/user/token` 与首管理员初始化（`GET/POST /api/user/initialization`）。
- 登录写 `X-Auth-Token` httpOnly cookie（`path=/api`、`SameSite=Strict`、`Secure`）；Go 另接受
  `Authorization: Bearer`。
- 授权是**动作级**：每个接口要求 `{module}:{action}` 权限码（如 `serviceBill:cancel-finish`）。
  Java 用 `@PreAuthorize` + `UserService.loadUserByUsername` 组装的 `authorities`（**故意不缓存**，
  改角色权限后下一次请求即生效）；Go 用 `security.Middleware`。
- 无 ADMIN 用户时前端跳 `/user/init`；未登录的 API 请求返回 428。初始化用行锁防并发双建。
- `ADMIN` 必须始终保留 `permission:query` / `permission:update`（保存时校验），否则权限页会自锁。

## 数据库

13 张表，整型主键取自 `SEQUENCE`（步长 50），全表带审计列
`created_by_id / created_date / last_modified_by_id / last_modified_date`。

| 表 | 关键字段 |
|---|---|
| `sys_user` | `username`(unique)、`name`、`password`(bcrypt)、`disabled` — **无角色列** |
| `sys_role` / `sys_user_role` | 角色 `code`(unique)、`sort_order`；用户↔角色唯一约束 |
| `sys_permission` / `sys_role_permission` | 权限 `code`(unique)、`module`、`module_name`、`sort_order` |
| `company` | `name`、联系人、`address`、`disabled` |
| `service_bill` | `number`、`type`(INSTALL/FIX)、`state`、`total_amount`、项目与现场联系人、`product_company_id`、`version` |
| `service_bill_detail` | `device`、`quantity`、`unit_price`、`subtotal`、FK `service_bill_id` |
| `reimbursement` / `reimburse_detail` | 报销单单头与明细（FK `reimbursement_id`） |
| `attachment` | `relative_path`(存储 key)、`type`、`status`(TEMP/TRANSIENT/PERSISTENT) |
| `service_bill_attach_relation` / `reimburse_attach_relation` | 各业务模块**各自拥有**的关联表 |

权限目录在启动时由各模块声明的清单 upsert 进 `sys_permission`（只增改，不删库中额外项）。

`deploy/database/`: `init_schema.sql` 建库；`migrate_attachment.sql`（拆分关联表 + `attachment.status`）
与 `migrations/migrate_security.sql`（角色/权限重构）为存量库的一次性迁移。

### 附件模型

附件是 S3 形态而非文件系统形态：key 为 `{namespace}/{uuid}{原始文件名}`，上传时生成，**永不移动**。
上传即写文件并插入 `TRANSIENT` 行；单据保存时用一条
`UPDATE ... WHERE status = TRANSIENT`（校验受影响行数）翻成 `PERSISTENT`，因此一个附件在结构上
只能归属一张单据。未被绑定的 `TRANSIENT` 行由每日任务按 7 天回收，不靠进程内 map。
`attach` 模块只提供 upload/persist/delete/download，只把 namespace 当不透明前缀，不碰任何关联表。
`relative_path` 是内部布局，**不得出现在 DTO / 响应里**。

## 状态机与单号

```
服务单 CREATED(0) → PROCESSING(1) → PROCESSED(2) → FINISHED(3)
报销单 CREATED(0) → PROCESSING(1) → FINISHED(2)
```

每次前进要求精确的前置状态；每个 `cancel-*` 只回退一格并清空对应日期列
（`processed_date` / `finished_date`）。只有 `CREATED` 可删除。单号 `S`/`R` + 雪花式 int id
（毫秒时间戳左移 5 位或上 1..31 序列），手填单号重复时拒绝。

## API

| 模块 | 接口 |
|---|---|
| `/api/user` | `POST /token`、`GET/POST /initialization`(public)、`PUT /logout`、`GET/POST/PUT /`、`DELETE /:username` |
| `/api/company` | `GET /`、`POST /`、`PUT /`、`DELETE /:id` |
| `/api/security` | `GET /permissions`、`GET/POST/PUT/DELETE /roles`、`GET/PUT /role/:id/permissions` |
| `/api/serviceBill` | `POST /query`、`GET /:id`、`POST /`、`PUT /`、`DELETE /`(批量)、`POST /import`、`PUT /process|processed|finish|cancel-process|cancel-processed|cancel-finish`、`POST /export`、`GET /countByState`、`GET /totalAmountGroupByMonth` |
| `/api/reimburse` | 同上（无 `cancel-processed`） |
| `/api/attachment` | `POST /upload`、`GET /`(下载) |

导出是 Excel 流；服务单导出把附件一起打成 zip。统计 `countByState` 按状态计数，
`totalAmountGroupByMonth` 按单据日期聚合（Go 侧有缓存）。

## 配置

**Java**（`application.yml`，需要 `DB_*` 与 `JWT_KEY`≥32 字符）：虚拟线程、`open-in-view: false`、
`hibernate.jdbc.batch_size: 30`、multipart 5MB、附件目录 `${user.dir}/attachment`。

**Go**（`config/config.yaml`，Viper 依次找 `./config`、`.`、`../config`、`..`、`../../config`）：
`server.port`、`log.level`、`db.*`（含连接池上限）、`cache.expire`、`jwt.*`、`attachment.*`、`ocr.url`。
注意：`viper.AutomaticEnv()` 未配 `BindEnv`，所以 compose 里设的 `DB_*` / `OCR_URL` **不生效**，改 YAML。

**OCR**：Java 本地跑 RapidOCR；Go 无原生中文 OCR，调 `ocr.url`（完整端点，默认
`http://localhost:9003/ocr`）的独立 FastAPI 服务 `ocr-server/`。

## 构建与测试

```bash
cd backend-java && mvn clean package -DskipTests   # 或 mvn test / verify
cd backend-go   && go build ./... && go test ./...
cd frontend     && pnpm install && pnpm dev        # pnpm build / type-check / lint
```

两端都是**集成测试，必须有可达的 PostgreSQL**，没有 mock 数据库层。Java 用 `@SpringBootTest` +
`@Transactional` 回滚（`common/BaseServiceTest`）；Go 每个包一个 `_test.go`，无 `TestMain`：
`test_util.AppCtx()` 懒初始化共享环境，每个用例 `test_util.BeginTx(t)` 返回携带事务的 context，用例结束自动回滚。
Go 测试读同一份 `config.yaml`，只把附件目录改到系统临时目录。

## 约定

- 批量写接口收 id 列表，返回 `ActionsResult`（逐项成败，不做整体回滚），每项独立事务。
- 分页统一 `PageResult` + `QueryParam`。**`pageIndex` 是 0 基**（对齐 Spring `PageRequest` 与前端
  `options.page - 1`）。
- 错误统一 `BizException` / `errs.NewBizError`，由 `ControllerAdvice` / `ErrorHandler` 包装。
- **仓储只返回实体和投影类 DTO**（聚合/统计行这类查询直接映射出的结构）。实体 → 接口 DTO 的转换
  放在 service / model 里：`store.go` 里不出现 `ToDTO()`，也不返回 `XxxDTO`。
- 注释只写「为什么」与约束；不复述标识符、不复述框架 API、不写过程性步骤标签。

## 易踩的坑

**JPA（Java）**

- 明细是双向关联：父 `@OneToMany(mappedBy="serviceBill", cascade=ALL, orphanRemoval=true)`，
  子 `@ManyToOne(LAZY)` 持有外键。因此服务在映射后必须 `bindDetails(...)`，否则外键为空。
  更新按 id 对账：保留行原地 UPDATE，移除行真 DELETE。
- `@Version` 字段**不要给默认值**，否则 Spring Data 认为是已持久化对象，`save()` 走 merge 而非 persist。
- 关联加载用 `@EntityGraph` 显式声明（列表取 `productCompany`、导出取 `details`），不要 EAGER。
- 聚合读用 JPQL 构造器投影到 record，不要 `Object[]` 解包。
- 批量删除用 `@Modifying @Query("delete from …")`；派生 `deleteBy…` 会先查再逐条删。
- 主键保持 `GenerationType.IDENTITY`：Go 侧共用同一批 sequence，改成 `SEQUENCE` 会与其分配冲突。

**GORM（Go）**

- 数据访问一律走泛型 API `gorm.G[T]`（含单列更新、联表、行锁）：读用值类型（直接得 `[]T`）；
  整行写用 `gorm.G[*T]` 并传指针；单列更新用 `Select("column").Updates(ctx, &T{...})`；
  联表用「子查询 + 外键 IN」；行锁用 `gorm.G[T](db, clause.Locking{Strength: "UPDATE"})`。
  不再使用 `Model/Table/Joins/Pluck/Scan/Clauses` 这类经典链式写法。
- 整行写必须传 `*T`：内嵌 `security.Audit` 的钩子要求可寻址值，值类型会报
  `invalid value, should be pointer to struct`。
- **`gorm.G[T](db)` 会丢弃 `db` 上已拼好的条件**（它用 `Session{NewDB:true}` 造空语句）。
  动态条件一律从 `r.Chain(ctx)` 累加；传给 `gorm.G` 的 `*gorm.DB` 必须无子句。
- **语句 context 取终止操作传入的那个**：`gorm.G[T](db).Find(ctx)` 会用 `ctx` 覆盖语句 context
  （审计钩子里的当前用户、取消与超时都来自它），链上 `WithContext` 挂的会被覆盖。所以仓储统一用
  `r.Ctx(ctx)` 取会话（优先取 context 里的事务）、并把同一个 `ctx` 传给终止调用。
- `Select` 限定更新列时，未列出的列不会进 `SET`：钩子照常执行、内存里的审计字段也有值，但会被
  **静默丢弃**。所以 `Audit.BeforeUpdate` 会在列清单非空时把 `last_modified_by_id` /
  `last_modified_date` 补进去，调用方只写业务列即可；列清单为空（不限制列）时钩子不追加，否则会变成
  只更新这两列。注意 `Select` 是按列名逐个匹配 schema 的，传逗号串会被当成一个未知列。
- 关联表批量写入用 `database.RelationBatchSize`（30），与 Java 的 `hibernate.jdbc.batch_size` 对齐。
- 分页只在 `BaseRepository.Page(ctx, chain, pageParam)` 做一次，且**只做分页**：计数、排序、偏移、
  组装结果。条件与预加载由调用方在**同一条链**上拼好传入——`Count` 不会触发 Preload（Preload 是
  独立的 `IN (...)` 查询），所以不需要区分「计数链」和「取数链」。排序用 `clause.OrderByColumn`
  表达，列名交给 GORM 按标识符加引号（不再自己拼 SQL、也不做字段白名单；非法列由数据库报错）；
  客户端传驼峰，这里只按命名约定转下划线。**分页但无排序时以 `id DESC` 兜底**；不分页时不注入排序。
- **事务放在 context 里**（`database.TxKey`）：`Transaction(ctx, fn func(ctx) error)` 把事务实例塞进
  回调的 ctx，之后的仓储调用凭同一个 ctx 自动加入事务。这样业务层只传 context，不接触 `*gorm.DB`，
  也就不会为了传递事务句柄而把 GORM 类型散到 service 里。测试同理：`test_util.BeginTx(t)` 直接返回
  携带事务的 ctx。
- `PrepareStmt` + `SkipDefaultTransaction` 已开，连接池有上限。**不要**开
  `postgres.Config.WithoutReturning`：批量插入会丢 `RETURNING`，所有 create 失败。
- 有唯一索引处（`username`、`role.code`、单号）不做「先查后插」，用 `database.IsDuplicateKey` 归一化错误。
- 服务层不 import GORM：行锁、条件更新、子查询都收在对应 `store.go` 的具名方法里，service 只拿
  context 调它们。

## 代码地图

```
backend-java/src/main/java/pers/eastwind/billmanager/
  common/    Result / PageResult / QueryParam / ActionsResult / BizException / ControllerAdvice / Snowflake
  security/  角色·权限实体与服务、PermissionSyncService、JWT 过滤器、权限枚举 provider
  user/ company/ servicebill/ reimburse/ attach/   各自 controller·service·repository·model·mapper
  {module}/permission/*PermissionProvider.java      模块声明的权限清单（启动时同步）

backend-go/
  cmd/main.go, server/server.go  入口与路由装配
  internal/{user,company,bill,reimburse,attach,security}/
      handler.go  service.go  store.go  model.go  permission.go
      stats: stat_handler.go / stat_svc.go / stat_store.go
      security: sync.go（权限同步 + 初始化）、middleware.go（请求级权限缓存）
      bill/reimburse 额外: *_rule.go（OCR 文本/表格映射）、mapper.go、attach_relation.go
      attach 额外: files.go / ocr.go / office.go / file_tx.go / content.go
  internal/test_util/   集成测试环境（共享 AppCtx、事务回滚、上传辅助）
  pkg/    auth / cache / context / database / errs / logger / middleware / result / util
  config/ config.go + config.yaml

frontend/src/
  common/  api/HttpClient.ts（去重、loading、401 跳转、解包 data、blob、进度）、
           directive/permission.ts（按动作码 display:none）、store、model、util/Crypto.ts
  {user,company,service-bill,reimburse,attachment,statistic,permission}/  api · model · view · component
  permission/                                                                角色×动作权限配置页

deploy/     compose*.yml、database/（init_schema、迁移）、frontend/（html、Caddyfile、cert）、build.sh
ocr-server/ Go 后端用的独立 FastAPI OCR 服务（uv 管理，PDF 先取文本层再降级 OCR）
```

命名：Java 用 `{Entity}{Role}`，词根 `ServiceBill` / `Reimburse` / `Attach`（不用
`Reimbursement`/`Attachment`，不用 `Biz`/`Rel` 缩写）。Go 用包名作命名空间，类型去掉实体前缀
（`bill.Service`、`bill.DTO`），实体保留表名语义并用 `TableName()` 固定。
