# 电梯空调服务单管理系统

- B/S 架构
- 用于管理安装维护工单
- 支持基础 RBAC
- 支持 OCR 导入
- 支持附件管理

## 技术栈

### 前端

- Vue3 + Typescript + Vuetify

### 后端 Java

- Spring Web MVC + Spring Security + Spring Data JPA + PostgreSQL
- 默认打包将依赖分离，而非 fat-jar，便于上传
- 使用 Java RapidOCR 实现 OCR

### 后端 GO

- Fiber + Gorm + PostgreSQL
- 由于 Go 没有原生中文 OCR 库，采用 RapidOCR API 镜像远程调用

### 后端 Rust（学习用）

> 该实现**仅用于学习 Rust**，不要求与 Java/Go 功能同步或行为一致，也不建议作为生产交付物。

- Axum + sqlx + PostgreSQL
- 参考 Java/Go 的分域方式与业务模型，但路由、DTO、错误信息、权限清单允许按 Rust 惯用法偏离
- 同样通过 `ocr.url` 调用 RapidOCR 服务；PDF 优先取文本层，失败再降级 OCR

## 构建

- Java/Go/前端： `deploy/build.sh`
- Rust（独立打包） `deploy/build-rust.sh`；产物只写入 `deploy/backend-rust/`，不参与常规打包与 CI
  - 依赖中的 `aws-lc-sys` 需要 CMake 与 C 工具链；musl 交叉编译还需 `cargo-zigbuild`，缺失时脚本会提示

## 部署

### 前置条件

- 服务器安装 docker

### 环境配置

1. 在 `deploy` 目录下新建 `.env` 文件
2. 至少设置以下环境变量

```text
DB_USERNAME # 数据库用户
DB_PASSWORD # 数据库用户密码
DB_NAME # 数据库名称
JWT_KEY # JWT 密钥, 用于生成 JWT, 至少256位
DOMAIN # 域名，本地可使用 localhost
CA_EMAIL # 用于 caddy 生成 CA 证书的邮箱
```

### Java 版后端

- 使用 `compose-java.yml`
- 交付物使用挂载目录管理，便于动态替换
- 容器
    - eac-frontend
        - 使用 Caddy 镜像
        - 挂载静态资源目录、配置
    - eac-backend
        - 使用 eclipse-temurin 镜像
        - 挂载 JAR 包目录及配置文件
        - 默认情况下在挂载目录生成附件文件夹
    - eac-postgres
        - 使用 postgres 镜像
        - 使用 volume 挂载数据目录

### Go 版后端

- 使用 `compose.yml`
- 交付物使用挂载目录管理，便于动态替换
- 容器
    - eac-frontend
        - 使用 Caddy 镜像
        - 挂载静态资源目录、配置
    - eac-backend-go
        - 使用 Alpine 镜像
        - 默认情况下在挂载目录生成附件文件夹
    - eac-postgres
        - 使用 postgres 镜像
        - 使用 volume 挂载数据目录
    - ocr-server
        - 默认不启用
        - 基于 rapidocr 官方 Dockerfile

### Rust 版后端

- 使用 `compose-rust.yml`
- 仅用于本地体验，不承诺与 Java/Go 版本功能同步
- 容器
    - eac-frontend
        - 使用 Caddy 镜像
    - eac-backend-rust
        - 使用 Alpine 镜像
        - 挂载目录中放置 `backend-rust` 与 `config/`
        - 默认情况下在挂载目录生成附件文件夹
    - eac-postgres
        - 使用 postgres 镜像
        - 使用 volume 挂载数据目录

### 手动部署

1. 将 `deploy` 拷贝至服务器
2. 赋予 `deploy/backend-go/backend-go` 可执行权限（rust `deploy/backend-rust/backend-rust` 同理）
3. 执行 `docker compose up -d`

### 脚本部署

1. 服务器配置为 SSH 登陆
2. 至少配置 `DEPLOY_HOST` 为服务器地址，其他变量见脚本
3. 执行 `deploy/deploy.sh` 脚本


## 运行

1. `cd deploy`
2. 执行目录下的 `build.sh` 脚本打包
3. 执行 `docker-compose -f docker-compose up -d` 或 `docker-compose -f docker-compose-java.yml up -d` 或 `docker compose -f compose-rust.yml up -d`
