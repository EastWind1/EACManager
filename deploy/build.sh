#!/bin/bash
set -e

# 设置目录
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT_DIR="$(dirname "$SCRIPT_DIR")"
BACKEND_JAVA_DIR="$ROOT_DIR/backend-java"
BACKEND_GO_DIR="$ROOT_DIR/backend-go"
FRONTEND_DIR="$ROOT_DIR/frontend"
DEPLOY_DIR="$ROOT_DIR/deploy"
DEPLOY_HTML="$DEPLOY_DIR/frontend/html"

# 打包 Java 后端
echo "[1/3] 打包 Java 后端..."
cd "$BACKEND_JAVA_DIR"
mvn clean package -DskipTests

# 打包 Go 后端
echo "[2/3] 打包 Go 后端..."
cd "$BACKEND_GO_DIR"
GOOS=linux GOARCH=amd64 CGO_ENABLED=0
go build -ldflags="-s -w" -o target/backend-go cmd/main.go

# 打包前端
if [ -d "$FRONTEND_DIR" ]; then
    echo "[3/3] 打包前端..."
    cd "$FRONTEND_DIR"
    pnpm build
else
    echo "警告：前端目录不存在，跳过前端打包"
fi

# 清理旧文件
rm -rf "$DEPLOY_DIR/backend-java" "$DEPLOY_DIR/backend-go" "$DEPLOY_HTML"

# 拷贝 Java 交付物
mkdir -p "$DEPLOY_DIR/backend-java"
find "$BACKEND_JAVA_DIR/target" -name "*.jar" -type f -exec sh -c 'java -Djarmode=tools -j "{}" extract --destination "$DEPLOY_DIR/backend-java"' \;

# 拷贝 Go 交付物
mkdir -p "$DEPLOY_DIR/backend-go"
cp -r "$BACKEND_GO_DIR/target"/* "$DEPLOY_DIR/backend-go/"
cp -r "$BACKEND_GO_DIR/config" "$DEPLOY_DIR/backend-go/config/"
chmod +x "$DEPLOY_DIR/backend-go/backend-go"

# 拷贝前端交付物
if [ -d "$FRONTEND_DIR/dist" ]; then
    mkdir -p "$DEPLOY_HTML"
    cp -r "$FRONTEND_DIR/dist"/* "$DEPLOY_HTML/"
fi

echo "构建和拷贝完成！"
chmod -R 755 "$DEPLOY_DIR"
