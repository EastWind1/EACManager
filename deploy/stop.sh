#!/bin/bash

# 获取当前脚本所在目录
DEPLOY_DIR="$(cd "$(dirname "$0")" && pwd)"

cd "$DEPLOY_DIR/nginx"
./sbin/nginx

cd "$DEPLOY_DIR/backend"
nohup java -jar app.jar > app.log 2>&1 &

