#!/bin/bash

# 设置脚本在遇到错误时立即退出
set -e

if [ -n "${BASH_SOURCE:-}" ]; then
  SCRIPT_PATH="${BASH_SOURCE[0]}"
else
  SCRIPT_PATH="$0"
fi
# 获取脚本目录
SCRIPT_DIR="$(cd "$(dirname "$SCRIPT_PATH")" && pwd)"
ROOT_DIR="$(dirname "$SCRIPT_DIR")"
BACKEND_DIR="$ROOT_DIR/backend"
BACKEND_GO_DIR="$ROOT_DIR/backend-go"
FRONTEND_DIR="$ROOT_DIR/frontend"
DEPLOY_DIR="$ROOT_DIR/deploy"
BACKEND_TARGET_DIR="$BACKEND_DIR/target"
BACKEND_GO_TARGET_DIR="$BACKEND_GO_DIR/target"
FRONTEND_DIST_DIR="$FRONTEND_DIR/dist"
BACKEND_DEPLOY_DIR="$DEPLOY_DIR/backend"
BACKEND_GO_DEPLOY_DIR="$DEPLOY_DIR/backend-go"
FRONTEND_DEPLOY_DIR="$DEPLOY_DIR/frontend/html"

echo "当前脚本目录: $SCRIPT_DIR"
echo "项目根目录: $ROOT_DIR"
echo "后端目录: $BACKEND_DIR"
echo "Go 后端目录: $BACKEND_DIR"
echo "前端目录: $FRONTEND_DIR"
echo "交付物目录: $DEPLOY_DIR"

# 检查目录是否存在
if [ ! -d "$BACKEND_DIR" ]; then
    echo "错误：后端目录不存在 $BACKEND_DIR"
    exit 1
fi

# 打包后端
echo ""
echo "打包后端..."
cd "$BACKEND_DIR"
mvn clean package -DskipTests


# 检查后端打包是否成功
if [ ! -d "$BACKEND_TARGET_DIR" ]; then
    echo "错误：后端打包失败"
    exit 1
fi

echo "后端打包成功"

# 打包 go 后端
echo ""
echo "打包 go 后端..."
cd "$BACKEND_GO_DIR"
GOOS=linux
GOARCH=amd64
CGO_ENABLED=0
go build -ldflags="-s -w" -o target/backend-go cmd/main.go

# 检查前端目录并打包前端（如果存在）
if [ -d "$FRONTEND_DIR" ]; then
    echo ""
    echo "打包前端..."
    cd "$FRONTEND_DIR"
    
    # 检查 package.json 并使用合适的包管理器
    if [ -f "package.json" ]; then
        if command -v pnpm &> /dev/null; then
            pnpm build
        elif command -v yarn &> /dev/null; then
            yarn build
        elif command -v npm &> /dev/null; then
            npm run build
        else
            echo "错误：未找到合适的包管理器（npm/yarn/pnpm）"
            exit 1
        fi
    else
        echo "错误：未找到 package.json 文件"
        exit 1
    fi
    
    # 检查前端打包是否成功
    if [ ! -d "$FRONTEND_DIST_DIR" ]; then
        echo "错误：前端打包失败"
        exit 1
    fi
    
    echo "前端打包成功"
else
    echo "警告：前端目录不存在，跳过前端打包"
fi

# 创建部署目录并拷贝文件
echo ""
echo "拷贝交付物..."

# 创建后端部署目录
mkdir -p "$BACKEND_DEPLOY_DIR/app"

# 清理旧的后端文件
if [ -d "$BACKEND_DEPLOY_DIR/app" ]; then
    echo "删除旧的后端app目录..."
    rm -rf "$BACKEND_DEPLOY_DIR/app"
fi


# 拷贝并展开后端jar文件
if [ -d "$BACKEND_TARGET_DIR" ]; then
    echo "拷贝后端文件..."
    
    # 查找jar文件
    JAR_FILE=$(find "$BACKEND_TARGET_DIR" -name "*.jar" -type f | head -n 1)
    
    if [ -n "$JAR_FILE" ]; then
        echo "找到jar文件: $JAR_FILE"
        cp "$JAR_FILE" "$BACKEND_DEPLOY_DIR/app.jar"
        # 使用java命令展开
        if command -v java &> /dev/null; then
            java -Djarmode=tools -jar "$BACKEND_DEPLOY_DIR/app.jar" extract --destination "$BACKEND_DEPLOY_DIR/app"
            echo "后端文件展开成功"
        fi
    else
        echo "错误：未找到jar文件"
        exit 1
    fi
else
    echo "错误：后端目标目录不存在"
    exit 1
fi


# 拷贝 GO 后端文件
if [ -d "$BACKEND_GO_TARGET_DIR" ]; then
    echo "拷贝 Go 后端文件..."
    mkdir -p "$BACKEND_GO_DEPLOY_DIR"
    cp -r "$BACKEND_GO_TARGET_DIR"/* "$BACKEND_GO_DEPLOY_DIR/"
    cp -r "$BACKEND_GO_DIR/config/config.yml" "$BACKEND_GO_DEPLOY_DIR/"
    echo "Go 后端文件拷贝成功"
else
    echo "警告：Go 后端 target 目录不存在，跳过拷贝"
fi

# 拷贝前端文件
if [ -d "$FRONTEND_DIST_DIR" ]; then
    echo "拷贝前端文件..."
    mkdir -p "$FRONTEND_DEPLOY_DIR"
    cp -r "$FRONTEND_DIST_DIR"/* "$FRONTEND_DEPLOY_DIR/"
    echo "前端文件拷贝成功"
else
    echo "警告：前端dist目录不存在，跳过前端拷贝"
fi

echo ""
echo "构建和拷贝完成！"

# 设置正确的权限
chmod -R 755 "$DEPLOY_DIR"