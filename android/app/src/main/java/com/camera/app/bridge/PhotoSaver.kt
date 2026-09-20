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
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object PhotoSaver {

    suspend fun saveJpegToGallery(
        context: Context,
        jpegBytes: ByteArray,
        displayName: String? = null,
        location: Location? = null
    ): Uri? = withContext(Dispatchers.IO) {
        val name = displayName ?: generateFileName()
        val finalBytes = if (location != null) writeExifGps(context, jpegBytes, location) else jpegBytes

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
            resolver.openOutputStream(uri)?.use { it.write(finalBytes); it.flush() }
                ?: return@withContext null.also { resolver.delete(uri, null, null) }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, contentValues, null, null)
            }
            uri
        } catch (e: Exception) {
            resolver.delete(uri, null, null); null
        }
    }

    suspend fun saveDngToGallery(context: Context, dngBytes: ByteArray, displayName: String? = null, location: Location? = null): Uri? =
        withContext(Dispatchers.IO) {
            val name = displayName ?: generateFileName()
            val finalBytes = if (location != null) writeExifGps(context, dngBytes, location) else dngBytes
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
                resolver.openOutputStream(uri)?.use { it.write(finalBytes); it.flush() }
                    ?: return@withContext null.also { resolver.delete(uri, null, null) }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    contentValues.clear()
                    contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                    resolver.update(uri, contentValues, null, null)
                }
                uri
            } catch (e: Exception) {
                resolver.delete(uri, null, null); null
            }
        }

    /**
     * 保存视频到相册，可选写入 GPS 坐标到 MediaStore 元数据
     */
    suspend fun saveVideoToGallery(
        context: Context,
        videoFile: File,
        displayName: String? = null,
        location: Location? = null
    ): Uri? = withContext(Dispatchers.IO) {
        val name = displayName ?: generateFileName()
        val contentValues = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, "$name.mp4")
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/CameraApp")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
            // GPS 元数据
            if (location != null) {
                put(MediaStore.Video.Media.LATITUDE, location.latitude)
                put(MediaStore.Video.Media.LONGITUDE, location.longitude)
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
                videoFile.inputStream().use { it.copyTo(stream) }; stream.flush()
            } ?: return@withContext null.also { resolver.delete(uri, null, null) }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.Video.Media.IS_PENDING, 0)
                resolver.update(uri, contentValues, null, null)
            }
            uri
        } catch (e: Exception) {
            resolver.delete(uri, null, null); null
        }
    }

    // ── EXIF GPS ─────────────────────────────────────────────────────

    private fun writeExifGps(context: Context, jpegBytes: ByteArray, location: Location): ByteArray {
        return try {
            val tmpFile = File(context.cacheDir, "tmp_exif_${System.currentTimeMillis()}.jpg")
            try {
                tmpFile.writeBytes(jpegBytes)
                val exif = ExifInterface(tmpFile.absolutePath)
                exif.setLatLong(location.latitude, location.longitude)
                if (location.hasAltitude()) exif.setAltitude(location.altitude)
                val ts = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US).format(Date(location.time))
                exif.setAttribute(ExifInterface.TAG_GPS_DATESTAMP, ts.split(" ")[0])
                exif.setAttribute(ExifInterface.TAG_GPS_TIMESTAMP, ts.split(" ")[1])
                exif.setAttribute(ExifInterface.TAG_GPS_PROCESSING_METHOD, "GPS")
                exif.saveAttributes()
                tmpFile.readBytes()
            } finally {
                tmpFile.delete()
            }
        } catch (e: Exception) {
            jpegBytes
        }
    }

    // ── 工具 ─────────────────────────────────────────────────────────

    fun bitmapToJpeg(bitmap: Bitmap, quality: Int = 95): ByteArray {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)
        return stream.toByteArray()
    }

    fun jpegToBitmap(jpegBytes: ByteArray): Bitmap? {
        return BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)
    }

    private fun generateFileName(): String {
        return "IMG_${SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())}"
    }
}