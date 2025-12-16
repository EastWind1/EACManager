# 电梯空调服务单管理系统

- B/S 架构
- 用于管理安装维护工单
- 支持 OCR 导入
- 支持附件管理

## 前端

- Vue3 + Typescript + Vuetify

## 后端

- Spring Boot + Spring Security + Spring Data JPA + PostgreSQL
- 默认打包将依赖分离，而非 fat-jar，便于上传

## 部署

### 结构

- 使用 Docker-Compose 管理容器集群
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
    - ocr-server
        - 基于 rapidocr 官方 Dockerfile
        - 由于 rapidocr 官方支持 Java 运行时，默认不启用该容器，在需要独立部署 OCR 服务时使用

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

### 运行

1. `cd deploy`
2. 执行目录下的 `build-and-copy.bat` 脚本
3. 执行 `docker-compose up -d`
4. 进入数据库容器，执行 `init_user.sql` 初始化管理员 `root`，默认密码 `admin`

```shell
docker cp init_user.sql eac-postgres:/tmp
docker exec -it eac-postgres /bin/bash
psql -U ${DB_NAME} -d ${DB_USERNAME} -f /tmp/init_user.sql
```
