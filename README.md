# 电梯空调服务单管理系统

- B/S 架构
- 用于管理安装维护工单
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

## 环境配置

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

## 部署

### Java 版后端

- 使用 docker-compose.yml
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

- 使用 docker-compose-go.yml
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


## 运行

1. `cd deploy`
2. 执行目录下的 `build-and-copy.bat` 脚本打包
3. 执行 `docker-compose -f docker-compose-java up -d` 或 `docker-compose -f docker-compose-go.yml up -d`
