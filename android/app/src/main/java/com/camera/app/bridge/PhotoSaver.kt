package com.camera.app.bridge

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.location.Location
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 照片/视频保存服务
 * 使用 MediaStore API (scoped storage) + EXIF GPS 写入
 */
object PhotoSaver {

    /**
     * 保存 JPEG 到相册，可选写入 GPS 坐标
     */
    suspend fun saveJpegToGallery(
        context: Context,
        jpegBytes: ByteArray,
        displayName: String? = null,
        location: Location? = null
    ): Uri? = withContext(Dispatchers.IO) {
        val name = displayName ?: generateFileName()

        // 写入 EXIF GPS 数据
        val finalBytes = if (location != null) {
            writeExifGps(jpegBytes, location)
        } else {
            jpegBytes
        }

        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "$name.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/CameraApp")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            put(MediaStore.Images.Media.DATE_ADDED, System.currentTimeMillis() / 1000)
            put(MediaStore.Images.Media.DATE_MODIFIED, System.currentTimeMillis() / 1000)
        }

        val resolver = context.contentResolver
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

        val uri = resolver.insert(collection, contentValues) ?: return@withContext null

        try {
            resolver.openOutputStream(uri)?.use { stream ->
                stream.write(finalBytes)
                stream.flush()
            } ?: return@withContext null.also { resolver.delete(uri, null, null) }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, contentValues, null, null)
            }

            uri
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            null
        }
    }

    /**
     * 保存 DNG RAW 文件到相册
     */
    suspend fun saveDngToGallery(
        context: Context,
        dngBytes: ByteArray,
        displayName: String? = null
    ): Uri? = withContext(Dispatchers.IO) {
        val name = displayName ?: generateFileName()

        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "$name.dng")
            put(MediaStore.Images.Media.MIME_TYPE, "image/x-adobe-dng")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/CameraApp/RAW")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            put(MediaStore.Images.Media.DATE_ADDED, System.currentTimeMillis() / 1000)
            put(MediaStore.Images.Media.DATE_MODIFIED, System.currentTimeMillis() / 1000)
        }

        val resolver = context.contentResolver
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

        val uri = resolver.insert(collection, contentValues) ?: return@withContext null

        try {
            resolver.openOutputStream(uri)?.use { stream ->
                stream.write(dngBytes)
                stream.flush()
            } ?: return@withContext null.also { resolver.delete(uri, null, null) }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, contentValues, null, null)
            }

            uri
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            null
        }
    }

    /**
     * 保存视频到相册
     */
    suspend fun saveVideoToGallery(
        context: Context,
        videoFile: File,
        displayName: String? = null
    ): Uri? = withContext(Dispatchers.IO) {
        val name = displayName ?: generateFileName()

        val contentValues = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, "$name.mp4")
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/CameraApp")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
            put(MediaStore.Video.Media.DATE_ADDED, System.currentTimeMillis() / 1000)
            put(MediaStore.Video.Media.DATE_MODIFIED, System.currentTimeMillis() / 1000)
        }

        val resolver = context.contentResolver
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }

        val uri = resolver.insert(collection, contentValues) ?: return@withContext null

        try {
            resolver.openOutputStream(uri)?.use { stream ->
                videoFile.inputStream().use { input -> input.copyTo(stream) }
                stream.flush()
            } ?: return@withContext null.also { resolver.delete(uri, null, null) }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.Video.Media.IS_PENDING, 0)
                resolver.update(uri, contentValues, null, null)
            }

            uri
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            null
        }
    }

    // ── EXIF GPS 写入 ────────────────────────────────────────────────

    /**
     * 在 JPEG 字节中写入 GPS 坐标 (EXIF)
     */
    private fun writeExifGps(jpegBytes: ByteArray, location: Location): ByteArray {
        return try {
            val inputStream = ByteArrayInputStream(jpegBytes)
            val exif = ExifInterface(inputStream)

            // 写入 GPS 坐标
            val lat = location.latitude
            val lon = location.longitude
            exif.setLatLong(lat, lon)

            // 写入海拔
            if (location.hasAltitude()) {
                exif.setAltitude(location.altitude)
            }

            // 写入 GPS 时间戳
            val gpsTimestamp = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US)
                .format(Date(location.time))
            exif.setAttribute(ExifInterface.TAG_GPS_DATESTAMP, gpsTimestamp.split(" ")[0])
            exif.setAttribute(ExifInterface.TAG_GPS_TIMESTAMP, gpsTimestamp.split(" ")[1])

            // 写入处理方法
            exif.setAttribute(ExifInterface.TAG_GPS_PROCESSING_METHOD, "GPS")

            // 保存到新的字节数组
            val outputStream = ByteArrayOutputStream()
            val outputExif = ExifInterface(outputStream)
            // 从原始 EXIF 复制所有属性
            copyExifAttributes(exif, outputExif)
            outputExif.saveAttributes()

            outputStream.toByteArray()
        } catch (e: Exception) {
            // EXIF 写入失败，返回原始字节
            jpegBytes
        }
    }

    private fun copyExifAttributes(source: ExifInterface, dest: ExifInterface) {
        val tags = listOf(
            ExifInterface.TAG_GPS_LATITUDE,
            ExifInterface.TAG_GPS_LATITUDE_REF,
            ExifInterface.TAG_GPS_LONGITUDE,
            ExifInterface.TAG_GPS_LONGITUDE_REF,
            ExifInterface.TAG_GPS_ALTITUDE,
            ExifInterface.TAG_GPS_ALTITUDE_REF,
            ExifInterface.TAG_GPS_DATESTAMP,
            ExifInterface.TAG_GPS_TIMESTAMP,
            ExifInterface.TAG_GPS_PROCESSING_METHOD,
            ExifInterface.TAG_DATETIME,
            ExifInterface.TAG_MAKE,
            ExifInterface.TAG_MODEL,
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.TAG_IMAGE_WIDTH,
            ExifInterface.TAG_IMAGE_LENGTH,
            ExifInterface.TAG_SOFTWARE
        )
        for (tag in tags) {
            val value = source.getAttribute(tag)
            if (value != null) {
                dest.setAttribute(tag, value)
            }
        }
    }

    // ── 工具方法 ──────────────────────────────────────────────────────

    fun bitmapToJpeg(bitmap: Bitmap, quality: Int = 95): ByteArray {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)
        return stream.toByteArray()
    }

    fun jpegToBitmap(jpegBytes: ByteArray): Bitmap? {
        return BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)
    }

    private fun generateFileName(): String {
        val sdf = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US)
        return "IMG_${sdf.format(Date())}"
    }
}