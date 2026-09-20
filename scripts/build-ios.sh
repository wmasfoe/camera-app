#!/bin/bash
set -euo pipefail

# Cross-compile Rust shared-core for iOS + generate UniFFI Swift bindings
# Usage: ./scripts/build-ios.sh [--simulator]
#
# Outputs:
#   - libcamera_shared_core.a  (static library)
#   - camera_shared_core.swift (UniFFI generated bindings)
#   - camera_shared_core.h     (C header)
#
# Requires: macOS with Xcode + Rust toolchain

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT_DIR="$(dirname "$SCRIPT_DIR")"
SHARED_CORE="$ROOT_DIR/shared-core"
TARGET_DIR="$ROOT_DIR/target"  # Cargo workspace target 目录
OUTPUT_DIR="${OUTPUT_DIR:-$ROOT_DIR/ios/CameraApp/Generated}"

# Parse arguments
BUILD_SIMULATOR=false
for arg in "$@"; do
    case $arg in
        --simulator) BUILD_SIMULATOR=true ;;
        --output=*) OUTPUT_DIR="${arg#*=}" ;;
    esac
done

# ── Pre-flight checks ──────────────────────────────────────────────

if [[ "$(uname)" != "Darwin" ]]; then
    echo "❌ iOS cross-compilation requires macOS with Xcode"
    echo "   On CI, this runs on macos-latest"
    exit 1
fi

if ! command -v xcodebuild &>/dev/null; then
    echo "❌ Xcode not found. Install Xcode from the App Store"
    exit 1
fi

if ! rustup target list --installed | grep -q aarch64-apple-ios; then
    echo "==> Installing iOS Rust targets..."
    rustup target add aarch64-apple-ios aarch64-apple-ios-sim x86_64-apple-ios
fi

# ── Build targets ───────────────────────────────────────────────────

DEVICE_TARGET="aarch64-apple-ios"
SIM_ARM64_TARGET="aarch64-apple-ios-sim"
SIM_X86_64_TARGET="x86_64-apple-ios"

echo "==> Building Rust library for iOS..."

cd "$SHARED_CORE"

# Always build for device (arm64)
echo "  → Building for device ($DEVICE_TARGET)..."
cargo build --release --target "$DEVICE_TARGET"

if [[ "$BUILD_SIMULATOR" == "true" ]]; then
    # Build for both simulator architectures
    echo "  → Building for simulator ($SIM_ARM64_TARGET)..."
    cargo build --release --target "$SIM_ARM64_TARGET"

    echo "  → Building for simulator ($SIM_X86_64_TARGET)..."
    cargo build --release --target "$SIM_X86_64_TARGET"

    # Create universal simulator library with lipo
    echo "  → Creating universal simulator library..."
    mkdir -p "$TARGET_DIR/universal-sim/release"
    lipo -create \
        "$TARGET_DIR/$SIM_ARM64_TARGET/release/libcamera_shared_core.a" \
        "$TARGET_DIR/$SIM_X86_64_TARGET/release/libcamera_shared_core.a" \
        -output "$TARGET_DIR/universal-sim/release/libcamera_shared_core.a"
fi

# ── Generate UniFFI Swift bindings ──────────────────────────────────

echo "==> Generating UniFFI Swift bindings..."
mkdir -p "$OUTPUT_DIR"

# Use the built library to generate bindings
cargo run --bin uniffi-bindgen -- generate \
    --library "$TARGET_DIR/$DEVICE_TARGET/release/libcamera_shared_core.a" \
    --language swift \
    --out-dir "$OUTPUT_DIR/"

# ── Summary ─────────────────────────────────────────────────────────

echo ""
echo "✅ iOS build complete!"
echo ""
echo "   Device library:"
echo "     $TARGET_DIR/$DEVICE_TARGET/release/libcamera_shared_core.a"

if [[ "$BUILD_SIMULATOR" == "true" ]]; then
    echo ""
    echo "   Simulator (universal) library:"
    echo "     $TARGET_DIR/universal-sim/release/libcamera_shared_core.a"
fi

echo ""
echo "   Swift bindings:"
echo "     $OUTPUT_DIR/camera_shared_core.swift"
echo "     $OUTPUT_DIR/camera_shared_core.h"
echo ""
echo "   Next steps:"
echo "     1. Open the Xcode project"
echo "     2. Add the .a library to 'Link Binary With Libraries'"
echo "     3. Add the .swift and .h files to the project"
echo "     4. Set 'Header Search Paths' to include the binding directory"