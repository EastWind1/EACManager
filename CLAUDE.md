# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Elevator AC After-Sale Manager — a B/S architecture system for managing installation/maintenance work orders, with OCR import support and attachment management. Supports **both Java and Go** backend implementations. Vue 3 frontend. PostgreSQL database.

## Architecture

**Java Backend** (port 8080) — Spring Boot 4 + Java 25 + Virtual Threads
**Go Backend** (port 8080) — Fiber v3 + GORM

Same DB schema and frontend for both — swap via `docker-compose-java.yml` / `docker-compose-go.yml`.

Both backends share identical layered structure: `controller` → `service` → `repository` → `model`. API prefix `/api`, response wrapper `{code, message, data}`, biz exceptions for errors. Java uses MapStruct for DTO mapping, Go uses manual conversion.

**Feature modules:** `user` (login/JWT/cookie `X-Auth-Token`), `company`, `servicebill` (work orders with lifecycle), `reimburse` (expense reports), `attach` (file upload/OCR), `statistic` (dashboard).

**Frontend:** Vue 3 + Vuetify 4 + Pinia + Vue Router 5. Each module has `api/`, `model/`, `view/`, `component/`, `composable/`, `store/`. HttpClient wraps `fetch()` with request dedup, 401→redirect, and loading bar. Custom `v-role` directive for RBAC. Routes: `/login`, `/dashboard`, `/user`, `/company`, `/services`, `/service/:id?`, `/reimburses`, `/reimburse/:id?`.

### Database Schema (PostgreSQL)

8 tables, all use integer PKs with `SEQUENCE` (increment 50):

| Table | Key fields | Notes |
|---|---|---|
| `sys_user` | `username` (unique), `password` (bcrypt), `authority` (enum), `disabled` | ROLE_ADMIN/ROLE_USER/ROLE_GUEST/ROLE_FINANCE |
| `company` | `name`, `contact_name`, `contact_phone`, `address`, `email`, `disabled` | Elevator companies |
| `service_bill` | `number` (indexed), `type` (INSTALL=0 / FIX=1), `state` (CREATED=0 → PROCESSING=1 → PROCESSED=2 → FINISHED=3), `total_amount`, `project_name`, `elevator_info`, `order_date`, `processed_date`, `finished_date`, `product_company_id` | Main work order |
| `service_bill_detail` | `device`, `quantity`, `unit_price`, `subtotal`, `remark`, FK `service_bill_id` | Line items |
| `reimbursement` | `number` (indexed), `state` (CREATED=0 → PROCESSING=1 → FINISHED=2), `total_amount`, `reimburse_date`, `summary`, `remark` | Expense report |
| `reimburse_detail` | `name`, `amount`, FK `reimbursement_id` | Expense line items |
| `attachment` | `name`, `relative_path`, `type` (IMAGE=0/PDF=1/WORD=2/EXCEL=3/OTHER=4), audit fields | Uploaded files |
| `bill_attach_relation` | FK `attach_id`, FK `bill_id`, `bill_type` (SERVICE_BILL=0/REIMBURSEMENT=1) | Many-to-many join |

**Default admin user** (from `init_user.sql`): username `root`, password bcrypt-encoded, role `ROLE_ADMIN`.

### Service Bill State Machine

States: `CREATED(0)` → `PROCESSING(1)` → `PROCESSED(2)` → `FINISHED(3)`

```
CREATED  ──process──→  PROCESSING  ──processed──→  PROCESSED  ──finish──→  FINISHED
   ↑                       ↑                          ↑                      ↑
   │   cancel-process      │   cancel-processed       │   cancel-finish       │
   └───────────────────────┘                          └──────────────────────┘
```

- Each forward transition requires the exact prior state (guarded in service layer).
- Each `cancel-*` goes back exactly ONE step and clears the associated date field.
- Only `CREATED`-state bills can be deleted.
- Bill numbers auto-generated: `S` + `YYYYMMDD` + 4-digit suffix (Go: nanosecond mod 1000; Java: same pattern).

### Reimbursement State Machine

States: `CREATED(0)` → `PROCESSING(1)` → `FINISHED(2)`

```
CREATED  ──submit──→  PROCESSING  ──pay──→  FINISHED
   ↑                      ↑                  ↑
   │   cancel-process     │   cancel-finish  │
   └──────────────────────┘                  │
                       └─────────────────────┘
```

- Each `cancel-*` goes back exactly ONE step.
- Only `CREATED`-state reimbursements can be deleted.
- Reimburse numbers auto-generated: `R` + `YYYYMMDD` + 4-digit random.

## Build & Development Commands

### Java Backend (port 8080)
```bash
cd backend-java
mvn clean package -DskipTests   # Build without tests
mvn test                        # Run tests
mvn verify                      # Build + run tests
```

### Go Backend (port 8080)
```bash
cd backend-go
go build -ldflags="-s -w" -o target/backend-go cmd/main.go
go test ./...
```

### Frontend (port 5173, proxied /api → 8080)
```bash
cd frontend
pnpm install
pnpm dev          # Dev server with HMR
pnpm build        # Production build (type-check + vite build)
pnpm type-check   # vue-tsc type checking
pnpm lint         # oxlint + eslint
pnpm format       # oxfmt
```

### Full Build
```bash
cd deploy && ./build.sh    # or build.bat on Windows
docker-compose -f docker-compose-java.yml up -d    # Java backend
docker-compose -f docker-compose-go.yml up -d      # Go backend
```

### Tests
```bash
# Go — testify/suite with transaction rollback (SetupTest begins, TearDownTest rolls back)
cd backend-go && go test -v ./test/...

# Java — @SpringBootTest + @Transactional (rollback after each test), profile "dev"
cd backend-java && mvn test
```

## Configuration

**Env vars (.env):** `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_KEY` (≥32 chars), `CA_EMAIL`, `DOMAIN`.

**Go** (`config/config.yaml`): Viper loads YAML + env vars. JWT expire 604800s (7d), cache expire 86400s, upload limit 50MB, attachment dir `./attachment`.

**Java** (`application.yml`): Virtual Threads enabled, upload limit 50MB, JWT expire 7d. Dev profile at `application-dev.yml` has local DB credentials.

**OCR:** Java uses local `RapidOCR` (ONNX, Chinese text). Go calls a remote OCR API (`ocr.url` in config). Optional standalone OCR server at `deploy/ocr-server/Dockerfile`.

## API Endpoint Reference

All under `/api/`. Response: `{ "code": 0, "message": "success", "data": ... }`.

### User (`/api/user`)

| Method | Path | Auth | Description |
|---|---|---|---|
| POST | `/login` | No | Login, sets `X-Auth-Token` cookie + returns user info |
| GET | `/` | Yes | List users (paginated) |
| PUT | `/password` | Yes | Change own password |
| PUT | `/:id` | Yes | Update user |
| POST | `/` | Yes | Create user (admin) |
| DELETE | `/` | Yes | Delete users (admin) |
| PUT | `/:username/disable` | Yes | Disable user (admin) |

### Company (`/api/company`)

| Method | Path | Auth | Description |
|---|---|---|---|
| POST | `/query` | Yes | Query companies |
| GET | `/all` | Yes | Get all companies |
| GET | `/:id` | Yes | Get by ID |
| POST | `/` | Yes | Create |
| PUT | `/` | Yes | Update |
| DELETE | `/` | Yes | Delete |

### Service Bill (`/api/serviceBill`)

| Method | Path | Auth | Description |
|---|---|---|---|
| POST | `/query` | Yes | Query with filters |
| GET | `/:id` | Yes | Get by ID |
| POST | `/` | Yes | Create |
| PUT | `/` | Yes | Update |
| DELETE | `/` | Yes | Delete (batch, only CREATED can be deleted) |
| POST | `/import` | Yes | Import from file (OCR parse) |
| PUT | `/process` | Yes | CREATED → PROCESSING (batch) |
| PUT | `/processed` | Yes | PROCESSING → PROCESSED (batch) |
| PUT | `/finish` | Yes | PROCESSED → FINISHED (batch) |
| PUT | `/cancel-process` | Yes | PROCESSING → CREATED (batch) |
| PUT | `/cancel-processed` | Yes | PROCESSED → PROCESSING (batch) |
| PUT | `/cancel-finish` | Yes | FINISHED → PROCESSED (batch) |
| POST | `/export` | Yes | Export to Excel (with attachments in zip) |

### Reimbursement (`/api/reimburse`)

| Method | Path | Auth | Description |
|---|---|---|---|
| POST | `/query` | Yes | Query with filters |
| GET | `/:id` | Yes | Get by ID |
| POST | `/` | Yes | Create |
| PUT | `/` | Yes | Update |
| DELETE | `/` | Yes | Delete (batch) |
| PUT | `/process` | Yes | Submit: CREATED → PROCESSING |
| PUT | `/finish` | Yes | Finalize: PROCESSING → FINISHED |
| PUT | `/cancel-process` | Yes | Revert: PROCESSING → CREATED |
| PUT | `/cancel-finish` | Yes | Revert: FINISHED → PROCESSING |
| POST | `/export` | Yes | Export to Excel |

### Attachment (`/api/attachment`)

| Method | Path | Auth | Description |
|---|---|---|---|
| POST | `/upload` | Yes | Upload file(s), returns attachment IDs |
| GET | `/download/:id` | Yes | Download by ID |
| DELETE | `/` | Yes | Delete attachments |
| POST | `/ocr` | Yes | OCR a file |

### Statistic (`/api/statistic`)

| Method | Path | Auth | Description |
|---|---|---|---|
| GET | `/countByState` | Yes | Count bills grouped by state |
| GET | `/sumAmountByMonth` | Yes | Monthly amount summary |

## Key Technologies

**Java:** Spring Boot 4, Java 25 (virtual threads), Spring Security (JWT, 4 roles), Spring Data JPA + Hibernate 7, MapStruct 1.6, RapidOCR (ONNX), PDFBox 3, POI 5, Lombok.
**Go:** Fiber v3, GORM, Sonic (JSON), Viper (config), golang-jwt v5, testify, excelize, optimisticlock.
**Frontend:** Vue 3 + TS 6, Vuetify 4 (`zhHans` locale), Pinia 3, Vue Router 5, Vite 8 (proxy `/api`→8080), oxlint+oxfmt, pdfjs-dist, xlsx (SheetJS).
**Deploy:** Docker (postgres + backend + Caddy), auto HTTPS via Let's Encrypt.

## Shared Patterns
- All tables: integer PKs + audit fields (`created_by/date`, `last_modified_by/date`)
- Batch ops: array of IDs in, `ActionsResult<int,void>` out (success/failure counts)
- Pagination: `PageResult<T>` with `page`, `size`, `total`, `data`
- Errors: `BizException` / `errs.NewBizError`
- Frontend: `HttpClient('/api/<module>')` per feature module, 401→auto-redirect to login, `v-role` directive for element visibility

## Code Map

```
backend-java/src/main/java/pers/eastwind/billmanager/
  EACAfterSaleMgrApplication.java    — Entry point
  common/  — Result, PageResult, QueryParam, AuthorityRole, ControllerAdvice, BaseRepository
  user/    — JWTTokenFilter, SecurityConfig, UserService, JWTUtil
  company/ — CompanyService, CompanyRepository
  servicebill/ — ServiceBillBizService, ServiceBillIOService, StatisticService
  reimburse/   — ReimburseService
  attach/  — AttachmentService, AttachMapService, OCRService, FileTxUtil

backend-go/
  cmd/main.go                        — Entry point
  internal/server/server.go          — Fiber app bootstrap, module init, routes
  internal/module/{user,company,servicebill,reimburse,attach}/
    module.go                        — Each has Setup() registering routes
    controller/                      — HTTP handlers
    service/                         — Business logic (biz_service, ocr_service, etc.)
    repository/                      — GORM queries
  internal/pkg/                      — cache, database, errs, middleware, result, auth, util
  test/                              — testify/suite integration tests
  config/config.yaml                 — Runtime config

frontend/src/
  main.ts                            — Bootstrap, Vuetify setup, v-role directive
  router.ts                          — Lazy routes, auth guard
  common/api/HttpClient.ts           — fetch wrapper (dedup, 401 redirect, loading bar)
  {service-bill,reimburse,company,attachment,statistic,user}/
    api/    — HttpClient calls
    model/  — TS interfaces
    view/   — Vue page components
    component/ — reusable sub-components
  user/store/UserStore.ts            — Pinia auth store

deploy/
  docker-compose-{java,go}.yml       — Postgres + backend + Caddy
  database/init_scheme.sql           — Full schema
  database/init_user.sql             — Default admin (root)
  build.sh / build.bat               — Cross-compile + deploy
```
