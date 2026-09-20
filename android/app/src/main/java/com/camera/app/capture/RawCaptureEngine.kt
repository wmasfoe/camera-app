package com.camera.app.capture

import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.DngCreator
import android.hardware.camera2.TotalCaptureResult
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.SessionConfiguration
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.util.Size
import android.view.Surface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume

/**
 * RAW + JPEG 同时捕获引擎
 *
 * 架构:
 * - CameraX 负责预览 (调用方管理)
 * - 本类负责在预览暂停期间用 Camera2 捕获 RAW+JPEG
 * - 调用方负责在捕获前暂停 CameraX，捕获后恢复
 *
 * 生命周期:
 * - create() → open() → capture() → close()
 * - 或使用 companion object 的 captureRawJpeg() 一步完成
 */
class RawCaptureEngine(private val context: Context) {

    companion object {
        /**
         * 一步完成 RAW+JPEG 捕获
         * 返回 Pair(jpegBytes, dngBytes) 或 null
         */
        suspend fun captureRawJpeg(
            context: Context,
            facingFront: Boolean,
            flashMode: Int,
            sensorOrientation: Int
        ): Pair<ByteArray, ByteArray>? = withContext(Dispatchers.IO) {
            val engine = RawCaptureEngine(context)
            try {
                engine.open(facingFront)
                engine.capture(flashMode, sensorOrientation)
            } catch (e: Throwable) {
                null
            } finally {
                engine.close()
            }
        }

        /**
         * 检测设备是否支持 RAW
         */
        fun isRawSupported(context: Context, cameraId: String): Boolean {
            return try {
                val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
                val chars = manager.getCameraCharacteristics(cameraId)
                val caps = chars.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)
                caps?.contains(CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_RAW) == true
            } catch (_: Exception) { false }
        }

        /**
         * 获取传感器方向 (度)
         */
        fun getSensorOrientation(context: Context, cameraId: String): Int {
            return try {
                val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
                val chars = manager.getCameraCharacteristics(cameraId)
                chars.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0
            } catch (_: Exception) { 0 }
        }

        /**
         * 相机信息
         */
        data class CameraInfo(
            val id: String,
            val facing: Int,  // CameraCharacteristics.LENS_FACING_*
            val focalLengths: FloatArray,
            val isUltraWide: Boolean
        )

        /**
         * 列出所有相机及其焦距
         */
        fun listCameras(context: Context): List<CameraInfo> {
            val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            return manager.cameraIdList.map { id ->
                val chars = manager.getCameraCharacteristics(id)
                val facing = chars.get(CameraCharacteristics.LENS_FACING) ?: CameraCharacteristics.LENS_FACING_BACK
                val focalLengths = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS) ?: floatArrayOf()
                // 超广角判断: 焦距 < 2.5mm (通常 1.x mm)
                val isUltraWide = focalLengths.isNotEmpty() && focalLengths[0] < 2.5f && facing == CameraCharacteristics.LENS_FACING_BACK
                CameraInfo(id, facing, focalLengths, isUltraWide)
            }
        }

        /**
         * 根据前后置查找 cameraId (默认主摄)
         */
        fun findCameraId(context: Context, facingFront: Boolean, cameraIndex: Int = 0): String? {
            val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val matching = manager.cameraIdList.filter { id ->
                val chars = manager.getCameraCharacteristics(id)
                val facing = chars.get(CameraCharacteristics.LENS_FACING)
                if (facingFront) facing == CameraCharacteristics.LENS_FACING_FRONT
                else facing == CameraCharacteristics.LENS_FACING_BACK
            }
            return matching.getOrNull(cameraIndex)
        }
    }

    private val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager

    // 复用的 HandlerThread (整个引擎生命周期)
    private val handlerThread = HandlerThread("RawCaptureEngine").apply { start() }
    private val handler = Handler(handlerThread.looper)

    // 相机设备
    private var device: CameraDevice? = null
    private var session: CameraCaptureSession? = null

    // ImageReader
    private var jpegReader: ImageReader? = null
    private var rawReader: ImageReader? = null

    // 捕获结果
    private data class CaptureOutput(
        val jpegBytes: ByteArray,
        val dngBytes: ByteArray,
        val result: TotalCaptureResult
    )

    /**
     * 打开相机设备
     */
    suspend fun open(facingFront: Boolean) {
        val cameraId = findCameraId(context, facingFront)
            ?: throw IllegalStateException("Camera not found")

        device = suspendCancellableCoroutine { cont ->
            try {
                manager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                    override fun onOpened(camera: CameraDevice) {
                        if (cont.isActive) cont.resume(camera)
                    }
                    override fun onDisconnected(camera: CameraDevice) {
                        camera.close()
                        if (cont.isActive) cont.resume(camera) // 会在后续失败
                    }
                    override fun onError(camera: CameraDevice, error: Int) {
                        camera.close()
                        if (cont.isActive) cont.resume(camera) // 会在后续失败
                    }
                }, handler)
            } catch (e: SecurityException) {
                // 没有相机权限
            }
        }

        // 创建 ImageReader
        val chars = manager.getCameraCharacteristics(
            findCameraId(context, facingFront)!!
        )
        val sizes = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)

        val jpegSize = sizes?.getOutputSizes(ImageFormat.JPEG)
            ?.maxByOrNull { it.width * it.height }
            ?: Size(4000, 3000)

        val rawSize = sizes?.getOutputSizes(ImageFormat.RAW_SENSOR)
            ?.maxByOrNull { it.width * it.height }
            ?: Size(4000, 3000)

        jpegReader = ImageReader.newInstance(jpegSize.width, jpegSize.height, ImageFormat.JPEG, 2)
        rawReader = ImageReader.newInstance(rawSize.width, rawSize.height, ImageFormat.RAW_SENSOR, 2)
    }

    /**
     * 创建捕获会话
     */
    private suspend fun createSession() {
        val dev = device ?: throw IllegalStateException("Device not opened")
        val jpegSurface = jpegReader?.surface ?: throw IllegalStateException("JPEG reader not created")
        val rawSurface = rawReader?.surface ?: throw IllegalStateException("RAW reader not created")

        session = suspendCancellableCoroutine { cont ->
            val outputs = listOf(
                OutputConfiguration(jpegSurface),
                OutputConfiguration(rawSurface)
            )
            val sessionConfig = SessionConfiguration(
                SessionConfiguration.SESSION_REGULAR,
                outputs,
                context.mainExecutor,
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(s: CameraCaptureSession) {
                        if (cont.isActive) cont.resume(s)
                    }
                    override fun onConfigureFailed(s: CameraCaptureSession) {
                        // 配置失败，尝试只用 JPEG
                    }
                }
            )
            dev.createCaptureSession(sessionConfig)
        }
    }

    /**
     * 执行 RAW+JPEG 捕获
     *
     * 流程:
     * 1. 创建会话
     * 2. 发送预捕获请求 (自动对焦+测光)
     * 3. 等待 AF/AE 收敛
     * 4. 发送最终捕获请求 (JPEG+RAW)
     * 5. 读取结果
     * 6. 用 DngCreator 写入 DNG
     */
    suspend fun capture(flashMode: Int, sensorOrientation: Int): Pair<ByteArray, ByteArray>? {
        val dev = device ?: return null

        createSession()
        val sess = session ?: return null

        val jpegSurface = jpegReader?.surface ?: return null
        val rawSurface = rawReader?.surface ?: return null

        try {
            // ── Step 1: 预捕获 (AF/AE 收敛) ──

            val preCaptureBuilder = dev.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            preCaptureBuilder.addTarget(jpegSurface)

            // 设置 AE 模式
            when (flashMode) {
                0 -> { // FLASH_MODE_AUTO
                    preCaptureBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH)
                }
                1 -> { // FLASH_MODE_ON
                    preCaptureBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                    preCaptureBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_SINGLE)
                }
                else -> { // FLASH_MODE_OFF
                    preCaptureBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                    preCaptureBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF)
                }
            }

            // 触发 AF
            preCaptureBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
            preCaptureBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_START)

            // 触发 AE 预捕获
            preCaptureBuilder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER, CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_START)

            // 等待 AF/AE 收敛
            suspendCancellableCoroutine<Unit> { cont ->
                sess.capture(preCaptureBuilder.build(), object : CameraCaptureSession.CaptureCallback() {
                    override fun onCaptureCompleted(session: CameraCaptureSession, request: CaptureRequest, result: TotalCaptureResult) {
                        val afState = result.get(CaptureResult.CONTROL_AF_STATE)
                        val aeState = result.get(CaptureResult.CONTROL_AE_STATE)
                        // AF 已收敛或 AE 已锁定时继续
                        if (afState == CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED ||
                            afState == CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED ||
                            afState == CaptureResult.CONTROL_AF_STATE_PASSIVE_FOCUSED ||
                            aeState == CaptureResult.CONTROL_AE_STATE_CONVERGED ||
                            aeState == CaptureResult.CONTROL_AE_STATE_FLASH_REQUIRED ||
                            aeState == CaptureResult.CONTROL_AE_STATE_LOCKED) {
                            if (cont.isActive) cont.resume(Unit)
                        }
                    }
                }, handler)
            }

            // ── Step 2: 最终捕获 (JPEG + RAW) ──

            val captureBuilder = dev.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
            captureBuilder.addTarget(jpegSurface)
            captureBuilder.addTarget(rawSurface)

            // JPEG 参数
            captureBuilder.set(CaptureRequest.JPEG_QUALITY, 95.toByte())
            captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, sensorOrientation)

            // AE 模式
            when (flashMode) {
                0 -> captureBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH)
                1 -> captureBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                else -> captureBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
            }

            // AF 锁定
            captureBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)

            // RAW 最优参数
            captureBuilder.set(CaptureRequest.STATISTICS_LENS_SHADING_MAP_MODE, CaptureRequest.STATISTICS_LENS_SHADING_MAP_MODE_ON)
            captureBuilder.set(CaptureRequest.NOISE_REDUCTION_MODE, CaptureRequest.NOISE_REDUCTION_MODE_HIGH_QUALITY)
            captureBuilder.set(CaptureRequest.EDGE_MODE, CaptureRequest.EDGE_MODE_HIGH_QUALITY)

            // 执行捕获，等待结果
            val captureResult = suspendCancellableCoroutine<TotalCaptureResult> { cont ->
                sess.capture(captureBuilder.build(), object : CameraCaptureSession.CaptureCallback() {
                    override fun onCaptureCompleted(session: CameraCaptureSession, request: CaptureRequest, result: TotalCaptureResult) {
                        if (cont.isActive) cont.resume(result)
                    }
                    override fun onCaptureFailed(session: CameraCaptureSession, request: CaptureRequest, failure: android.hardware.camera2.CaptureFailure) {
                        if (cont.isActive) cont.resume(null as TotalCaptureResult) // 触发 NPE 走 catch
                    }
                }, handler)
            }

            // ── Step 3: 读取结果 ──

            // 读取 JPEG
            val jpegImage = jpegReader?.acquireLatestImage()
            val jpegBytes: ByteArray? = if (jpegImage != null) {
                try {
                    val buf = jpegImage.planes[0].buffer
                    ByteArray(buf.remaining()).also { buf.get(it) }
                } finally {
                    jpegImage.close()
                }
            } else null

            // 读取 RAW + 写入 DNG
            val rawImage = rawReader?.acquireLatestImage()
            val dngBytes: ByteArray? = if (rawImage != null) {
                try {
                    val dngFile = File(context.cacheDir, "raw_${System.currentTimeMillis()}.dng")
                    try {
                        val chars = manager.getCameraCharacteristics(
                            findCameraId(context, false) ?: "0"
                        )
                        val dngCreator = DngCreator(chars, captureResult)
                        dngCreator.setOrientation(sensorOrientation)
                        dngCreator.writeImage(dngFile.outputStream(), rawImage)
                        dngCreator.close()
                        dngFile.readBytes()
                    } finally {
                        dngFile.delete()
                    }
                } finally {
                    rawImage.close()
                }
            } else null

            if (jpegBytes != null && dngBytes != null) {
                return Pair(jpegBytes, dngBytes)
            }
            return null

        } catch (e: Throwable) {
            return null
        }
    }

    /**
     * 关闭引擎，释放所有资源
     */
    fun close() {
        try { session?.close() } catch (_: Exception) {}
        try { device?.close() } catch (_: Exception) {}
        try { jpegReader?.close() } catch (_: Exception) {}
        try { rawReader?.close() } catch (_: Exception) {}
        session = null; device = null; jpegReader = null; rawReader = null
        handlerThread.quitSafely()
    }
}