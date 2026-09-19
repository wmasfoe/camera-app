#!/bin/bash
set -euo pipefail

# 编译 Rust 共享库到 iOS arm64 + 生成 Swift 绑定
# 用法: ./scripts/build-ios.sh
# 需要: macOS + Xcode

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT_DIR="$(dirname "$SCRIPT_DIR")"
SHARED_CORE="$ROOT_DIR/shared-core"
IOS_FRAMEWORKS="$ROOT_DIR/ios/CameraApp/Generated"

echo "==> Building Rust → iOS arm64..."

# 检查是否在 macOS
if [[ "$(uname)" != "Darwin" ]]; then
    echo "❌ iOS build requires macOS with Xcode"
    exit 1
fi

# 编译
cd "$SHARED_CORE"
cargo build --release --target aarch64-apple-ios

# 生成 Swift 绑定
echo "==> Generating Swift bindings..."
mkdir -p "$IOS_FRAMEWORKS"
cargo run --bin uniffi-bindgen -- generate \
    --library target/aarch64-apple-ios/release/libcamera_shared_core.a \
    --language swift \
    --out-dir "$IOS_FRAMEWORKS/"

echo "✅ iOS build complete"
echo "   Library: target/aarch64-apple-ios/release/libcamera_shared_core.a"
echo "   Swift: $IOS_FRAMEWORKS/"