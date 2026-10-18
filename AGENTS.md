# AGENTS.md

Agent guidance for this repository. Details live in the code — this file is the map.

## Project Overview

Elevator AC After-Sale Manager (电梯空调服务单管理系统) — B/S system for installation/maintenance
work orders, expense reports, OCR import and attachment management. Java **and** Go backends,
shared PostgreSQL schema and Vue 3 frontend.

## Architecture

| | Java backend | Go backend |
|---|---|---|
| Stack | Spring Boot 4.1 + Java 25 + JPA/Hibernate 7 | Fiber v3 + GORM |
| Port | 8080 | 8080 |
| Deploy | `deploy/compose-java.yml` | `deploy/compose.yml` |

Both expose the same API under `/api`, wrapped as `{code, message, data}`, and share the same
per-module layering. Java follows `controller → service → repository → model` with MapStruct
mappers; Go flattens each domain into one package with `handler.go` / `service.go` / `store.go` /
`model.go`.

**Auth (identical contract):** `POST /api/user/token` is the only public endpoint; it sets an
`X-Auth-Token` httpOnly cookie (`path=/api`, `SameSite=Strict`, `Secure`) and returns the user.
Go additionally accepts `Authorization: Bearer <token>`. Java enforces roles with
`@PreAuthorize`, Go with `auth.RoleMiddleware` (this is why some Go routes differ — see below).

**Modules:** `user` (login/JWT), `company`, `servicebill`/`bill` (work orders + statistics),
`reimburse` (expense reports + statistics), `attach` (upload/OCR/export). Statistics live inside
each business module — no standalone `statistic` backend module; only the dashboard frontend is
`frontend/src/statistic/`.

**Frontend:** Vue 3 + Vuetify 4 (`zhHans`, dark-mode aware) + Pinia + Vue Router, built with Vite.
Routes: `/login`, `/dashboard`, `/user`, `/company`, `/services`, `/service/:id?`, `/reimburses`,
`/reimburse/:id?`, plus a catch-all 404. `common/api/HttpClient.ts` wraps `axios` (request dedup
via `AbortController`, loading bar, 401→login redirect, 403/500 toasts, unwraps `data`; binary
endpoints pass `responseType: 'blob'`; `onDownloadProgress` / `onUploadProgress` report progress).
`v-role` hides elements by setting `display: none` (it does not unmount them).

## Database (PostgreSQL)

9 tables, integer PKs from `SEQUENCE`s (increment 50), audit columns on all: `created_by_id`,
`created_date`, `last_modified_by_id`, `last_modified_date`.

| Table | Key fields |
|---|---|
| `sys_user` | `username` (unique), `name`, `password` (bcrypt), `phone`, `email`, `authority`, `disabled` |
| `company` | `name`, `contact_name`, `contact_phone`, `address`, `email`, `disabled` |
| `service_bill` | `number`, `type` (INSTALL=0/FIX=1), `state`, `total_amount`, `project_name`, `project_address`, `project_contact(_phone)`, `elevator_info`, `on_site_contact`, `on_site_phone`, `order_date`, `processed_date`, `finished_date`, `remark`, `product_company_id`, `version` |
| `service_bill_detail` | `device`, `quantity`, `unit_price`, `subtotal`, `remark`, FK `service_bill_id` |
| `reimbursement` | `number`, `state`, `total_amount`, `reimburse_date`, `summary`, `remark`, `version` |
| `reimburse_detail` | `name`, `amount`, FK `reimbursement_id` |
| `attachment` | `name`, `relative_path` (存储 key), `type` (IMAGE=0/PDF=1/WORD=2/EXCEL=3/OTHER=4), `status` (TEMP=0/TRANSIENT=1/PERSISTENT=2) |
| `service_bill_attach_relation` | `service_bill_id`, `attach_id` — owned by the service-bill module |
| `reimburse_attach_relation` | `reimburse_id`, `attach_id` — owned by the reimburse module |

`authority` ∈ ROLE_ADMIN / ROLE_USER / ROLE_FINANCE / ROLE_GUEST. `version` is the Go
optimistic-lock column. Indexes exist on `service_bill(number, created_date)`,
`reimbursement(number, reimburse_date)`, `sys_user(username)`,
`service_bill_attach_relation(service_bill_id)`, `reimburse_attach_relation(reimburse_id)`,
`attachment(status)`.

**Attachments are S3-shaped, not filesystem-shaped.** The storage key is
`{namespace}/{uuid}{original-file-name}` — generated once at upload and **never moved**, so there is
no cross-system "update the row and move the file" operation to make atomic. `POST /upload` writes
the file *and* inserts the `attachment` row as `TRANSIENT` in one step; committing a bill flips the
row to `PERSISTENT` with a single conditional `UPDATE ... WHERE status = TRANSIENT` (affected-rows
checked), which also makes an attachment committable exactly once — i.e. it structurally belongs to
one bill. `TEMP` is reserved for attachments managed from the temp directory and is not written by
the current upload path. Expired `TRANSIENT` rows are reclaimed by a scheduled status scan (7 days,
`cleanTransientFiles()`), not by an in-process map, so a restart no longer leaks orphans.

Bill↔attachment links are **not** shared: each business module owns its own relation table and
repository. The `attach` module exposes only `upload` / `persist` / `delete` plus download, knows no
document concept (the namespace is an opaque string supplied by the caller), and never touches a
relation table. There is no `bill_type` discriminator column.
Each business module diffs its own link rows against the submitted set as a plain set operation —
no temp-id sign convention, no path building, no file operations.

`deploy/database/migrate_attachment.sql` is the one-off migration for all of the above: it splits
the old shared `bill_attach_relation` into the two per-module tables **and** adds
`attachment.status` (existing rows become `PERSISTENT`). It moves no files — legacy
`SERVICE_BILL/<number>/…` paths stay valid — and aborts loudly if it finds a partially migrated
database.

`attachment.relative_path` is internal storage layout — never expose it in DTOs or API responses.
`service_bill_detail` / `reimburse_detail` own no audit columns and are cascade-deleted with their
parent. Default admin `root` is seeded by `deploy/database/init_user.sql` (scheme in
`init_scheme.sql`).

### Service bill state machine

```
CREATED(0) ─process→ PROCESSING(1) ─processed→ PROCESSED(2) ─finish→ FINISHED(3)
     ←cancel-process─      ←cancel-processed─      ←cancel-finish─
```

Each forward transition requires the exact prior state; each `cancel-*` steps back exactly one
state and clears the matching date field (`processed_date` / `finished_date`). Only `CREATED` bills
are deletable. Numbers: `S` + `genIntID()` (26-bit millisecond stamp shifted left 5 bits OR'd with a
1..31 per-millisecond sequence — `common/util/Snowflake` in Java, `pkg/util.GenIntID` in Go);
duplicate numbers are rejected when supplied manually.

### Reimbursement state machine

```
CREATED(0) ─process→ PROCESSING(1) ─finish→ FINISHED(2)
     ←cancel-process─       ←cancel-finish─
```

Same one-step revert rule; only `CREATED` is deletable. Numbers: `R` + `genIntID()`.

## API Surface

Roles: A=ADMIN, U=USER, F=FINANCE. "auth" means any authenticated role.

| Module | Endpoints |
|---|---|
| `/api/user` | `POST /token` (public) · `PUT /logout` · `GET /` A/U/F · `POST /` A · `PUT /` A/U/F · `DELETE /:username` (disable) A |
| `/api/company` | `GET /` A/U · `POST /` A · `PUT /` A · `DELETE /:id` (disable) A |
| `/api/serviceBill` | `POST /query` · `GET /:id` · `POST /` · `PUT /` · `DELETE /` (batch, CREATED only) · `POST /import` (OCR) · `PUT /process` `processed` `finish` `cancel-process` `cancel-processed` `cancel-finish` · `POST /export` · `GET /countByState` · `GET /totalAmountGroupByMonth` |
| `/api/reimburse` | `POST /query` · `GET /:id` · `POST /` · `PUT /` · `DELETE /` · `POST /import` (invoice OCR → prefilled DTO) · `PUT /process` `finish` `cancel-process` `cancel-finish` · `POST /export` · `GET /countByState` · `GET /totalAmountGroupByMonth` |
| `/api/attachment` | `POST /upload` (namespace + files → attachment DTOs, rows created as `TRANSIENT`) · `GET /` (download, attachment DTO as query params) |

Business endpoints are `auth` unless noted; Go tightens several: bill `cancel-*` are A-only,
company `POST`/`DELETE` are A-only, user `GET`/`PUT` are A/U/F. Go's company `PUT /` currently
registers **no** role middleware — treat that as a gap, not a contract.

Attachments have no standalone delete/OCR endpoint. The frontend uploads with
`POST /api/attachment/upload?namespace=…` (the namespace must match the backend constant
`ServiceBillAttachService.ATTACH_NAMESPACE` / `ReimburseAttachService.ATTACH_NAMESPACE`,
i.e. `SERVICE_BILL` or `REIMBURSEMENT`); the row is created as `TRANSIENT` right away. When the bill is
saved, the business module diffs the submitted ids against its own link rows and calls
`persist(added)` / `delete(removed)` on the attach module — no paths, no file operations, no temp-id
convention. Uploads that are never bound stay `TRANSIENT` and are reclaimed by the scheduled status scan
(`cleanTransientFiles()` in Java, a daily goroutine in Go); `FileTxUtil` / `file_tx.go` now only ever
soft-delete files. Exports are Excel streams; the service-bill export bundles attachments as a zip.

Statistics: `countByState` returns per-state counts; `totalAmountGroupByMonth` sums by
`processed_date` (bills) / `reimburse_date` (reimbursements) and is cached in Go.

## Configuration

**Java** (`backend-java/src/main/resources/application.yml`) — requires env `DB_HOST`, `DB_PORT`,
`DB_NAME`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_KEY` (≥32 ASCII chars). Virtual threads on, multipart
and `config.jwt.expire=604800` (7d), attachment dir `${user.dir}/attachment`. Local dev credentials
live in `application-dev.yml` (profile `dev`, which tests activate).

**Go** (`backend-go/config/config.yaml`, loaded by Viper from `./config`, `.`, `../config`, `..`,
`../../config`) — `server.port`, `log.level`, `db.*`, `cache.expire=86400`,
`jwt.secret`/`jwt.expire=604800`, `attachment.path=attachment`, `attachment.maxFileSize=5242880`,
`ocr.url`. Note: `viper.AutomaticEnv()` is called without `BindEnv`, so `db.*` / `ocr.url` are **not**
actually overridable by the `DB_*` / `OCR_URL` env vars that `deploy/compose.yml` sets — edit the
YAML.

**Upload limit is 5 MB** on both backends (`spring.servlet.multipart.max-file-size` and Fiber
`BodyLimit` / `attachment.maxFileSize`).

**OCR:** Java runs RapidOCR locally (ONNX, Windows + Linux native deps, Chinese). Go has no native
Chinese OCR and calls a remote HTTP API instead — `ocr.url` is the **full endpoint**, default
`http://localhost:9003/ocr`, and Go posts a single `file` multipart field and reads
`{code, msg, data}` with `data` as `[]string` (one entry per text line). That server is
`ocr-server/` — a FastAPI app (Python ≥3.14, managed with `uv`, `rapidocr` + `pdfplumber`) whose
`ocr_server/__init__.py` entry point takes `-p/--port` (default 9003). For PDFs it extracts text
with pdfplumber and only falls back to OCR on the embedded image when no text layer exists. It is
commented out in `compose.yml` by default; `deploy/build.sh` copies it to `deploy/ocr-server`.

## Build & Test

```bash
# Java backend
cd backend-java && mvn clean package -DskipTests   # or: mvn test / mvn verify

# Go backend
cd backend-go && go build -ldflags="-s -w" -o target/backend-go cmd/main.go && go test ./...

# Frontend (dev server proxies /api → localhost:8080)
cd frontend && pnpm install && pnpm dev            # pnpm build / type-check / lint / format

# Full deployment bundle + containers
cd deploy && ./build.sh                            # or build.bat on Windows
docker compose -f compose-java.yml up -d           # Java; compose.yml for Go
```

`deploy/build.sh` packages all three: Maven explodes the Java jar with
`-Djarmode=tools extract` into `deploy/backend-java` (dependencies kept separate, not a fat jar),
cross-compiles the Go binary into `deploy/backend-go`, and copies the Vite `dist` into
`deploy/frontend/html`. Compose then runs postgres + backend + Caddy (static files, `/api` reverse
proxy, automatic Let's Encrypt HTTPS via `DOMAIN` / `CA_EMAIL`).

**Tests:** Java — JUnit 5 on `@SpringBootTest` + `@Transactional` (per-test rollback), base class
`common/BaseServiceTest`, Mockito for collaborators, profile `dev`. Go — **one `_test.go` per
package**, named after it (`internal/bill/bill_test.go`, likewise `reimburse`, `company`, `user`,
`attach`), each holding every suite for that module in package `bill_test` etc. There is **no shared
base struct and no `TestMain`**: `internal/testutil` is package-level support, not a superclass —
`testutil.AppCtx()` lazily initialises the shared environment once per test binary (one connection
pool / logger / config) on first use, each suite's `SetupSuite` wires its own services into suite
fields, and each `SetupTest` opens its own transaction with `s.ctx = testutil.BeginTx(s.T())` (plus
per-test fixtures), which registers the rollback via `t.Cleanup` so a forgotten teardown cannot leak
data. `testutil.Tx(t, ctx)` exposes the transaction for raw SQL. Go tests load the **same**
`backend-go/config/config.yaml` as the app (`NewConfig` also searches `../../config` so it resolves
from `internal/<pkg>`); only the attachment root is redirected to the OS temp dir so test runs do not
scatter `attachment/` folders into module directories.
Run with `mvn test` and `go test ./...`. Both are **integration** tests that need a reachable
PostgreSQL (`backend-go/config/config.yaml` for Go, the `dev` profile for Java) — there is no
mocked-DB tier, so the suite fails at setup without a database.

## Conventions

- Batch mutations take a list of ids and return `ActionsResult` (per-item success/failure counts,
  no all-or-nothing rollback); each item runs in its own transaction.
- Pagination is `PageResult<T>` (`page`, `size`, `total`, `data`) driven by `QueryParam`.
- Errors are `BizException` (Java) / `errs.NewBizError` (Go), mapped to the `{code, message, data}`
  wrapper by `ControllerAdvice` / `middleware.ErrorHandler`.

## Code Map

```
backend-java/src/main/java/pers/eastwind/billmanager/
  EACAfterSaleMgrApplication.java   — entry point
  common/     — Result, PageResult, QueryParam, ActionsResult, AuditEntity, AuthorityRole,
                BizException, ControllerAdvice, GlobalErrorController, AuthUtil, Snowflake,
                config/CacheConfig
  user/       — controller, service, UserProperties, security/{SecurityConfig, JWTTokenFilter},
                util/JWTUtil, config/AuditConfig
  company/    — CompanyController/Service/Repository/Mapper
  servicebill/— ServiceBillController, ServiceBillService (state machine),
                ServiceBillIOService + ServiceBillImportMapper/-Rule (LD/WK rules, import/export),
                ServiceBillAttachService + model/ServiceBillAttach
                (+ ServiceBillAttachRepository) for the bill↔attachment links,
                ServiceBillStatisticController/Service (+ StatisticRepository)
  reimburse/  — ReimburseController/Service, ReimburseIOService + ReimburseImportMapper/-Rule
                (invoice rule, import), ReimburseAttachService +
                model/ReimburseAttach (+ ReimburseAttachRepository) for the bill↔attachment
                links, ReimburseStatisticController/Service (+ StatisticRepository)
  attach/     — AttachController/Service (upload/persist/delete + download; keys are
                `{namespace}/{uuid}{name}` and never move; owns no relation table and no business
                concept), AttachContentService (image/PDF texts, Excel grid),
                OCRService, util/{FileUtil, OfficeFileUtil, FileTxUtil}, config/AttachConfigProperties

backend-go/
  cmd/main.go                       — entry point
  server/server.go                  — Fiber bootstrap, middleware, module wiring under /api
  internal/{user,company,bill,reimburse,attach}/ — one flat package per domain
      {name}.go   Setup() + routes        handler.go  HTTP handlers
      service.go  business logic          store.go    GORM queries
      model.go    types/DTOs              query.go    query params (where present)
      stats: stat_handler.go / stat_svc.go / stat_store.go
      attach extras: files.go, ocr.go, office.go, file_tx.go, content.go   (no relation table, no
          business concept — `Upload` / `Persist` / `Delete` only; the caller owns the namespace
          and the link rows)
      bill extras: ld_rule.go, wk_rule.go, mapper.go, attach_relation.go
      reimburse extras: invoice_rule.go, mapper.go, attach_relation.go
  internal/testutil/ — package-level test support (NOT a base class, no TestMain): AppCtx lazily
                       initialises the shared env, BeginTx (rollback via t.Cleanup), Tx, upload
                       helpers
  pkg/        — audit, auth, cache, context, database, errs, logger, middleware, result, util
  config/     — config.go + config.yaml (also what the tests load)

Naming — Java is `{Entity}{Role}` with the word roots `ServiceBill` / `Reimburse` / `Attach`
(never `Reimbursement`/`Attachment`, never `Biz`/`Rel` abbreviations), e.g. `ServiceBillService`,
`ReimburseAttachRepository`, `AttachDTO`. Go uses the package as the namespace, so types drop the
entity prefix (`bill.Service`, `bill.DTO`, `bill.Mapper`, `reimburse.Service`); entities keep their
table words (`bill.ServiceBill`, `reimburse.Reimburse`, `attach.Attach`) and pin the DB table with
`TableName()` where the Go name differs from it.

frontend/src/
  main.ts                 — app bootstrap, Vuetify theme/locale, registers v-role
  App.vue, router.ts, assets/style.css
  common/                 — api/HttpClient.ts, model/, store/{UIStore, RouterStore},
                            component/, view/{HomeView, NotFoundView},
                            directive/role.ts (hides by role), util/Crypto.ts
  {user,company,service-bill,reimburse,attachment,statistic}/
      api/ HttpClient calls · model/ TS interfaces · view/ pages · component/ sub-components
      composable/ (service-bill, reimburse, attachment) · store/ (user)

deploy/
  compose-java.yml / compose.yml    — postgres + backend + Caddy (Java / Go)
  database/init_scheme.sql          — full schema      database/init_user.sql — default admin
  database/migrate_attachment.sql   — one-off migration: split bill_attach_relation into the two
                                    per-module relation tables + add attachment.status
  frontend/{html,conf,cert}         — Vite output, Caddyfile, certificates
  ocr-server/                       — optional RapidOCR service (copied from ocr-server/)
  build.sh / build.bat, backup.sh

ocr-server/                         — standalone FastAPI OCR service for the Go backend
  src/ocr_server/__init__.py        — FastAPI app, POST /ocr, CLI (-ip/-p/-workers)
  src/ocr_server/ocr.py             — RapidOCR wrapper (model paths via env)
  src/ocr_server/pdf.py             — pdfplumber text extraction + image fallback
  Dockerfile, pyproject.toml, uv.lock
```
