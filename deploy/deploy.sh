#!/usr/bin/env bash

#   DEPLOY_HOST      远程主机，必填
#   DEPLOY_USER      SSH 用户，默认 root
#   DEPLOY_PATH      远程部署目录，默认 ~/deploy
#   DEPLOY_BACKEND   go 或 java，默认 go
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ENV_FILE="$SCRIPT_DIR/.env"

env_value() {
    grep -m1 "^$1=" "$ENV_FILE" 2>/dev/null | cut -d= -f2- | tr -d '\r' || true
}

if [ -f "$ENV_FILE" ]; then
    DEPLOY_HOST="${DEPLOY_HOST:-$(env_value DEPLOY_HOST)}"
    DEPLOY_USER="${DEPLOY_USER:-$(env_value DEPLOY_USER)}"
    DEPLOY_PATH="${DEPLOY_PATH:-$(env_value DEPLOY_PATH)}"
    DEPLOY_BACKEND="${DEPLOY_BACKEND:-$(env_value DEPLOY_BACKEND)}"
fi

if [ -z "${DEPLOY_HOST:-}" ]; then
    echo "未设置 DEPLOY_HOST" >&2
    exit 1
fi

DEPLOY_USER="${DEPLOY_USER:-root}"
DEPLOY_PORT="${DEPLOY_PORT:-22}"
DEPLOY_PATH="${DEPLOY_PATH:-"~/deploy"}"
DEPLOY_BACKEND="${DEPLOY_BACKEND:-go}"

case "$DEPLOY_BACKEND" in
    go) COMPOSE_FILE=compose.yml ;;
    java) COMPOSE_FILE=compose-java.yml ;;
    *)
        echo "不支持的 DEPLOY_BACKEND: $DEPLOY_BACKEND" >&2
        exit 1
        ;;
esac

SSH_OPTS=(-o StrictHostKeyChecking=accept-new -o ConnectTimeout=10)
REMOTE="${DEPLOY_USER}@${DEPLOY_HOST}"

echo "同步到 ${REMOTE}:${DEPLOY_PATH} ..."
ssh "${SSH_OPTS[@]}" "$REMOTE" "mkdir -p '$DEPLOY_PATH'"

rsync -az --delete --info=progress2 \
    --exclude='.env' \
    --exclude='frontend/cert/' \
    --exclude='backend-go/attachment/' \
    --exclude='backend-java/attachment/' \
    "$SCRIPT_DIR/" "${REMOTE}:${DEPLOY_PATH}/"

echo "重启远程服务 ..."
ssh "${SSH_OPTS[@]}" "$REMOTE" "bash -s" <<EOF
set -euo pipefail
cd $DEPLOY_PATH
chmod +x backend-go/backend-go
docker compose -f '$COMPOSE_FILE' up -d
docker compose restart backend
docker compose restart frontend
EOF

echo "部署完成。"
