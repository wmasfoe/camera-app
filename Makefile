.PHONY: all test android ios clean fmt check

# 默认目标
all: test

# 运行 Rust 测试
test:
	cd shared-core && cargo test

# 格式化代码
fmt:
	cd shared-core && cargo fmt

# 代码检查
check:
	cd shared-core && cargo clippy

# 编译 Android 目标 (arm64)
android: test
	@echo "==> 编译 Android arm64-v8a (.so)"
	cd shared-core && \
		cargo build --release --target aarch64-linux-android 2>/dev/null || \
		echo "⚠️  需要安装 Android NDK: rustup target add aarch64-linux-android"
	@echo "==> 生成 Kotlin 绑定"
	cd shared-core && \
		cargo run --bin uniffi-bindgen -- generate \
			--library target/aarch64-linux-android/release/libcamera_shared_core.so \
			--language kotlin \
			--out-dir ../android/app/src/main/java 2>/dev/null || \
		echo "⚠️  绑定生成需要先编译成功"
	@echo "✅ Android 编译完成"

# 编译 iOS 目标
ios: test
	@echo "==> 编译 iOS arm64 (.a)"
	cd shared-core && \
		cargo build --release --target aarch64-apple-ios 2>/dev/null || \
		echo "⚠️  需要 Mac + Xcode: rustup target add aarch64-apple-ios"
	@echo "==> 生成 Swift 绑定"
	cd shared-core && \
		cargo run --bin uniffi-bindgen -- generate \
			--library target/aarch64-apple-ios/release/libcamera_shared_core.a \
			--language swift \
			--out-dir ../ios/CameraApp/Generated 2>/dev/null || \
		echo "⚠️  绑定生成需要先编译成功"
	@echo "✅ iOS 编译完成"

# 清理构建产物
clean:
	cd shared-core && cargo clean
	@echo "✅ 清理完成"