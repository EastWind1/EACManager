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
- 登录写 `X-Auth-Token` httpOnly cookie（`path=/api`、`SameSite=Strict`、`Secure`）
- 授权是**动作级**：每个接口要求 `{module}:{action}` 权限码（如 `serviceBill:cancel-finish`）。
  Java 用 `@PreAuthorize` + `AuthenticateService.loadUserByUsername` 组装的 `authorities`（**故意不缓存**，
  改角色权限后下一次请求即生效）；Go 用 `security.Security.Middleware`。
- 无 ADMIN 用户时前端跳 `/user/init`；未登录的 API 请求返回 428。初始化用行锁防并发双建。
- `ADMIN` 必须始终保留 `permission:query` / `permission:update`（保存时校验），否则权限页会自锁。

## 数据库

15 张表，整型主键取自 `SEQUENCE`（步长 50），全表带审计列
`created_by_id / created_date / last_modified_by_id / last_modified_date`。

| 表 | 关键字段 |
|---|---|
| `sys_user` | 账户：`username`(unique)、`password`(bcrypt)、`disabled` — **无角色列、无资料字段** |
| `user_profile` | 用户资料：`user_id`(PK)、`name`、`phone`、`email`，由 user 模块维护 |
| `sys_role` / `sys_user_role` | 角色 `code`(unique)、`sort_order`；用户↔角色唯一约束 |
| `sys_permission` / `sys_role_permission` | 权限 `code`(unique)、`module`、`module_name`、`sort_order` |
| `company` | `name`、联系人、`address`、`disabled` |
| `service_bill` | `number`、`type`(INSTALL/FIX)、`state`、`total_amount`、项目与现场联系人、`product_company_id`、`version` |
| `service_bill_detail` | `device`、`quantity`、`unit_price`、`subtotal`、FK `service_bill_id` |
| `service_bill_assignee` | 服务单负责人子表，FK `service_bill_id` / `user_id`，唯一约束 `(service_bill_id, user_id)` |
| `reimbursement` / `reimburse_detail` | 报销单单头与明细（FK `reimbursement_id`） |
| `attachment` | `relative_path`(存储 key)、`type`、`status`(TEMP/TRANSIENT/PERSISTENT) |
| `service_bill_attach_relation` / `reimburse_attach_relation` | 各业务模块**各自拥有**的关联表 |

权限目录在启动时由各模块声明的清单 upsert 进 `sys_permission`（只增改，不删库中额外项）。

`deploy/database/`: `init_schema.sql` 建库；`migrate_attachment.sql`（拆分关联表 + `attachment.status`）、
`migrations/migrate_security.sql`（角色/权限重构）、
`migrations/migrate_service_bill_assignee.sql`（服务单负责人子表）与
`migrations/migrate_user_profile.sql`（账户/用户资料拆表）为存量库的一次性迁移。

### 附件模型

附件是 S3 形态而非文件系统形态：key 为 `{namespace}/{uuid}`uuid 由服务端生成，不含原始文件名），上传时生成，**永不移动**。
原始文件名上传时经 `validName` 校验（拒绝 `<>:"/\|?*`、单独 `..`、空名），同时作为
`attachment.name` 保存在数据库，下载与导出从数据库读取；导出目录使用单号、归档文件使用附件名，
Go 侧还会对拼接结果做 `EnsureWithin` 目录包含校验。
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
（`processed_date` / `finished_date`）。只有 `CREATED` 可删除。指派/取消指派只允许在
`CREATED` / `PROCESSING` 状态进行。单号 `S`/`R` + 雪花式 int id
（毫秒时间戳左移 5 位或上 1..31 序列），手填单号重复时拒绝。

## API

| 模块 | 接口 |
|---|---|
| `/api/user` | `POST /token`、`GET/POST /initialization`(public)、`PUT /logout`、`GET/POST/PUT /`、`DELETE /:username` |
| `/api/company` | `GET /`、`POST /`、`PUT /`、`DELETE /:id` |
| `/api/security` | `GET /permissions`、`GET/POST/PUT/DELETE /roles`、`GET/PUT /role/:id/permissions` |
| `/api/serviceBill` | `POST /query`、`GET /:id`、`POST /`、`PUT /`、`DELETE /`(批量)、`POST /import`、`PUT /process|processed|finish|cancel-process|cancel-processed|cancel-finish`、`PUT /assign`、`GET /assignees`、`POST /export`、`GET /countByState`、`GET /totalAmountGroupByMonth` |
| `/api/reimburse` | 同上（无 `cancel-processed`） |
| `/api/attachment` | `POST /upload`、`GET /`(下载) |

导出是 Excel 流；服务单导出把附件一起打成 zip。统计 `countByState` 按状态计数，
`totalAmountGroupByMonth` 按单据日期聚合（Go 侧有缓存）。

## 配置

**Java**（`application.yml`，需要 `DB_*` 与 `JWT_SECRET`≥32 字符）：虚拟线程、`open-in-view: false`、
`hibernate.jdbc.batch_size: 30`、multipart 5MB、附件目录 `${user.dir}/attachment`。
`JWT_SECRET` 没有默认值，缺失或过短时启动失败。

**Go**（`config/config.yaml`，Viper 依次找 `./config`、`.`、`../config`、`..`、`../../config`）：
`server.port`、`server.allowedOrigins`（空表示不启用 CORS）、`log.level`、`db.*`（含连接池上限）、
`cache.expire`、`jwt.*`、`attachment.*`、`ocr.url`。`jwt.secret` 不写默认值，必须通过
`JWT_SECRET` 环境变量注入，缺失或不足 32 字节时启动失败。

**OCR**：Java 本地跑 RapidOCR；Go 无原生中文 OCR，调 `ocr.url`（完整端点，默认
`http://localhost:9003/ocr`）的独立 FastAPI 服务 `ocr-server/`。

## 构建与测试

```bash
cd backend-java && mvn clean package -DskipTests   # 或 mvn test / verify
cd backend-go   && go build ./... && go test ./...
cd frontend     && pnpm install && pnpm dev        # pnpm build / type-check / lint
```

两端都是**集成测试，必须有可达的 PostgreSQL**，没有 mock 数据库层。Java 用 `@SpringBootTest` +
`@Transactional` 回滚（`common/BaseServiceTest`）；Go 测试集中在 `backend-go/test`，
`BaseSuite.SetupSuite` 懒初始化共享应用实例，走与 `server.Run` 相同的组装、权限同步和角色初始化路径，
每个用例的 `SetupTest` 开启事务，用例结束自动回滚。
Go 测试读同一份 `config.yaml`，只把附件目录改到系统临时目录。

## 约定

### 架构边界

- **查询与写动作分离**：查询、详情、导出读取和数据权限放在 QueryService；动作服务只做事务内写。动作服务需要按 id 读写时，复用查询服务的访问入口和权限判断，不再单独维护 DataScope。
- **数据权限只在查询边界处理**：列表、统计、导出由查询条件过滤；按 id 的写操作必须经过同一套权限判断，避免“列表看不到但按 id 能改”。
- **跨模块只通过 Service 协作**：不直接依赖其他模块的 Repository，也不使用 EntityManager 组装关联实体。
- **关联表使用标量 ID 显式维护**：负责人、附件等关系表只存 id，通过批量查询关联行再补 DTO；不建立 ORM 关联来“自动带出”。删除、替换和唯一性约束由业务显式处理。
- **鉴权统一收口 security**：账户、登录、token、禁用、角色权限属于安全模块；user 模块只维护用户资料。认证主体与账户信息尽量共用一个类型，响应 DTO 用继承或嵌入扩展，不复制字段。
- **避免过度抽象和死代码**：只有一个调用的辅助方法优先内联；删除未使用的 DTO、参数类型、方法和旧实现。保持模块边界干净比保留过渡层更重要。
- **公共契约保持稳定**：分页统一 PageResult + QueryParam，pageIndex 从 0 开始；批量写返回 ActionsResult；错误统一 BizException / errs.NewBizError；仓储只返回实体或投影 DTO，实体到接口 DTO 的转换放在 service/model。
- **注释只写“为什么”和约束**：不复述标识符、不写框架 API 说明、不记录过程性步骤。

### 数据访问

- **JPA**：明细是双向关联，写入前必须绑定父实体；更新按 id 对账，保留行原地更新、移除行真删除。@Version 不设默认值。关联加载用 EntityGraph 显式声明。批量删除用 JPQL，不依赖派生 deleteBy。主键保持 IDENTITY，与 Go 共用 sequence。
- **GORM**：统一使用泛型 API；整行写传指针，单列更新用 Select；动态条件从 Chain 累加，传给 gorm.G 的 DB 不能带子句；事务通过 context 传播；分页只在 BaseRepository.Page 做一次。服务层不直接 import GORM，复杂查询收在 store.go。
- **跨语言一致性**：Java 与 Go 共享数据库和 API，字段、状态、单号、分页和错误语义必须保持一致；任一端改契约都要同步另一端。

### 变更与验证

- 重构优先保持 API 和前端 DTO 形状不变；确需变更时同时更新两端、前端和文档。
- 涉及删列、拆表或数据回填的迁移必须显式说明执行顺序和影响，迁移前先备份。
- 结构调整后必须跑两端集成测试；Java 用 mvn clean test，避免删除类后的旧 class 残留；涉及前端时跑 pnpm run build。

## 代码地图

```
backend-java/src/main/java/pers/eastwind/billmanager/
  common/    Result / PageResult / QueryParam / ActionsResult / BizException / ControllerAdvice / Snowflake
  security/  AuthorizeService（角色/权限）、AuthenticateService（登录/token/禁用/账户）、JWT 过滤器
  user/      只维护 user_profile 资料；company/ servicebill/ reimburse/ attach/ 各自模块
  servicebill/service: ServiceBillQueryService 负责查询/数据权限，ServiceBillActionService 只做写动作
  {module}/permission/*PermissionProvider.java      模块声明的权限清单（启动时同步）

backend-go/
  cmd/main.go, server/server.go  入口与路由装配
  internal/{user,company,bill,reimburse,attach,security}/
      handler.go  service.go  store.go  model.go  permission.go
      stats: stat_handler.go / stat_svc.go / stat_store.go
      security: Security 聚合（Authorize / Authenticate / PermissionSync / Auth 中间件）、sync.go（权限同步 + 初始化）
      bill/reimburse 额外: *_rule.go（OCR 文本/表格映射）、mapper.go、attach_relation.go
      attach/reimburse/bill 以 Module 聚合多个对外服务；bill 查询/动作分离: query_service.go 负责查询与数据权限，service.go 只做写动作
      attach 额外: files.go / ocr.go / office.go / file_tx.go / content.go
  pkg/core/  App（config / cache / db / server / router 聚合）
  test/      集成测试环境（共享应用实例、事务回滚、上传辅助）
  pkg/       auth / cache / database / errs / logger / middleware / result / util
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
