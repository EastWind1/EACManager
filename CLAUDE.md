# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Elevator AC After-Sale Manager - a B/S architecture system for managing installation/maintenance work orders, with OCR import support and attachment management. Supports both Java and Go backend implementations.

## Build & Development Commands

### Java Backend
```bash
cd backend-java
mvn clean package -DskipTests
mvn test
mvn verify
```

### Go Backend
```bash
cd backend-go
go build -ldflags="-s -w" -o target/backend-go cmd/main.go
go test ./...
```

### Frontend
```bash
cd frontend
pnpm install
pnpm dev
pnpm build
pnpm test
pnpm type-check
pnpm lint
pnpm format
```

### Full Build (for deployment)
```bash
cd deploy
# Windows
build.bat
# Linux
build.sh

docker-compose -f docker-compose-java up -d
# or
docker-compose -f docker-compose-go.yml up -d
```

## Architecture

### Backend Structure

**Java Backend** (`backend-java/src/main/java/pers/eastwind/billmanager/`):
- `common/` - shared utilities, controllers, models, repositories
- `attach/` - attachment management module
- `company/` - company management
- `reimburse/` - reimbursement module
- `servicebill/` - service bill/work order module
- `user/` - authentication and user management

**Go Backend** (`backend-go/internal/`):
- `internal/module/` - feature modules (user, company, attach, reimburse, servicebill)
- `internal/pkg/` - shared packages (cache, context, database, logger, middleware)
- `server/server.go` - application entry point, initializes modules and sets up routes
- `config/config.yaml` - configuration file

### Frontend Structure

- Vue 3 + TypeScript + Vuetify
- `src/main.ts` - application entry point, Vuetify setup with Chinese locale
- `src/router.ts` - Vue Router configuration
- `src/user/` - user authentication store
- `src/service-bill/`, `src/reimburse/`, `src/company/` - feature modules
- `src/common/` - shared components and utilities

### Database

PostgreSQL - both backends use the same database schema.

## Module Structure Pattern

Both backends follow similar module structure:
- `controller/` - HTTP request handlers
- `service/` - business logic
- `model/` - data models (Go) / entities (Java)
- `repository/` - data access layer (Go) / JPA repositories (Java)

## Key Technologies

- **Java Backend**: Spring Web MVC, Spring Security, Spring Data JPA, MapStruct, RapidOCR, PDFBox, POI
- **Go Backend**: Fiber, Gorm, PostgreSQL, RapidOCR API (remote calls due to no native Chinese OCR in Go)
- **Frontend**: Vue 3, TypeScript, Vuetify 4, Pinia, Vue Router, Vite
- **Deployment**: Docker + Caddy reverse proxy, separated jar dependencies (Java) or static binary (Go)
