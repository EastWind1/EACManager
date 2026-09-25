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
`/reimburse/:id?`, plus a catch-all 404. `common/api/HttpClient.ts` wraps `fetch()` (request dedup
via `AbortController`, loading bar, 401→login redirect, 403/500 toasts, unwraps `data`, returns
`Blob` for non-JSON). `v-role` hides elements by setting `display: none` (it does not unmount them).

## Database (PostgreSQL)

8 tables, integer PKs from `SEQUENCE`s (increment 50), audit columns on all: `created_by_id`,
`created_date`, `last_modified_by_id`, `last_modified_date`.

| Table | Key fields |
|---|---|
| `sys_user` | `username` (unique), `name`, `password` (bcrypt), `phone`, `email`, `authority`, `disabled` |
| `company` | `name`, `contact_name`, `contact_phone`, `address`, `email`, `disabled` |
| `service_bill` | `number`, `type` (INSTALL=0/FIX=1), `state`, `total_amount`, `project_name`, `project_address`, `project_contact(_phone)`, `elevator_info`, `on_site_contact`, `on_site_phone`, `order_date`, `processed_date`, `finished_date`, `remark`, `product_company_id`, `version` |
| `service_bill_detail` | `device`, `quantity`, `unit_price`, `subtotal`, `remark`, FK `service_bill_id` |
| `reimbursement` | `number`, `state`, `total_amount`, `reimburse_date`, `summary`, `remark`, `version` |
| `reimburse_detail` | `name`, `amount`, FK `reimbursement_id` |
| `attachment` | `name`, `relative_path`, `type` (IMAGE=0/PDF=1/WORD=2/EXCEL=3/OTHER=4) |
| `bill_attach_relation` | FK `attach_id`, FK `bill_id`, `bill_type` (SERVICE_BILL=0/REIMBURSEMENT=1) |

`authority` ∈ ROLE_ADMIN / ROLE_USER / ROLE_FINANCE / ROLE_GUEST. `version` is the Go
optimistic-lock column. Indexes exist on `service_bill(number, created_date)`,
`reimbursement(number, reimburse_date)`, `sys_user(username)`,
`bill_attach_relation(bill_id, bill_type)`.

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
are deletable. Numbers: `S` + `YYYYMMDD` + 3-digit suffix (`time.Now().UnixNano() % 1000` in Go,
`Math.random()`-derived in Java); duplicate numbers are rejected when supplied manually.

### Reimbursement state machine

```
CREATED(0) ─process→ PROCESSING(1) ─finish→ FINISHED(2)
     ←cancel-process─       ←cancel-finish─
```

Same one-step revert rule; only `CREATED` is deletable. Numbers: `R` + `YYYYMMDD` + 3-digit random.

## API Surface

Roles: A=ADMIN, U=USER, F=FINANCE. "auth" means any authenticated role.

| Module | Endpoints |
|---|---|
| `/api/user` | `POST /token` (public) · `PUT /logout` · `GET /` A/U/F · `POST /` A · `PUT /` A/U/F · `DELETE /:username` (disable) A |
| `/api/company` | `GET /` A/U · `POST /` A · `PUT /` A · `DELETE /:id` (disable) A |
| `/api/serviceBill` | `POST /query` · `GET /:id` · `POST /` · `PUT /` · `DELETE /` (batch, CREATED only) · `POST /import` (OCR) · `PUT /process` `processed` `finish` `cancel-process` `cancel-processed` `cancel-finish` · `POST /export` · `GET /countByState` · `GET /totalAmountGroupByMonth` |
| `/api/reimburse` | `POST /query` · `GET /:id` · `POST /` · `PUT /` · `DELETE /` · `POST /import` (invoice OCR → prefilled DTO) · `PUT /process` `finish` `cancel-process` `cancel-finish` · `POST /export` · `GET /countByState` · `GET /totalAmountGroupByMonth` |
| `/api/attachment` | `POST /temp` (upload temp files → DTOs) · `GET /` (download, attachment DTO as query params) |

Business endpoints are `auth` unless noted; Go tightens several: bill `cancel-*` are A-only,
company `POST`/`DELETE` are A-only, user `GET`/`PUT` are A/U/F. Go's company `PUT /` currently
registers **no** role middleware — treat that as a gap, not a contract.

Attachments have no standalone delete/OCR endpoint: `POST /temp` stages files in a JVM/temp
directory, then bill/reimburse create/update re-binds them through `bill_attach_relation`
(unreferenced temp entries are swept — lazily on read in Java, by a background
`CleanTempFiles()` goroutine in Go). Physical moves are transactional (`FileTxUtil` /
`file_tx.go`). Exports are Excel streams; the service-bill export bundles attachments as a zip.

Statistics: `countByState` returns per-state counts; `totalAmountGroupByMonth` sums by
`processed_date` (bills) / `reimburse_date` (reimbursements) and is cached in Go.

## Configuration

**Java** (`backend-java/src/main/resources/application.yml`) — requires env `DB_HOST`, `DB_PORT`,
`DB_NAME`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_KEY` (≥32 ASCII chars). Virtual threads on, multipart
and `config.jwt.expire=604800` (7d), attachment dir `${user.dir}/attachment`. Local dev credentials
live in `application-dev.yml` (profile `dev`, which tests activate).

**Go** (`backend-go/config/config.yaml`, loaded by Viper from `./config`, `.`, `../config`, `..`) —
`server.port`, `log.level`, `db.*`, `cache.expire=86400`, `jwt.secret`/`jwt.expire=604800`,
`attachment.path=attachment`, `attachment.maxFileSize=5242880`, `ocr.url`. Note: `viper.AutomaticEnv()`
is called without `BindEnv`, so `db.*` / `ocr.url` are **not** actually overridable by the
`DB_*` / `OCR_URL` env vars that `deploy/compose.yml` sets — edit the YAML.

**Upload limit is 5 MB** on both backends (`spring.servlet.multipart.max-file-size` and Fiber
`BodyLimit` / `attachment.maxFileSize`); the Go **test** config raises it to 50 MB.

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
`common/BaseServiceTest`, Mockito for collaborators, profile `dev`. Go — `testify/suite` integration
tests in `backend-go/test` (begin transaction in `SetupTest`, roll back in `TearDownTest`). Run with
`mvn test` and `go test ./test/...`. Both are **integration** tests that need a reachable
PostgreSQL (`backend-go/test/config/config.yaml` for Go, the `dev` profile for Java) — there is no
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
                BizException, ControllerAdvice, GlobalErrorController, AuthUtil, config/CacheConfig
  user/       — controller, service, UserProperties, security/{SecurityConfig, JWTTokenFilter},
                util/JWTUtil, config/AuditConfig
  company/    — CompanyController/Service/Repository/Mapper
  servicebill/— ServiceBillController, ServiceBillBizService (state machine),
                ServiceBillIOService + LD/WK AttachMapRule (import/export),
                BillStatisticController/Service (+ StatisticRepository)
  reimburse/  — ReimburseController/Service, ReimburseIOService + ReimburseMapRule (invoice import),
                ReimburseStatisticController/Service (+ StatisticRepository)
  attach/     — AttachmentController/Service, AttachMapService + AttachMapRule (binding rules),
                OCRService, util/{FileUtil, OfficeFileUtil, FileTxUtil}, config/AttachConfigProperties

backend-go/
  cmd/main.go                       — entry point
  internal/server/server.go         — Fiber bootstrap, middleware, module wiring under /api
  internal/{user,company,bill,reimburse,attach}/ — one flat package per domain
      {name}.go   Setup() + routes        handler.go  HTTP handlers
      service.go  business logic          store.go    GORM queries
      model.go    types/DTOs              query.go    query params (where present)
      stats: stat_handler.go / stat_svc.go / stat_store.go
      attach extras: files.go, ocr.go, office.go, file_tx.go, attachmap.go
      bill extras: ld_rule.go, wk_rule.go      reimburse extras: invoice_rule.go
  pkg/        — audit, auth, cache, context, database, errs, logger, middleware, result, util
  config/     — config.go + config.yaml
  test/       — testify suites

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
  frontend/{html,conf,cert}         — Vite output, Caddyfile, certificates
  ocr-server/                       — optional RapidOCR service (copied from ocr-server/)
  build.sh / build.bat, backup.sh

ocr-server/                         — standalone FastAPI OCR service for the Go backend
  src/ocr_server/__init__.py        — FastAPI app, POST /ocr, CLI (-ip/-p/-workers)
  src/ocr_server/ocr.py             — RapidOCR wrapper (model paths via env)
  src/ocr_server/pdf.py             — pdfplumber text extraction + image fallback
  Dockerfile, pyproject.toml, uv.lock
```
