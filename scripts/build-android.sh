#!/bin/bash
set -euo pipefail

# 编译 Rust 共享库到 Android arm64 + 生成 Kotlin 绑定
# 用法: ./scripts/build-android.sh

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT_DIR="$(dirname "$SCRIPT_DIR")"
SHARED_CORE="$ROOT_DIR/shared-core"
ANDROID_JNILIBS="$ROOT_DIR/android/app/src/main/jniLibs/arm64-v8a"
ANDROID_JAVA="$ROOT_DIR/android/app/src/main/java"

echo "==> Building Rust → Android arm64..."

# 检查 cargo-ndk
if ! command -v cargo-ndk &>/dev/null; then
    echo "Installing cargo-ndk..."
    cargo install cargo-ndk
fi

# 检查 Android NDK
if [ -z "${ANDROID_NDK_HOME:-}" ]; then
    echo "⚠️  ANDROID_NDK_HOME not set. cargo-ndk will try to auto-detect."
fi

# 编译
cd "$SHARED_CORE"
cargo ndk -t arm64-v8a build --release

# 复制 .so
echo "==> Copying .so to Android project..."
mkdir -p "$ANDROID_JNILIBS"
cp "$SHARED_CORE/target/aarch64-linux-android/release/libcamera_shared_core.so" "$ANDROID_JNILIBS/"

# 生成 Kotlin 绑定
echo "==> Generating Kotlin bindings..."
cargo run --bin uniffi-bindgen -- generate \
    --library target/aarch64-linux-android/release/libcamera_shared_core.so \
    --language kotlin \
    --out-dir "$ANDROID_JAVA/"

echo "✅ Android build complete"
echo "   .so: $ANDROID_JNILIBS/libcamera_shared_core.so"
echo "   Kotlin: $ANDROID_JAVA/uniffi/"