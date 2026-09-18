package com.camera.app.bridge

/**
 * Rust 共享库桥接层
 * UniFFI 会自动生成绑定代码，这里做初始化和加载
 *
 * 编译流程：
 * 1. cd shared-core && cargo build --release --target aarch64-linux-android
 * 2. 把 libcamera_shared_core.so 放到 android/app/src/main/jniLibs/arm64-v8a/
 * 3. UniFFI 生成的 Kotlin 类会自动加载这个 .so
 */
object RustBridge {

    init {
        try {
            System.loadLibrary("camera_shared_core")
        } catch (e: UnsatisfiedLinkError) {
            // 开发阶段 .so 可能还未编译，先跳过
            println("⚠️ Rust shared library not loaded: ${e.message}")
        }
    }

    /**
     * 检查 Rust 库是否已加载
     */
    fun isLoaded(): Boolean {
        return try {
            // 调用 UniFFI 生成的函数来验证
            uniffi.camera_shared_core.get_version()
            true
        } catch (e: Throwable) {
            false
        }
    }

    /**
     * 获取 Rust 库版本
     */
    fun getVersion(): String {
        return try {
            uniffi.camera_shared_core.get_version()
        } catch (e: Throwable) {
            "not loaded"
        }
    }
}