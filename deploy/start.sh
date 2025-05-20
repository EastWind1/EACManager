#!/bin/bash

DEPLOY_DIR="$(cd "$(dirname "$0")" && pwd)"

cd "$DEPLOY_DIR/nginx"
./sbin/nginx

cd "$DEPLOY_DIR/backend"
nohup java -jar -Dloader.path=./lib app.jar > app.log 2>&1 &

