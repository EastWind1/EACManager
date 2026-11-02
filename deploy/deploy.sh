#!/usr/bin/env bash

#   DEPLOY_HOST      远程主机，必填
#   DEPLOY_USER      SSH 用户，默认 root
#   DEPLOY_PATH      远程部署目录，默认 deploy
#   DEPLOY_BACKEND   go 或 java，默认 go
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ENV_FILE="$SCRIPT_DIR/.env"

# get_cfg KEY [DEFAULT]
# 优先级：环境变量 > .env > 默认值
get_cfg() {
    local key="$1"
    local default="${2:-}"
    local val

    if [[ -n "${!key:-}" ]]; then
        val="${!key}"
    else
        val=$(grep -m1 "^${key}=" "$ENV_FILE" 2>/dev/null | cut -d= -f2- | tr -d '\r')
        val="${val:-$default}"
    fi

    echo "$val"
}

DEPLOY_HOST=$(get_cfg DEPLOY_HOST localhost)

if [[ -z "$DEPLOY_HOST" ]]; then
    echo "未设置 DEPLOY_HOST" >&2
    exit 1
fi

DEPLOY_USER=$(get_cfg DEPLOY_USER root)
DEPLOY_PATH=$(get_cfg DEPLOY_PATH deploy)
DEPLOY_BACKEND=$(get_cfg DEPLOY_BACKEND go)

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
