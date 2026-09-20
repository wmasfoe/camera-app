package com.camera.app.ui

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import android.location.Location
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.MediaStore
import android.view.MotionEvent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.AspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.FlashAuto
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Landscape
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.camera.app.bridge.PhotoSaver
import com.camera.app.bridge.RustBridge
import com.camera.app.capture.RawCaptureEngine
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import uniffi.camera_shared_core.FilterType
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.math.abs

// ── Tokens ───────────────────────────────────────────────────────────

private val Bg = Color(0xFF000000)
private val Surface = Color(0xFF1A1A1A)
private val Surface2 = Color(0xFF252525)
private val TextPrimary = Color(0xFFFFFFFF)
private val TextMuted = Color(0xFF8E8E93)
private val AccentDim = Color(0xFF636366)
private val AccentGreen = Color(0xFF30D158)
private val GridColor = Color(0x33FFFFFF)
private val Danger = Color(0xFFFF453A)

// ── Main ─────────────────────────────────────────────────────────────

@Composable
fun CameraScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    var hasCameraPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var hasAudioPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
    }
    var hasLocationPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)
    }

    // Photo state
    var capturedBytes by remember { mutableStateOf<ByteArray?>(null) }
    var processedBytes by remember { mutableStateOf<ByteArray?>(null) }
    var selectedFilter by remember { mutableStateOf(FilterType.NONE) }
    var isProcessing by remember { mutableStateOf(false) }

    // Camera controls
    var isFrontCamera by remember { mutableStateOf(false) }
    var backCameraIndex by remember { mutableIntStateOf(0) }  // 0=主摄, 1=超广角, 2=长焦
    var backCameraCount by remember { mutableIntStateOf(1) }
    var flashMode by remember { mutableIntStateOf(ImageCapture.FLASH_MODE_AUTO) }
    var selectedMode by remember { mutableIntStateOf(1) }
    var zoomRatio by remember { mutableFloatStateOf(1f) }
    var showGrid by remember { mutableStateOf(false) }
    var exposureComp by remember { mutableFloatStateOf(0f) }
    var showExposureSlider by remember { mutableStateOf(false) }

    // GPS
    var enableLocation by remember { mutableStateOf(false) }
    var currentLocation by remember { mutableStateOf<Location?>(null) }
    var isLocationLoading by remember { mutableStateOf(false) }

    // RAW
    var enableRaw by remember { mutableStateOf(false) }
    var isRawSupported by remember { mutableStateOf(false) }
    var currentCameraId by remember { mutableStateOf<String?>(null) }
    var sensorOrientation by remember { mutableIntStateOf(0) }

    // Focus
    var focusPoint by remember { mutableStateOf<Offset?>(null) }
    var showFocusRing by remember { mutableStateOf(false) }

    // Shutter flash
    var showFlash by remember { mutableStateOf(false) }

    // Video recording
    var isRecording by remember { mutableStateOf(false) }
    var recordingDuration by remember { mutableIntStateOf(0) }
    var activeRecording by remember { mutableStateOf<Recording?>(null) }

    // Last photo
    var lastPhotoBitmap by remember { mutableStateOf<Bitmap?>(null) }

    // Camera refs
    var camera by remember { mutableStateOf<Camera?>(null) }
    var previewViewRef by remember { mutableStateOf<PreviewView?>(null) }
    var cameraProviderRef by remember { mutableStateOf<ProcessCameraProvider?>(null) }

    // 暂停状态 (RAW 捕获时暂停预览)
    var isPreviewPaused by remember { mutableStateOf(false) }

    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .setFlashMode(flashMode)
            .setTargetAspectRatio(AspectRatio.RATIO_4_3)
            .build()
    }

    val videoCapture = remember {
        val recorder = Recorder.Builder()
            .setQualitySelector(
                androidx.camera.video.QualitySelector.from(
                    androidx.camera.video.Quality.HD,
                    androidx.camera.video.FallbackStrategy.higherQualityOrLowerThan(androidx.camera.video.Quality.SD)
                )
            )
            .build()
        VideoCapture.withOutput(recorder)
    }

    val captureExecutor = remember { Executors.newSingleThreadExecutor() }
    val vibrator = remember { context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator }
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        hasCameraPermission = results[Manifest.permission.CAMERA] == true
        hasAudioPermission = results[Manifest.permission.RECORD_AUDIO] == true
        hasLocationPermission = results[Manifest.permission.ACCESS_FINE_LOCATION] == true
        if (hasLocationPermission) enableLocation = true
    }

    // 绑定 CameraX 预览的函数 (捕获后恢复用)
    fun bindCameraPreview(pv: PreviewView) {
        val provider = cameraProviderRef ?: return
        val preview = Preview.Builder().setTargetAspectRatio(AspectRatio.RATIO_4_3).build()
            .also { it.surfaceProvider = pv.surfaceProvider }
        // 根据 cameraIndex 选择具体相机
        val selector = if (isFrontCamera) {
            CameraSelector.DEFAULT_FRONT_CAMERA
        } else {
            val cameras = RawCaptureEngine.listCameras(context).filter { it.facing == android.hardware.camera2.CameraCharacteristics.LENS_FACING_BACK }
            val targetCam = cameras.getOrNull(backCameraIndex) ?: cameras.firstOrNull()
            if (targetCam != null) {
                val targetId = targetCam.id
                CameraSelector.Builder().addCameraFilter { cameraInfos ->
                    cameraInfos.filter { info ->
                        val cam2Info = androidx.camera.camera2.interop.Camera2CameraInfo.from(info)
                        cam2Info.cameraId == targetId
                    }
                }.build()
            } else CameraSelector.DEFAULT_BACK_CAMERA
        }
        try {
            provider.unbindAll()
            val useCaseGroup = UseCaseGroup.Builder()
                .addUseCase(preview)
                .addUseCase(imageCapture)
                .addUseCase(videoCapture)
                .setViewPort(pv.viewPort!!)
                .build()
            val cam = provider.bindToLifecycle(lifecycleOwner, selector, useCaseGroup)
            camera = cam

            // 记录 cameraId 和传感器方向
            val camId = if (isFrontCamera) RawCaptureEngine.findCameraId(context, true)
            else {
                val cameras = RawCaptureEngine.listCameras(context).filter { it.facing == android.hardware.camera2.CameraCharacteristics.LENS_FACING_BACK }
                cameras.getOrNull(backCameraIndex)?.id ?: cameras.firstOrNull()?.id
            }
            currentCameraId = camId
            if (camId != null) {
                isRawSupported = RawCaptureEngine.isRawSupported(context, camId)
                sensorOrientation = RawCaptureEngine.getSensorOrientation(context, camId)
            }

            isPreviewPaused = false
        } catch (_: Exception) {}
    }

    LaunchedEffect(Unit) {
        val needed = mutableListOf<String>()
        if (!hasCameraPermission) needed.add(Manifest.permission.CAMERA)
        if (!hasAudioPermission) needed.add(Manifest.permission.RECORD_AUDIO)
        if (needed.isNotEmpty()) permissionLauncher.launch(needed.toTypedArray())
        lastPhotoBitmap = loadLastPhotoThumbnail(context)
    }

    LaunchedEffect(enableLocation, hasLocationPermission) {
        if (enableLocation && hasLocationPermission) {
            isLocationLoading = true
            // 先用 lastLocation 快速填充
            try {
                fusedLocationClient.lastLocation.addOnSuccessListener { loc ->
                    if (loc != null && currentLocation == null) currentLocation = loc
                }
            } catch (_: Exception) {}
            // 再获取精确位置
            try {
                val cts = CancellationTokenSource()
                currentLocation = suspendCancellableCoroutine { cont ->
                    fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token)
                        .addOnSuccessListener { loc -> cont.resume(loc) }
                        .addOnFailureListener { cont.resume(null) }
                }
            } catch (_: SecurityException) { currentLocation = null }
            isLocationLoading = false
        } else { currentLocation = null; isLocationLoading = false }
    }

    LaunchedEffect(showFocusRing) { if (showFocusRing) { delay(1200); showFocusRing = false } }
    LaunchedEffect(showFlash) { if (showFlash) { delay(120); showFlash = false } }
    LaunchedEffect(isRecording) {
        if (isRecording) { recordingDuration = 0; while (isRecording) { delay(1000); recordingDuration++ } }
    }

    // ── Actions ──

    fun takePhoto() {
        showFlash = true
        try { vibrator?.vibrate(VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE)) } catch (_: Exception) {}

        // GPS: 优先用 lastLocation 快速获取，再异步更新为最新位置
        if (enableLocation && hasLocationPermission) {
            try {
                fusedLocationClient.lastLocation.addOnSuccessListener { loc ->
                    if (loc != null) currentLocation = loc
                }
                val cts = CancellationTokenSource()
                fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token)
                    .addOnSuccessListener { loc -> if (loc != null) currentLocation = loc }
            } catch (_: SecurityException) {}
        }

        // RAW 模式: 暂停 CameraX → Camera2 捕获 → 恢复 CameraX
        if (enableRaw && isRawSupported && currentCameraId != null) {
            scope.launch {
                isProcessing = true
                isPreviewPaused = true

                try {
                    // 1. 暂停 CameraX 预览
                    val provider = cameraProviderRef
                    if (provider != null) {
                        provider.unbindAll()
                    }

                    // 2. Camera2 捕获 JPEG + RAW
                    val result = RawCaptureEngine.captureRawJpeg(
                        context, isFrontCamera, flashMode, sensorOrientation
                    )

                    if (result != null) {
                        val (jpegBytes, dngBytes) = result
                        capturedBytes = jpegBytes
                        processedBytes = jpegBytes

                        // Rust 自动增强
                        try { RustBridge.autoEnhance(jpegBytes).onSuccess { processedBytes = it } }
                        catch (_: Throwable) {}

                        // 保存 DNG (含 GPS)
                        withContext(Dispatchers.IO) { PhotoSaver.saveDngToGallery(context, dngBytes, location = currentLocation) }

                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "JPEG + RAW saved", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        // RAW 失败，降级到普通 JPEG 并自动保存
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "RAW failed, saving JPEG", Toast.LENGTH_SHORT).show()
                        }
                        // 重新绑定 CameraX 并用 ImageCapture 拍照
                        val pv = previewViewRef
                        if (pv != null && provider != null) {
                            bindCameraPreview(pv)
                            delay(200) // 等预览稳定
                            imageCapture.takePicture(captureExecutor, object : ImageCapture.OnImageCapturedCallback() {
                                override fun onCaptureSuccess(image: ImageProxy) {
                                    val jpegBytes = imageProxyToJpegBytes(image); image.close()
                                    scope.launch {
                                        capturedBytes = jpegBytes; processedBytes = jpegBytes
                                        try { RustBridge.autoEnhance(jpegBytes).onSuccess { processedBytes = it } }
                                        catch (_: Throwable) {}
                                        // 自动保存 JPEG 到相册
                                        val uri = withContext(Dispatchers.IO) {
                                            PhotoSaver.saveJpegToGallery(context, jpegBytes, location = currentLocation)
                                        }
                                        withContext(Dispatchers.Main) {
                                            Toast.makeText(context, if (uri != null) "JPEG saved" else "Save failed", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                                override fun onError(exception: ImageCaptureException) {
                                    scope.launch { withContext(Dispatchers.Main) { Toast.makeText(context, "Fallback failed", Toast.LENGTH_SHORT).show() } }
                                }
                            })
                        }
                    }
                } catch (e: Throwable) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Capture error", Toast.LENGTH_SHORT).show()
                    }
                }

                // 3. 恢复 CameraX 预览
                val pv = previewViewRef
                if (pv != null) {
                    bindCameraPreview(pv)
                }

                isProcessing = false
                lastPhotoBitmap = loadLastPhotoThumbnail(context)
            }
        } else {
            // 普通 JPEG 模式
            imageCapture.takePicture(captureExecutor, object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    val jpegBytes = imageProxyToJpegBytes(image); image.close()
                    scope.launch {
                        isProcessing = true
                        capturedBytes = jpegBytes; processedBytes = jpegBytes
                        try { RustBridge.autoEnhance(jpegBytes).onSuccess { processedBytes = it } }
                        catch (_: Throwable) {}
                        isProcessing = false
                        lastPhotoBitmap = loadLastPhotoThumbnail(context)
                    }
                }
                override fun onError(exception: ImageCaptureException) {
                    scope.launch { Toast.makeText(context, "Capture failed", Toast.LENGTH_SHORT).show() }
                }
            })
        }
    }

    fun startRecording() {
        val ctx = context
        val videoDir = File(ctx.getExternalFilesDir(null), "videos").apply { mkdirs() }
        val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val videoFile = File(videoDir, "VID_$ts.mp4")
        val outputOptions = FileOutputOptions.Builder(videoFile).build()
        val videoLocation = if (enableLocation && hasLocationPermission) currentLocation else null

        activeRecording = videoCapture.output
            .prepareRecording(ctx, outputOptions)
            .apply { if (hasAudioPermission) withAudioEnabled() }
            .start(ContextCompat.getMainExecutor(ctx)) { event ->
                if (event is VideoRecordEvent.Finalize) {
                    if (event.hasError()) {
                        scope.launch { Toast.makeText(ctx, "Recording failed", Toast.LENGTH_SHORT).show() }
                    } else {
                        scope.launch {
                            PhotoSaver.saveVideoToGallery(ctx, videoFile, location = videoLocation)
                            Toast.makeText(ctx, "Video saved", Toast.LENGTH_SHORT).show()
                            lastPhotoBitmap = loadLastPhotoThumbnail(ctx)
                        }
                    }
                }
            }
        isRecording = true
    }

    fun stopRecording() { activeRecording?.stop(); activeRecording = null; isRecording = false }

    fun onTapToFocus(x: Float, y: Float) {
        if (isPreviewPaused) return
        focusPoint = Offset(x, y); showFocusRing = true
        val pv = previewViewRef ?: return; val ctrl = camera?.cameraControl ?: return
        val point = pv.meteringPointFactory.createPoint(x, y)
        val action = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE)
            .setAutoCancelDuration(3, TimeUnit.SECONDS).build()
        ctrl.startFocusAndMetering(action)
    }

    fun onZoom(delta: Float) {
        if (isPreviewPaused) return
        val ctrl = camera?.cameraControl ?: return
        zoomRatio = (zoomRatio * delta).coerceIn(1f, 10f); ctrl.setZoomRatio(zoomRatio)
    }

    fun setExposure(value: Float) {
        exposureComp = value
        val ctrl = camera?.cameraControl ?: return; val info = camera?.cameraInfo ?: return
        val range = info.exposureState.exposureCompensationRange
        val index = (value * (range.upper - range.lower) / 2 + (range.upper + range.lower) / 2).toInt().coerceIn(range.lower, range.upper)
        ctrl.setExposureCompensationIndex(index)
    }

    fun applyFilter(filter: FilterType) {
        val bytes = capturedBytes ?: return; selectedFilter = filter
        scope.launch {
            isProcessing = true
            if (filter == FilterType.NONE) { processedBytes = bytes }
            else { try { RustBridge.applyFilter(bytes, filter).onSuccess { processedBytes = it } } catch (_: Throwable) { processedBytes = bytes } }
            isProcessing = false
        }
    }

    fun retake() { capturedBytes = null; processedBytes = null; selectedFilter = FilterType.NONE }

    fun save() {
        val bytes = processedBytes ?: return
        scope.launch {
            val uri = PhotoSaver.saveJpegToGallery(context, bytes, location = currentLocation)
            if (uri != null) { Toast.makeText(context, "Saved", Toast.LENGTH_SHORT).show(); retake() }
            else Toast.makeText(context, "Failed", Toast.LENGTH_SHORT).show()
        }
    }

    fun toggleFlash() {
        flashMode = when (flashMode) {
            ImageCapture.FLASH_MODE_AUTO -> ImageCapture.FLASH_MODE_ON
            ImageCapture.FLASH_MODE_ON -> ImageCapture.FLASH_MODE_OFF
            else -> ImageCapture.FLASH_MODE_AUTO
        }
        imageCapture.flashMode = flashMode
    }

    fun switchCamera() { isFrontCamera = !isFrontCamera; backCameraIndex = 0; zoomRatio = 1f; exposureComp = 0f }

    fun cycleLens() {
        if (backCameraCount <= 1) return
        backCameraIndex = (backCameraIndex + 1) % backCameraCount
        zoomRatio = 1f
        // 重新绑定预览
        val pv = previewViewRef ?: return
        bindCameraPreview(pv)
    }

    fun setZoomPreset(target: Float) {
        val cam = camera ?: return
        zoomRatio = target.coerceIn(cam.cameraInfo.zoomState.value?.minZoomRatio ?: 1f,
            cam.cameraInfo.zoomState.value?.maxZoomRatio ?: 10f)
        cam.cameraControl.setZoomRatio(zoomRatio)
    }

    fun openGallery() {
        try { context.startActivity(Intent(Intent.ACTION_VIEW, MediaStore.Images.Media.EXTERNAL_CONTENT_URI).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        catch (_: Exception) { Toast.makeText(context, "No gallery app", Toast.LENGTH_SHORT).show() }
    }

    fun toggleLocation() {
        if (!hasLocationPermission) { permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)) }
        else { enableLocation = !enableLocation }
    }

    // ── UI ──

    Box(Modifier.fillMaxSize().background(Bg)) {
        if (!hasCameraPermission) {
            PermissionScreen { permissionLauncher.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)) }
            return@Box
        }

        if (processedBytes != null) {
            ReviewScreen(processedBytes, isProcessing, selectedFilter,
                onSelectFilter = { applyFilter(it) }, onRetake = { retake() }, onSave = { save() })
        } else {
            ViewfinderScreen(
                imageCapture, videoCapture, isFrontCamera, zoomRatio, showGrid,
                showFocusRing, focusPoint, showFlash, flashMode,
                selectedMode, lastPhotoBitmap,
                isRecording, recordingDuration,
                showExposureSlider, exposureComp,
                enableLocation, enableRaw, isRawSupported, isPreviewPaused,
                currentLocation, isLocationLoading,
                onCameraReady = { c, pv ->
                    camera = c; previewViewRef = pv
                    cameraProviderRef = ProcessCameraProvider.getInstance(context).get()
                    // 统计后置相机数量
                    val backCams = RawCaptureEngine.listCameras(context).filter {
                        it.facing == android.hardware.camera2.CameraCharacteristics.LENS_FACING_BACK
                    }
                    backCameraCount = backCams.size.coerceAtLeast(1)
                    val camId = if (isFrontCamera) RawCaptureEngine.findCameraId(context, true)
                    else backCams.getOrNull(backCameraIndex)?.id ?: backCams.firstOrNull()?.id
                    currentCameraId = camId
                    if (camId != null) {
                        isRawSupported = RawCaptureEngine.isRawSupported(context, camId)
                        sensorOrientation = RawCaptureEngine.getSensorOrientation(context, camId)
                    }
                },
                onTap = { x, y -> onTapToFocus(x, y) },
                onZoom = { onZoom(it) },
                onShutter = { when (selectedMode) { 0 -> { if (isRecording) stopRecording() else startRecording() }; else -> takePhoto() } },
                onFlashToggle = { toggleFlash() },
                onSwitchCamera = { switchCamera() },
                onModeChange = { if (isRecording) stopRecording(); selectedMode = it },
                onGridToggle = { showGrid = !showGrid },
                onGalleryClick = { openGallery() },
                onExposureToggle = { showExposureSlider = !showExposureSlider },
                onExposureChange = { setExposure(it) },
                onLocationToggle = { toggleLocation() },
                onRawToggle = { enableRaw = !enableRaw },
                onZoomPreset = { setZoomPreset(it) },
                onCycleLens = { cycleLens() },
                backCameraCount = backCameraCount
            )
        }
    }
}

// ── Permission ───────────────────────────────────────────────────────

@Composable
private fun PermissionScreen(onRequest: () -> Unit) {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text("Camera access required", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp)); Text("Grant permission to start taking photos", color = TextMuted, fontSize = 13.sp)
        Spacer(Modifier.height(16.dp))
        Button(onRequest, colors = ButtonDefaults.buttonColors(containerColor = TextPrimary), shape = RoundedCornerShape(20.dp),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 10.dp)) { Text("Allow", color = Bg, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
    }
}

// ── Viewfinder ───────────────────────────────────────────────────────

@Composable
private fun ViewfinderScreen(
    imageCapture: ImageCapture, videoCapture: VideoCapture<Recorder>,
    isFrontCamera: Boolean, zoomRatio: Float, showGrid: Boolean,
    showFocusRing: Boolean, focusPoint: Offset?, showFlash: Boolean, flashMode: Int,
    selectedMode: Int, lastPhotoBitmap: Bitmap?,
    isRecording: Boolean, recordingDuration: Int,
    showExposureSlider: Boolean, exposureComp: Float,
    enableLocation: Boolean, enableRaw: Boolean, isRawSupported: Boolean, isPreviewPaused: Boolean,
    currentLocation: Location?, isLocationLoading: Boolean,
    onCameraReady: (Camera, PreviewView) -> Unit,
    onTap: (Float, Float) -> Unit, onZoom: (Float) -> Unit, onShutter: () -> Unit,
    onFlashToggle: () -> Unit, onSwitchCamera: () -> Unit, onModeChange: (Int) -> Unit,
    onGridToggle: () -> Unit, onGalleryClick: () -> Unit,
    onExposureToggle: () -> Unit, onExposureChange: (Float) -> Unit,
    onLocationToggle: () -> Unit, onRawToggle: () -> Unit, onZoomPreset: (Float) -> Unit,
    onCycleLens: () -> Unit, backCameraCount: Int = 1
) {
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    Box(Modifier.fillMaxSize()) {
        // 预览 + 双指缩放 + 水平滑动变焦
        Box(Modifier.fillMaxSize()
            .pointerInput(Unit) { detectTransformGestures { _, _, zoom, _ -> if (zoom != 1f) onZoom(zoom) } }
            .pointerInput(Unit) {
                detectHorizontalDragGestures { change, dragAmount ->
                    change.consume()
                    // 水平滑动调节变焦: 向右滑=放大, 向左滑=缩小
                    val delta = dragAmount / 800f
                    onZoom(1f + delta)
                }
            }
        ) {
            CameraPreviewWithFocus(imageCapture, videoCapture, isFrontCamera, showGrid, showFocusRing, focusPoint,
                onTap, onCameraReady, Modifier.fillMaxWidth().aspectRatio(3f / 4f).align(Alignment.Center))
        }

        // RAW 捕获时的黑色遮罩
        if (isPreviewPaused) {
            Box(Modifier.fillMaxSize().background(Bg.copy(alpha = 0.8f)))
            CircularProgressIndicator(Modifier.align(Alignment.Center), color = TextPrimary)
        }

        if (showFlash) Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.7f)))

        if (isRecording) RecordingIndicator(recordingDuration, Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 12.dp))

        if (!isLandscape) {
            // ══════════════════════════════════════════════════
            // 竖屏布局
            // ══════════════════════════════════════════════════

            // 顶部栏
            TopBar(flashMode, showGrid, enableLocation, enableRaw, isRawSupported,
                onFlashToggle, onGridToggle, onExposureToggle, onLocationToggle, onRawToggle,
                Modifier.align(Alignment.TopStart).statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp))

            // 变焦倍率
            if (zoomRatio > 1.05f) {
                Text("${String.format("%.1f", zoomRatio)}x", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 64.dp)
                        .background(Surface2.copy(alpha = 0.6f), RoundedCornerShape(10.dp)).padding(horizontal = 10.dp, vertical = 4.dp))
            }

            // GPS / RAW 标签
            StatusBadges(Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(top = 56.dp, end = 16.dp),
                enableLocation, currentLocation, isLocationLoading, enableRaw, isRawSupported)

            // 曝光滑块 (垂直)
            if (showExposureSlider) {
                ExposureSlider(exposureComp, onExposureChange, isLandscape = false,
                    Modifier.align(Alignment.CenterEnd).padding(end = 12.dp))
            }

            // 底部区域: 变焦预设 + 模式 + 快门
            Column(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                // 变焦预设
                ZoomPresets(zoomRatio, onZoomPreset)
                Spacer(Modifier.height(12.dp))
                // 模式选择 + 快门
                BottomControls(selectedMode, lastPhotoBitmap, isRecording,
                    onModeChange, onShutter, onSwitchCamera, onGalleryClick,
                    onCycleLens = onCycleLens, backCameraCount = backCameraCount)
            }

        } else {
            // ══════════════════════════════════════════════════
            // 横屏布局: 右侧垂直控制栏
            // ══════════════════════════════════════════════════

            // 顶部栏
            TopBar(flashMode, showGrid, enableLocation, enableRaw, isRawSupported,
                onFlashToggle, onGridToggle, onExposureToggle, onLocationToggle, onRawToggle,
                Modifier.align(Alignment.TopStart).statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp))

            // GPS / RAW 标签 (左下)
            StatusBadges(Modifier.align(Alignment.BottomStart).navigationBarsPadding().padding(start = 12.dp, bottom = 12.dp),
                enableLocation, currentLocation, isLocationLoading, enableRaw, isRawSupported)

            // 曝光滑块 (水平，底部)
            if (showExposureSlider) {
                ExposureSlider(exposureComp, onExposureChange, isLandscape = true,
                    Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 12.dp))
            }

            // 右侧控制栏
            Column(Modifier.align(Alignment.CenterEnd).navigationBarsPadding().padding(end = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // 变焦预设 (竖排)
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(0.5f to ".5", 1f to "1", 2f to "2", 5f to "5").forEach { (zoom, label) ->
                        val isActive = abs(zoomRatio - zoom) < 0.15f
                        Box(Modifier.size(34.dp)
                            .background(if (isActive) TextPrimary else Surface2.copy(alpha = 0.7f), CircleShape)
                            .clickable(remember { MutableInteractionSource() }, null) { onZoomPreset(zoom) },
                            contentAlignment = Alignment.Center) {
                            Text(label, color = if (isActive) Bg else TextMuted, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
                // 分隔线
                Box(Modifier.width(24.dp).height(1.dp).background(AccentDim.copy(alpha = 0.3f)))
                // 模式选择 (竖排)
                listOf("VID" to 0, "PHT" to 1, "PRT" to 2).forEach { (label, mode) ->
                    Text(label, color = if (selectedMode == mode) TextPrimary else TextMuted,
                        fontSize = 10.sp, fontWeight = if (selectedMode == mode) FontWeight.Bold else FontWeight.Medium,
                        modifier = Modifier.clickable(remember { MutableInteractionSource() }, null) { onModeChange(mode) })
                }
                // 分隔线
                Box(Modifier.width(24.dp).height(1.dp).background(AccentDim.copy(alpha = 0.3f)))
                // 相册
                Box(Modifier.size(38.dp).background(Surface2, RoundedCornerShape(8.dp))
                    .clickable(remember { MutableInteractionSource() }, null) { onGalleryClick() }, contentAlignment = Alignment.Center) {
                    if (lastPhotoBitmap != null) Image(lastPhotoBitmap.asImageBitmap(), null, Modifier.size(38.dp).clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop)
                    else Icon(Icons.Default.GridOn, null, tint = TextMuted, modifier = Modifier.size(18.dp))
                }
                // 快门
                if (selectedMode == 0) {
                    Box(Modifier.size(64.dp).border(3.dp, if (isRecording) Danger else TextPrimary, CircleShape)
                        .clickable(remember { MutableInteractionSource() }, null) { onShutter() }, contentAlignment = Alignment.Center) {
                        if (isRecording) Box(Modifier.size(24.dp).background(Danger, RoundedCornerShape(5.dp)))
                        else Box(Modifier.size(52.dp).background(Danger, CircleShape))
                    }
                } else {
                    Box(Modifier.size(64.dp).border(3.dp, TextPrimary, CircleShape)
                        .clickable(remember { MutableInteractionSource() }, null) { onShutter() }, contentAlignment = Alignment.Center) {
                        Box(Modifier.size(52.dp).background(TextPrimary, CircleShape))
                    }
                }
                // 切换摄像头 + 镜头
                if (backCameraCount > 1) {
                    CircleIconButton(Icons.Default.Landscape, AccentGreen, size = 34, onClick = onCycleLens)
                }
                CircleIconButton(Icons.Default.Cameraswitch, TextPrimary, size = 38, onClick = onSwitchCamera)
            }
        }
    }
}

// ── 提取的子组件 ────────────────────────────────────────────────────

@Composable
private fun StatusBadges(modifier: Modifier, enableLocation: Boolean, currentLocation: Location?,
    isLocationLoading: Boolean, enableRaw: Boolean, isRawSupported: Boolean) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (enableLocation) {
            Row(Modifier.background(Surface2.copy(alpha = 0.6f), RoundedCornerShape(10.dp)).padding(horizontal = 8.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(Icons.Default.LocationOn, null, tint = if (currentLocation != null) AccentGreen else TextMuted, modifier = Modifier.size(12.dp))
                Text(
                    if (isLocationLoading) "定位中..." else if (currentLocation != null) "GPS ✓" else "GPS",
                    color = if (currentLocation != null) AccentGreen else TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Medium)
            }
        }
        if (enableRaw && isRawSupported) {
            Row(Modifier.background(Surface2.copy(alpha = 0.6f), RoundedCornerShape(10.dp)).padding(horizontal = 8.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(Icons.Default.PhotoCamera, null, tint = TextMuted, modifier = Modifier.size(12.dp))
                Text("RAW", color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun ZoomPresets(zoomRatio: Float, onZoomPreset: (Float) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        listOf(0.5f to ".5", 1f to "1", 2f to "2", 5f to "5").forEach { (zoom, label) ->
            val isActive = abs(zoomRatio - zoom) < 0.15f
            Box(Modifier.size(36.dp)
                .background(if (isActive) TextPrimary else Surface2.copy(alpha = 0.7f), CircleShape)
                .clickable(remember { MutableInteractionSource() }, null) { onZoomPreset(zoom) },
                contentAlignment = Alignment.Center) {
                Text(label, color = if (isActive) Bg else TextMuted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

// ── Camera Preview ───────────────────────────────────────────────────

@Composable
private fun CameraPreviewWithFocus(
    imageCapture: ImageCapture, videoCapture: VideoCapture<Recorder>,
    isFrontCamera: Boolean, showGrid: Boolean, showFocusRing: Boolean, focusPoint: Offset?,
    onTap: (Float, Float) -> Unit, onCameraReady: (Camera, PreviewView) -> Unit, modifier: Modifier = Modifier
) {
    val context = LocalContext.current; val lifecycleOwner = LocalLifecycleOwner.current
    Box(modifier.clip(RoundedCornerShape(4.dp))) {
        AndroidView(
            factory = { ctx -> PreviewView(ctx).apply {
                scaleType = PreviewView.ScaleType.FIT_CENTER; implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                setOnTouchListener { _, event -> if (event.action == MotionEvent.ACTION_UP) onTap(event.x, event.y); true }
            } },
            update = { pv ->
                val future = ProcessCameraProvider.getInstance(context)
                future.addListener({
                    val provider = future.get()
                    val preview = Preview.Builder().setTargetAspectRatio(AspectRatio.RATIO_4_3).build().also { it.surfaceProvider = pv.surfaceProvider }
                    val selector = if (isFrontCamera) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
                    try {
                        provider.unbindAll()
                        val useCaseGroup = UseCaseGroup.Builder().addUseCase(preview).addUseCase(imageCapture).addUseCase(videoCapture).setViewPort(pv.viewPort!!).build()
                        val cam = provider.bindToLifecycle(lifecycleOwner, selector, useCaseGroup)
                        onCameraReady(cam, pv)
                    } catch (_: Exception) {}
                }, ContextCompat.getMainExecutor(context))
            }, modifier = Modifier.fillMaxSize()
        )
        if (showGrid) GridOverlay(Modifier.fillMaxSize())
        if (showFocusRing && focusPoint != null) FocusRing(focusPoint)
    }
}

// ── UI Components ────────────────────────────────────────────────────

@Composable private fun GridOverlay(modifier: Modifier) {
    Canvas(modifier) { val w = size.width; val h = size.height
        drawLine(GridColor, Offset(w/3, 0f), Offset(w/3, h), strokeWidth = 1f)
        drawLine(GridColor, Offset(w*2/3, 0f), Offset(w*2/3, h), strokeWidth = 1f)
        drawLine(GridColor, Offset(0f, h/3), Offset(w, h/3), strokeWidth = 1f)
        drawLine(GridColor, Offset(0f, h*2/3), Offset(w, h*2/3), strokeWidth = 1f)
    }
}

@Composable private fun FocusRing(point: Offset) {
    val alpha by animateFloatAsState(targetValue = 0f, animationSpec = tween(1200), label = "focus")
    Canvas(Modifier.fillMaxSize()) { drawCircle(color = TextPrimary.copy(alpha = 0.8f), radius = 40f, center = point, style = Stroke(2f)) }
}

@Composable private fun ExposureSlider(value: Float, onChange: (Float) -> Unit, isLandscape: Boolean, modifier: Modifier) {
    // 竖屏: 右侧垂直滑块 (上=增亮, 下=减暗); 横屏: 底部水平滑块 (右=增亮, 左=减暗)
    if (isLandscape) {
        Row(modifier.height(40.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Default.Brightness6, null, tint = TextMuted, modifier = Modifier.size(16.dp))
            Slider(value = value, onValueChange = onChange, valueRange = -1f..1f, modifier = Modifier.width(280.dp),
                colors = SliderDefaults.colors(thumbColor = TextPrimary, activeTrackColor = TextPrimary, inactiveTrackColor = AccentDim))
            Text("%.1f".format(value), color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Medium, modifier = Modifier.width(30.dp))
        }
    } else {
        // 竖直滑块: 旋转 -90° 实现垂直交互 (上=增亮, 下=减暗)
        Column(modifier.height(250.dp).width(44.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("%.1f".format(value), color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(6.dp))
            Slider(value = value, onValueChange = onChange, valueRange = -1f..1f,
                modifier = Modifier.width(250.dp).graphicsLayer { rotationZ = -90f },
                colors = SliderDefaults.colors(thumbColor = TextPrimary, activeTrackColor = TextPrimary, inactiveTrackColor = AccentDim))
            Spacer(Modifier.height(6.dp))
            Icon(Icons.Default.Brightness6, null, tint = TextMuted, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable private fun RecordingIndicator(duration: Int, modifier: Modifier) {
    Row(modifier.background(Surface.copy(alpha = 0.8f), RoundedCornerShape(14.dp)).padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(8.dp).background(Danger, CircleShape))
        Text("%d:%02d".format(duration / 60, duration % 60), color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable private fun TopBar(flashMode: Int, showGrid: Boolean, enableLocation: Boolean, enableRaw: Boolean, isRawSupported: Boolean,
    onFlashToggle: () -> Unit, onGridToggle: () -> Unit, onExposureToggle: () -> Unit, onLocationToggle: () -> Unit, onRawToggle: () -> Unit, modifier: Modifier) {
    val flashIcon = when (flashMode) { ImageCapture.FLASH_MODE_ON -> Icons.Default.FlashOn; ImageCapture.FLASH_MODE_OFF -> Icons.Default.FlashOff; else -> Icons.Default.FlashAuto }
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        CircleIconButton(flashIcon, if (flashMode == ImageCapture.FLASH_MODE_OFF) AccentDim else TextMuted, onClick = onFlashToggle)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            CircleIconButton(Icons.Default.GridOn, if (showGrid) TextPrimary else TextMuted, onClick = onGridToggle)
            CircleIconButton(Icons.Default.Brightness6, TextMuted, onClick = onExposureToggle)
            CircleIconButton(Icons.Default.LocationOn, if (enableLocation) TextPrimary else TextMuted, onClick = onLocationToggle)
            if (isRawSupported) {
                Box(Modifier.size(36.dp).background(Surface2.copy(alpha = 0.6f), CircleShape)
                    .clickable(remember { MutableInteractionSource() }, null) { onRawToggle() }, contentAlignment = Alignment.Center) {
                    Text("R", color = if (enableRaw) TextPrimary else TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable private fun BottomControls(selectedMode: Int, lastPhotoBitmap: Bitmap?, isRecording: Boolean,
    onModeChange: (Int) -> Unit, onShutter: () -> Unit, onSwitchCamera: () -> Unit, onGalleryClick: () -> Unit,
    onCycleLens: (() -> Unit)? = null, backCameraCount: Int = 1, modifier: Modifier = Modifier) {
    val modes = listOf("VIDEO", "PHOTO", "PORTRAIT")
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        // 模式选择器
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
            modes.forEachIndexed { i, label ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(label, color = if (i == selectedMode) TextPrimary else TextMuted,
                        fontSize = 12.sp, fontWeight = if (i == selectedMode) FontWeight.Bold else FontWeight.Medium,
                        letterSpacing = 0.5.sp,
                        modifier = Modifier.clickable(remember { MutableInteractionSource() }, null) { onModeChange(i) })
                    if (i == selectedMode) {
                        Spacer(Modifier.height(3.dp))
                        Box(Modifier.width(20.dp).height(2.dp).background(TextPrimary, RoundedCornerShape(1.dp)))
                    } else Spacer(Modifier.height(5.dp))
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        // 底部: 相册 / 快门 / 切换
        Row(Modifier.fillMaxWidth().padding(horizontal = 32.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            // 相册缩略图
            Box(Modifier.size(42.dp).background(Surface2, RoundedCornerShape(10.dp))
                .clickable(remember { MutableInteractionSource() }, null) { onGalleryClick() }, contentAlignment = Alignment.Center) {
                if (lastPhotoBitmap != null) Image(lastPhotoBitmap.asImageBitmap(), null, Modifier.size(42.dp).clip(RoundedCornerShape(10.dp)), contentScale = ContentScale.Crop)
                else Icon(Icons.Default.GridOn, null, tint = TextMuted, modifier = Modifier.size(20.dp))
            }
            // 快门按钮
            if (selectedMode == 0) {
                // 录像: 红色圆点/方块
                Box(Modifier.size(72.dp).border(3.dp, if (isRecording) Danger else TextPrimary, CircleShape)
                    .clickable(remember { MutableInteractionSource() }, null) { onShutter() }, contentAlignment = Alignment.Center) {
                    if (isRecording) Box(Modifier.size(28.dp).background(Danger, RoundedCornerShape(6.dp)))
                    else Box(Modifier.size(60.dp).background(Danger, CircleShape))
                }
            } else {
                // 拍照: 白色圆环 + 内圆
                Box(Modifier.size(72.dp).border(3.dp, TextPrimary, CircleShape)
                    .clickable(remember { MutableInteractionSource() }, null) { onShutter() }, contentAlignment = Alignment.Center) {
                    Box(Modifier.size(60.dp).background(TextPrimary, CircleShape))
                }
            }
            // 前后摄 + 镜头切换
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (backCameraCount > 1 && onCycleLens != null) {
                    CircleIconButton(Icons.Default.Landscape, AccentGreen, size = 38, onClick = onCycleLens)
                }
                CircleIconButton(Icons.Default.Cameraswitch, TextPrimary, size = 42, onClick = onSwitchCamera)
            }
        }
    }
}

@Composable private fun ReviewScreen(processedBytes: ByteArray?, isProcessing: Boolean, selectedFilter: FilterType,
    onSelectFilter: (FilterType) -> Unit, onRetake: () -> Unit, onSave: () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        val bitmap = remember(processedBytes) { processedBytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) } }
        bitmap?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit) }
        if (isProcessing) ProcessingBadge(Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 12.dp))
        ReviewControls(selectedFilter, onSelectFilter, onRetake, onSave, Modifier.align(Alignment.BottomStart).navigationBarsPadding().padding(bottom = 24.dp))
    }
}

@Composable private fun CircleIconButton(icon: ImageVector, tint: Color, size: Int = 36, onClick: () -> Unit) {
    Box(Modifier.size(size.dp).background(Surface2.copy(alpha = 0.6f), CircleShape).clickable(remember { MutableInteractionSource() }, null) { onClick() }, contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = tint, modifier = Modifier.size((size * 0.5).dp))
    }
}

@Composable private fun ProcessingBadge(modifier: Modifier) {
    Row(modifier.background(Surface.copy(alpha = 0.7f), RoundedCornerShape(14.dp)).padding(horizontal = 14.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        CircularProgressIndicator(Modifier.size(12.dp), color = TextMuted, strokeWidth = 1.5.dp)
        Text("Processing", color = TextMuted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable private fun ReviewControls(selectedFilter: FilterType, onSelectFilter: (FilterType) -> Unit, onRetake: () -> Unit, onSave: () -> Unit, modifier: Modifier) {
    Column(modifier.fillMaxWidth()) {
        LazyRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(horizontal = 20.dp)) {
            items(RustBridge.supportedFilters()) { filter -> val active = selectedFilter == filter
                Text(RustBridge.filterName(filter), color = if (active) Bg else TextMuted, fontSize = 12.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.2.sp,
                    modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(if (active) TextPrimary else Color.Transparent)
                        .then(if (!active) Modifier.border(1.5.dp, AccentDim, RoundedCornerShape(14.dp)) else Modifier)
                        .clickable(remember { MutableInteractionSource() }, null) { onSelectFilter(filter) }.padding(horizontal = 14.dp, vertical = 6.dp))
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            TextButton(onRetake, colors = ButtonDefaults.textButtonColors(contentColor = TextPrimary), shape = RoundedCornerShape(20.dp), contentPadding = PaddingValues(horizontal = 24.dp, vertical = 10.dp)) { Text("Retake", fontSize = 14.sp, fontWeight = FontWeight.Medium) }
            Button(onSave, colors = ButtonDefaults.buttonColors(containerColor = TextPrimary), shape = RoundedCornerShape(20.dp), contentPadding = PaddingValues(horizontal = 28.dp, vertical = 10.dp)) { Text("Save", color = Bg, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
        }
    }
}

// ── ImageProxy → JPEG ────────────────────────────────────────────────

private fun imageProxyToJpegBytes(image: ImageProxy): ByteArray {
    val rotation = image.imageInfo.rotationDegrees
    val jpegBytes = if (image.format == ImageFormat.JPEG) {
        val buf = image.planes[0].buffer; ByteArray(buf.remaining()).also { buf.get(it) }
    } else {
        val y = image.planes[0].buffer; val u = image.planes[1].buffer; val v = image.planes[2].buffer
        val ySize = y.remaining(); val uvSize = u.remaining().coerceAtMost(v.remaining())
        val nv21 = ByteArray(ySize + uvSize * 2)
        y.get(nv21, 0, ySize)
        for (i in 0 until uvSize) { nv21[ySize + i * 2] = v.get(i); nv21[ySize + i * 2 + 1] = u.get(i) }
        val out = ByteArrayOutputStream()
        YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null).compressToJpeg(Rect(0, 0, image.width, image.height), 95, out)
        out.toByteArray()
    }
    return if (rotation != 0) rotateJpeg(jpegBytes, rotation) else jpegBytes
}

private fun rotateJpeg(bytes: ByteArray, degrees: Int): ByteArray {
    val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return bytes
    val rotated = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(degrees.toFloat()) }, true)
    bmp.recycle(); val out = ByteArrayOutputStream(); rotated.compress(Bitmap.CompressFormat.JPEG, 95, out); rotated.recycle(); return out.toByteArray()
}

private fun loadLastPhotoThumbnail(ctx: Context): Bitmap? {
    val uri = ctx.contentResolver.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, arrayOf(MediaStore.Images.Media._ID),
        "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?", arrayOf("%Pictures/CameraApp%"), "${MediaStore.Images.Media.DATE_ADDED} DESC"
    )?.use { c -> if (c.moveToFirst()) { val id = c.getLong(c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)); ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id) } else null }
    return uri?.let { try { ctx.contentResolver.loadThumbnail(it, android.util.Size(128, 128), null) } catch (_: Exception) { null } }
}