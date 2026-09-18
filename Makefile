.PHONY: all test android android-so android-bindings ios clean fmt check

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

# 编译 Android .so (arm64)
# 需要: rustup target add aarch64-linux-android + Android NDK
android-so:
	@echo "==> 编译 Android arm64-v8a (.so)"
	cd shared-core && cargo ndk -t arm64-v8a build --release
	@echo "==> 复制 .so 到 Android 项目"
	mkdir -p android/app/src/main/jniLibs/arm64-v8a/
	cp shared-core/target/aarch64-linux-android/release/libcamera_shared_core.so \
		android/app/src/main/jniLibs/arm64-v8a/
	@echo "✅ .so 编译完成"

# 生成 Kotlin 绑定
android-bindings:
	@echo "==> 生成 Kotlin 绑定"
	cd shared-core && cargo run --bin uniffi-bindgen -- generate \
		--library target/aarch64-linux-android/release/libcamera_shared_core.so \
		--language kotlin \
		--out-dir ../android/app/src/main/java/
	@echo "✅ Kotlin 绑定生成完成"

# 完整 Android 构建 (so + 绑定)
android: test android-so android-bindings
	@echo "✅ Android 完整构建完成"

# 编译 iOS 目标 (需要 Mac + Xcode)
ios: test
	@echo "==> 编译 iOS arm64 (.a)"
	cd shared-core && cargo build --release --target aarch64-apple-ios
	@echo "==> 生成 Swift 绑定"
	cd shared-core && cargo run --bin uniffi-bindgen -- generate \
		--library target/aarch64-apple-ios/release/libcamera_shared_core.a \
		--language swift \
		--out-dir ../ios/CameraApp/Generated/
	@echo "✅ iOS 编译完成"

# 清理构建产物
clean:
	cd shared-core && cargo clean
	rm -f android/app/src/main/jniLibs/arm64-v8a/libcamera_shared_core.so
	rm -rf android/app/src/main/java/uniffi/
	@echo "✅ 清理完成"