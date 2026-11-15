#!/bin/bash
set -e

# 单独打包 Rust 版本（学习实现）。
# 与 build.sh 解耦：它只产出 deploy/backend-rust，不影响 Java/Go/前端交付，也不参与常规 CI。
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT_DIR="$(dirname "$SCRIPT_DIR")"
BACKEND_RUST_DIR="$ROOT_DIR/backend-rust"
DEPLOY_DIR="$ROOT_DIR/deploy"
RUST_TARGET="x86_64-unknown-linux-musl"

if ! command -v cargo >/dev/null 2>&1; then
    echo "未找到 cargo，请先安装 Rust 工具链：https://rustup.rs"
    exit 1
fi

echo "[1/2] 编译 Rust 后端（$RUST_TARGET）..."
cd "$BACKEND_RUST_DIR"

# musl 目标需要显式安装；rustup 不存在的环境（如发行版自带 cargo）跳过检查
if command -v rustup >/dev/null 2>&1 \
    && ! rustup target list --installed | grep -qx "$RUST_TARGET"; then
    echo "未安装目标 $RUST_TARGET，正在通过 rustup 添加..."
    rustup target add "$RUST_TARGET"
fi

# zigbuild 能省掉自备 musl 交叉链接器；没有就用 rustup 的 musl 工具链
if command -v cargo-zigbuild >/dev/null 2>&1; then
    cargo zigbuild --release --locked --target "$RUST_TARGET"
else
    cargo build --release --locked --target "$RUST_TARGET"
fi

RUST_BIN="$BACKEND_RUST_DIR/target/$RUST_TARGET/release/billmanager"
if [ ! -f "$RUST_BIN" ]; then
    echo "未找到编译产物：$RUST_BIN"
    exit 1
fi

echo "[2/2] 组装 Rust 交付物..."
# 只重建 Rust 自己的交付目录，不碰 backend-java / backend-go / frontend
rm -rf "$DEPLOY_DIR/backend-rust"
mkdir -p "$DEPLOY_DIR/backend-rust"
cp "$RUST_BIN" "$DEPLOY_DIR/backend-rust/backend-rust"
cp -r "$BACKEND_RUST_DIR/config" "$DEPLOY_DIR/backend-rust/"
# 在 Windows 挂载盘（/mnt/c）上 chmod 可能是空操作，失败不影响交付
chmod +x "$DEPLOY_DIR/backend-rust/backend-rust" 2>/dev/null || true

echo "Rust 打包完成：$DEPLOY_DIR/backend-rust"
echo "提示：Rust 版本仅用于学习与本地体验，不随 deploy/build.sh 一起打包。"
