package com.camera.app.bridge

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import uniffi.camera_shared_core.CameraException
import uniffi.camera_shared_core.FilterType
import uniffi.camera_shared_core.ImageProcessor

/**
 * Rust 共享库桥接层
 * 封装 UniFFI 生成的绑定，提供 Kotlin 友好的 API
 */
object RustBridge {

    private var _processor: ImageProcessor? = null

    /**
     * 初始化 Rust 库（在 Application 启动时调用）
     */
    fun init() {
        try {
            _processor = ImageProcessor()
            val version = uniffi.camera_shared_core.getVersion()
            println("✅ Rust shared core v$version loaded")
        } catch (e: Throwable) {
            println("⚠️ Rust library not loaded: ${e.message}")
        }
    }

    /**
     * 检查 Rust 库是否可用
     */
    fun isLoaded(): Boolean = _processor != null

    /**
     * 获取 Rust 库版本
     */
    fun getVersion(): String {
        return try {
            uniffi.camera_shared_core.getVersion()
        } catch (e: Throwable) {
            "not loaded"
        }
    }

    /**
     * 获取支持的滤镜列表
     */
    fun supportedFilters(): List<FilterType> {
        return _processor?.supportedFilters() ?: emptyList()
    }

    /**
     * 获取滤镜名称
     */
    fun filterName(filter: FilterType): String {
        return _processor?.filterName(filter) ?: filter.name
    }

    /**
     * 对 JPEG 字节应用滤镜（在 IO 线程执行）
     */
    suspend fun applyFilter(
        imageBytes: ByteArray,
        filter: FilterType
    ): Result<ByteArray> = withContext(Dispatchers.IO) {
        try {
            val processor = _processor ?: return@withContext Result.failure(
                IllegalStateException("Rust library not loaded")
            )
            val result = processor.applyFilter(imageBytes, filter)
            Result.success(result)
        } catch (e: CameraException) {
            Result.failure(RuntimeException("Camera error: $e"))
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }

    /**
     * 自动增强图片（在 IO 线程执行）
     */
    suspend fun autoEnhance(imageBytes: ByteArray): Result<ByteArray> =
        withContext(Dispatchers.IO) {
            try {
                val processor = _processor ?: return@withContext Result.failure(
                    IllegalStateException("Rust library not loaded")
                )
                val result = processor.autoEnhance(imageBytes)
                Result.success(result)
            } catch (e: CameraException) {
                Result.failure(RuntimeException("Camera error: $e"))
            } catch (e: Throwable) {
                Result.failure(e)
            }
        }

    /**
     * 压缩图片（在 IO 线程执行）
     */
    suspend fun compress(imageBytes: ByteArray, quality: UInt): Result<ByteArray> =
        withContext(Dispatchers.IO) {
            try {
                val processor = _processor ?: return@withContext Result.failure(
                    IllegalStateException("Rust library not loaded")
                )
                val result = processor.compress(imageBytes, quality)
                Result.success(result)
            } catch (e: CameraException) {
                Result.failure(RuntimeException("Camera error: $e"))
            } catch (e: Throwable) {
                Result.failure(e)
            }
        }

    /**
     * JPEG 字节转 Bitmap
     */
    fun bytesToBitmap(bytes: ByteArray): Bitmap? {
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }

    /**
     * Bitmap 转 JPEG 字节
     */
    fun bitmapToBytes(bitmap: Bitmap, quality: Int = 95): ByteArray {
        val stream = java.io.ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)
        return stream.toByteArray()
    }
}