package com.camera.app.bridge

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 照片保存服务
 * 使用 MediaStore API (scoped storage) 保存 JPEG 到相册
 */
object PhotoSaver {

    /**
     * 保存 JPEG 字节到系统相册
     * @param context Android Context
     * @param jpegBytes JPEG 图片字节
     * @param displayName 文件名（不含扩展名），默认用时间戳
     * @return 保存成功返回 Uri，失败返回 null
     */
    suspend fun saveJpegToGallery(
        context: Context,
        jpegBytes: ByteArray,
        displayName: String? = null
    ): Uri? = withContext(Dispatchers.IO) {
        val name = displayName ?: generateFileName()

        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "$name.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10+ scoped storage
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/CameraApp")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            // Android 8-9: 设置日期
            put(MediaStore.Images.Media.DATE_ADDED, System.currentTimeMillis() / 1000)
            put(MediaStore.Images.Media.DATE_MODIFIED, System.currentTimeMillis() / 1000)
        }

        val resolver = context.contentResolver
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

        val uri = resolver.insert(collection, contentValues)
        if (uri == null) {
            return@withContext null
        }

        try {
            // 写入 JPEG 字节
            resolver.openOutputStream(uri)?.use { stream ->
                stream.write(jpegBytes)
                stream.flush()
            } ?: return@withContext null.also {
                resolver.delete(uri, null, null)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // 清除 IS_PENDING，让图片在相册中可见
                contentValues.clear()
                contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, contentValues, null, null)
            }

            uri
        } catch (e: Exception) {
            // 写入失败，清理
            resolver.delete(uri, null, null)
            null
        }
    }

    /**
     * 保存 Bitmap 到系统相册
     */
    suspend fun saveBitmapToGallery(
        context: Context,
        bitmap: Bitmap,
        quality: Int = 95,
        displayName: String? = null
    ): Uri? {
        val bytes = bitmapToJpeg(bitmap, quality)
        return saveJpegToGallery(context, bytes, displayName)
    }

    /**
     * Bitmap 转 JPEG 字节
     */
    fun bitmapToJpeg(bitmap: Bitmap, quality: Int = 95): ByteArray {
        val stream = java.io.ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)
        return stream.toByteArray()
    }

    /**
     * JPEG 字节转 Bitmap
     */
    fun jpegToBitmap(jpegBytes: ByteArray): Bitmap? {
        return BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)
    }

    private fun generateFileName(): String {
        val sdf = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US)
        return "IMG_${sdf.format(Date())}"
    }
}