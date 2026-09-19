package com.camera.app.ui

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.exifinterface.media.ExifInterface
import com.camera.app.bridge.PhotoSaver
import com.camera.app.bridge.RustBridge
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import uniffi.camera_shared_core.FilterType
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

// ── Tokens ───────────────────────────────────────────────────────────

private val Bg = Color(0xFF000000)
private val Surface = Color(0xFF1A1A1A)
private val Surface2 = Color(0xFF252525)
private val TextPrimary = Color(0xFFFFFFFF)
private val TextMuted = Color(0xFF8E8E93)
private val AccentDim = Color(0xFF636366)
private val GridColor = Color(0x33FFFFFF)
private val Danger = Color(0xFFFF453A)

// ── Main ─────────────────────────────────────────────────────────────

@Composable
fun CameraScreen() {
    val context = LocalContext.current
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
    var flashMode by remember { mutableIntStateOf(ImageCapture.FLASH_MODE_AUTO) }
    var selectedMode by remember { mutableIntStateOf(1) }
    var zoomRatio by remember { mutableFloatStateOf(1f) }
    var showGrid by remember { mutableStateOf(false) }
    var exposureComp by remember { mutableFloatStateOf(0f) }
    var showExposureSlider by remember { mutableStateOf(false) }

    // GPS
    var enableLocation by remember { mutableStateOf(false) }
    var currentLocation by remember { mutableStateOf<Location?>(null) }

    // RAW
    var enableRaw by remember { mutableStateOf(false) }

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

    LaunchedEffect(Unit) {
        val needed = mutableListOf<String>()
        if (!hasCameraPermission) needed.add(Manifest.permission.CAMERA)
        if (!hasAudioPermission) needed.add(Manifest.permission.RECORD_AUDIO)
        if (needed.isNotEmpty()) permissionLauncher.launch(needed.toTypedArray())
        lastPhotoBitmap = loadLastPhotoThumbnail(context)
    }

    // 获取当前位置
    LaunchedEffect(enableLocation, hasLocationPermission) {
        if (enableLocation && hasLocationPermission) {
            try {
                val cts = CancellationTokenSource()
                currentLocation = suspendCancellableCoroutine { cont ->
                    fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token)
                        .addOnSuccessListener { loc -> cont.resume(loc) }
                        .addOnFailureListener { cont.resume(null) }
                }
            } catch (_: SecurityException) { currentLocation = null }
        } else {
            currentLocation = null
        }
    }

    // Focus ring disappear
    LaunchedEffect(showFocusRing) {
        if (showFocusRing) { delay(1200); showFocusRing = false }
    }

    // Shutter flash
    LaunchedEffect(showFlash) {
        if (showFlash) { delay(120); showFlash = false }
    }

    // Recording timer
    LaunchedEffect(isRecording) {
        if (isRecording) {
            recordingDuration = 0
            while (isRecording) { delay(1000); recordingDuration++ }
        }
    }

    // ── Actions ──

    fun takePhoto() {
        showFlash = true
        try { vibrator?.vibrate(VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE)) } catch (_: Exception) {}

        // 刷新位置
        if (enableLocation && hasLocationPermission) {
            try {
                val cts = CancellationTokenSource()
                fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token)
                    .addOnSuccessListener { loc -> currentLocation = loc }
            } catch (_: SecurityException) {}
        }

        imageCapture.takePicture(captureExecutor, object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                val jpegBytes = imageProxyToJpegBytes(image)
                image.close()

                scope.launch {
                    isProcessing = true
                    capturedBytes = jpegBytes
                    processedBytes = jpegBytes

                    // 安全调用 Rust，失败则用原图
                    try {
                        RustBridge.autoEnhance(jpegBytes).onSuccess { processedBytes = it }
                    } catch (e: Throwable) {
                        // Rust 处理失败，保持原图
                        processedBytes = jpegBytes
                    }
                    isProcessing = false
                    lastPhotoBitmap = loadLastPhotoThumbnail(context)
                }
            }

            override fun onError(exception: ImageCaptureException) {
                scope.launch {
                    Toast.makeText(context, "Capture failed: ${exception.message}", Toast.LENGTH_SHORT).show()
                }
            }
        })
    }

    fun startRecording() {
        val ctx = context
        val videoDir = File(ctx.getExternalFilesDir(null), "videos").apply { mkdirs() }
        val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val videoFile = File(videoDir, "VID_$ts.mp4")

        val outputOptions = FileOutputOptions.Builder(videoFile).build()

        // 获取当前 GPS 位置用于视频元数据
        val videoLocation = if (enableLocation && hasLocationPermission) currentLocation else null

        var recording = videoCapture.output
            .prepareRecording(ctx, outputOptions)
            .apply { if (hasAudioPermission) withAudioEnabled() }

        // 注: CameraX VideoCapture 目前不支持直接写入 GPS 元数据
        // GPS 信息会在保存时通过 MediaStore 写入视频文件的元数据
        recording = recording.start(ContextCompat.getMainExecutor(ctx)) { event ->
            if (event is VideoRecordEvent.Finalize) {
                if (event.hasError()) {
                    scope.launch { Toast.makeText(ctx, "Recording failed", Toast.LENGTH_SHORT).show() }
                } else {
                    scope.launch {
                        val uri = PhotoSaver.saveVideoToGallery(ctx, videoFile, location = videoLocation)
                        if (uri != null) Toast.makeText(ctx, "Video saved", Toast.LENGTH_SHORT).show()
                        lastPhotoBitmap = loadLastPhotoThumbnail(ctx)
                    }
                }
            }
        }

        activeRecording = recording
        isRecording = true
    }

    fun stopRecording() {
        activeRecording?.stop(); activeRecording = null; isRecording = false
    }

    fun onTapToFocus(x: Float, y: Float) {
        focusPoint = Offset(x, y); showFocusRing = true
        val pv = previewViewRef ?: return
        val ctrl = camera?.cameraControl ?: return
        val point = pv.meteringPointFactory.createPoint(x, y)
        val action = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE)
            .setAutoCancelDuration(3, TimeUnit.SECONDS).build()
        ctrl.startFocusAndMetering(action)
    }

    fun onZoom(delta: Float) {
        val ctrl = camera?.cameraControl ?: return
        zoomRatio = (zoomRatio * delta).coerceIn(1f, 10f)
        ctrl.setZoomRatio(zoomRatio)
    }

    fun setExposure(value: Float) {
        exposureComp = value
        val ctrl = camera?.cameraControl ?: return
        val info = camera?.cameraInfo ?: return
        val range = info.exposureState.exposureCompensationRange
        val index = (value * (range.upper - range.lower) / 2 + (range.upper + range.lower) / 2).toInt()
            .coerceIn(range.lower, range.upper)
        ctrl.setExposureCompensationIndex(index)
    }

    fun applyFilter(filter: FilterType) {
        val bytes = capturedBytes ?: return
        selectedFilter = filter
        scope.launch {
            isProcessing = true
            if (filter == FilterType.NONE) {
                processedBytes = bytes
            } else {
                try {
                    RustBridge.applyFilter(bytes, filter).onSuccess { processedBytes = it }
                } catch (_: Throwable) {
                    processedBytes = bytes
                }
            }
            isProcessing = false
        }
    }

    fun retake() {
        capturedBytes = null; processedBytes = null; selectedFilter = FilterType.NONE
    }

    fun save() {
        val bytes = processedBytes ?: return
        scope.launch {
            val uri = PhotoSaver.saveJpegToGallery(context, bytes, location = currentLocation)
            if (uri == null) {
                Toast.makeText(context, "Failed", Toast.LENGTH_SHORT).show()
                return@launch
            }
            Toast.makeText(context, "Saved", Toast.LENGTH_SHORT).show()
            retake()
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

    fun switchCamera() {
        isFrontCamera = !isFrontCamera; zoomRatio = 1f; exposureComp = 0f
    }

    fun openGallery() {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, MediaStore.Images.Media.EXTERNAL_CONTENT_URI).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) { Toast.makeText(context, "No gallery app", Toast.LENGTH_SHORT).show() }
    }

    fun toggleLocation() {
        if (!hasLocationPermission) {
            permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION))
        } else {
            enableLocation = !enableLocation
        }
    }

    // ── UI ──

    Box(Modifier.fillMaxSize().background(Bg)) {
        if (!hasCameraPermission) {
            PermissionScreen { permissionLauncher.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)) }
            return@Box
        }

        if (processedBytes != null) {
            ReviewScreen(processedBytes, isProcessing, selectedFilter,
                onSelectFilter = { applyFilter(it) },
                onRetake = { retake() },
                onSave = { save() })
        } else {
            ViewfinderScreen(
                imageCapture, videoCapture, isFrontCamera, zoomRatio, showGrid,
                showFocusRing, focusPoint, showFlash, flashMode,
                selectedMode, lastPhotoBitmap,
                isRecording, recordingDuration,
                showExposureSlider, exposureComp,
                enableLocation, enableRaw,
                onCameraReady = { c, pv -> camera = c; previewViewRef = pv },
                onTap = { x, y -> onTapToFocus(x, y) },
                onZoom = { onZoom(it) },
                onShutter = {
                    when (selectedMode) {
                        0 -> { if (isRecording) stopRecording() else startRecording() }
                        else -> takePhoto()
                    }
                },
                onFlashToggle = { toggleFlash() },
                onSwitchCamera = { switchCamera() },
                onModeChange = { if (isRecording) stopRecording(); selectedMode = it },
                onGridToggle = { showGrid = !showGrid },
                onGalleryClick = { openGallery() },
                onExposureToggle = { showExposureSlider = !showExposureSlider },
                onExposureChange = { setExposure(it) },
                onLocationToggle = { toggleLocation() },
                onRawToggle = { enableRaw = !enableRaw }
            )
        }
    }
}

// ── Permission ───────────────────────────────────────────────────────

@Composable
private fun PermissionScreen(onRequest: () -> Unit) {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text("Camera access required", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text("Grant permission to start taking photos", color = TextMuted, fontSize = 13.sp)
        Spacer(Modifier.height(16.dp))
        Button(onRequest, colors = ButtonDefaults.buttonColors(containerColor = TextPrimary),
            shape = RoundedCornerShape(20.dp), contentPadding = PaddingValues(horizontal = 24.dp, vertical = 10.dp)) {
            Text("Allow", color = Bg, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

// ── Viewfinder ───────────────────────────────────────────────────────

@Composable
private fun ViewfinderScreen(
    imageCapture: ImageCapture,
    videoCapture: VideoCapture<Recorder>,
    isFrontCamera: Boolean,
    zoomRatio: Float,
    showGrid: Boolean,
    showFocusRing: Boolean,
    focusPoint: Offset?,
    showFlash: Boolean,
    flashMode: Int,
    selectedMode: Int,
    lastPhotoBitmap: Bitmap?,
    isRecording: Boolean,
    recordingDuration: Int,
    showExposureSlider: Boolean,
    exposureComp: Float,
    enableLocation: Boolean,
    enableRaw: Boolean,
    onCameraReady: (Camera, PreviewView) -> Unit,
    onTap: (Float, Float) -> Unit,
    onZoom: (Float) -> Unit,
    onShutter: () -> Unit,
    onFlashToggle: () -> Unit,
    onSwitchCamera: () -> Unit,
    onModeChange: (Int) -> Unit,
    onGridToggle: () -> Unit,
    onGalleryClick: () -> Unit,
    onExposureToggle: () -> Unit,
    onExposureChange: (Float) -> Unit,
    onLocationToggle: () -> Unit,
    onRawToggle: () -> Unit
) {
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().pointerInput(Unit) {
            detectTransformGestures { _, _, zoom, _ -> if (zoom != 1f) onZoom(zoom) }
        }) {
            CameraPreviewWithFocus(imageCapture, videoCapture, isFrontCamera, showGrid, showFocusRing, focusPoint,
                onTap, onCameraReady,
                Modifier.fillMaxWidth().aspectRatio(3f / 4f).align(Alignment.Center))
        }

        if (showFlash) Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.7f)))

        TopBar(flashMode, showGrid, enableLocation, enableRaw,
            onFlashToggle, onGridToggle, onExposureToggle, onLocationToggle, onRawToggle,
            Modifier.align(Alignment.TopStart).statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp))

        if (zoomRatio > 1.05f) {
            Text("${String.format("%.1f", zoomRatio)}x", color = TextPrimary, fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 64.dp)
                    .background(Surface2.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp))
        }

        if (showExposureSlider) {
            ExposureSlider(exposureComp, onExposureChange,
                Modifier.align(Alignment.CenterEnd).padding(end = 16.dp))
        }

        if (isRecording) {
            RecordingIndicator(recordingDuration,
                Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 12.dp))
        }

        // GPS indicator
        if (enableLocation) {
            Row(Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(top = 56.dp, end = 16.dp)
                .background(Surface2.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                .padding(horizontal = 8.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(Icons.Default.LocationOn, null, tint = TextMuted, modifier = Modifier.size(12.dp))
                Text("GPS", color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Medium)
            }
        }

        // RAW indicator
        if (enableRaw) {
            Row(Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(top = if (enableLocation) 80.dp else 56.dp, end = 16.dp)
                .background(Surface2.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                .padding(horizontal = 8.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(Icons.Default.PhotoCamera, null, tint = TextMuted, modifier = Modifier.size(12.dp))
                Text("RAW", color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Medium)
            }
        }

        BottomControls(selectedMode, lastPhotoBitmap, isRecording,
            onModeChange, onShutter, onSwitchCamera, onGalleryClick,
            Modifier.align(Alignment.BottomStart).navigationBarsPadding().padding(bottom = 24.dp))
    }
}

// ── Camera Preview ───────────────────────────────────────────────────

@Composable
private fun CameraPreviewWithFocus(
    imageCapture: ImageCapture,
    videoCapture: VideoCapture<Recorder>,
    isFrontCamera: Boolean,
    showGrid: Boolean,
    showFocusRing: Boolean,
    focusPoint: Offset?,
    onTap: (Float, Float) -> Unit,
    onCameraReady: (Camera, PreviewView) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    Box(modifier.clip(RoundedCornerShape(4.dp))) {
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).apply {
                    scaleType = PreviewView.ScaleType.FIT_CENTER
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                    setOnTouchListener { _, event ->
                        if (event.action == MotionEvent.ACTION_UP) onTap(event.x, event.y)
                        true
                    }
                }
            },
            update = { pv ->
                val future = ProcessCameraProvider.getInstance(context)
                future.addListener({
                    val provider = future.get()
                    val preview = Preview.Builder().setTargetAspectRatio(AspectRatio.RATIO_4_3).build()
                        .also { it.surfaceProvider = pv.surfaceProvider }
                    val selector = if (isFrontCamera) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
                    try {
                        provider.unbindAll()
                        val useCaseGroup = UseCaseGroup.Builder()
                            .addUseCase(preview)
                            .addUseCase(imageCapture)
                            .addUseCase(videoCapture)
                            .setViewPort(pv.viewPort!!)
                            .build()
                        val cam = provider.bindToLifecycle(lifecycleOwner, selector, useCaseGroup)
                        onCameraReady(cam, pv)
                    } catch (_: Exception) {}
                }, ContextCompat.getMainExecutor(context))
            },
            modifier = Modifier.fillMaxSize()
        )

        if (showGrid) GridOverlay(Modifier.fillMaxSize())
        if (showFocusRing && focusPoint != null) FocusRing(focusPoint)
    }
}

// ── Grid ─────────────────────────────────────────────────────────────

@Composable
private fun GridOverlay(modifier: Modifier) {
    Canvas(modifier) {
        val w = size.width; val h = size.height
        drawLine(GridColor, Offset(w / 3, 0f), Offset(w / 3, h), strokeWidth = 1f)
        drawLine(GridColor, Offset(w * 2 / 3, 0f), Offset(w * 2 / 3, h), strokeWidth = 1f)
        drawLine(GridColor, Offset(0f, h / 3), Offset(w, h / 3), strokeWidth = 1f)
        drawLine(GridColor, Offset(0f, h * 2 / 3), Offset(w, h * 2 / 3), strokeWidth = 1f)
    }
}

// ── Focus Ring ───────────────────────────────────────────────────────

@Composable
private fun FocusRing(point: Offset) {
    val alpha by animateFloatAsState(targetValue = 0f, animationSpec = tween(1200), label = "focus")
    Canvas(Modifier.fillMaxSize()) {
        drawCircle(color = TextPrimary.copy(alpha = 0.8f), radius = 40f, center = point, style = Stroke(2f))
    }
}

// ── Exposure Slider ──────────────────────────────────────────────────

@Composable
private fun ExposureSlider(value: Float, onChange: (Float) -> Unit, modifier: Modifier) {
    Column(modifier.width(36.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Default.Brightness6, null, tint = TextPrimary, modifier = Modifier.size(16.dp))
        Spacer(Modifier.height(8.dp))
        Slider(value = value, onValueChange = onChange, valueRange = -1f..1f, modifier = Modifier.height(200.dp),
            colors = SliderDefaults.colors(thumbColor = TextPrimary, activeTrackColor = TextPrimary, inactiveTrackColor = AccentDim))
    }
}

// ── Recording Indicator ──────────────────────────────────────────────

@Composable
private fun RecordingIndicator(duration: Int, modifier: Modifier) {
    val min = duration / 60; val sec = duration % 60
    Row(modifier.background(Surface.copy(alpha = 0.8f), RoundedCornerShape(14.dp)).padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(8.dp).background(Danger, CircleShape))
        Text("%d:%02d".format(min, sec), color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

// ── TopBar ───────────────────────────────────────────────────────────

@Composable
private fun TopBar(
    flashMode: Int, showGrid: Boolean, enableLocation: Boolean, enableRaw: Boolean,
    onFlashToggle: () -> Unit, onGridToggle: () -> Unit, onExposureToggle: () -> Unit,
    onLocationToggle: () -> Unit, onRawToggle: () -> Unit, modifier: Modifier
) {
    val flashIcon = when (flashMode) {
        ImageCapture.FLASH_MODE_ON -> Icons.Default.FlashOn
        ImageCapture.FLASH_MODE_OFF -> Icons.Default.FlashOff
        else -> Icons.Default.FlashAuto
    }
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        CircleIconButton(flashIcon, if (flashMode == ImageCapture.FLASH_MODE_OFF) AccentDim else TextMuted, onClick = onFlashToggle)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            CircleIconButton(Icons.Default.GridOn, if (showGrid) TextPrimary else TextMuted, onClick = onGridToggle)
            CircleIconButton(Icons.Default.Brightness6, TextMuted, onClick = onExposureToggle)
            CircleIconButton(Icons.Default.LocationOn, if (enableLocation) TextPrimary else TextMuted, onClick = onLocationToggle)
            Box(Modifier.size(36.dp).background(Surface2.copy(alpha = 0.6f), CircleShape)
                .clickable(remember { MutableInteractionSource() }, null) { onRawToggle() },
                contentAlignment = Alignment.Center) {
                Text("R", color = if (enableRaw) TextPrimary else TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ── Bottom Controls ──────────────────────────────────────────────────

@Composable
private fun BottomControls(selectedMode: Int, lastPhotoBitmap: Bitmap?, isRecording: Boolean,
    onModeChange: (Int) -> Unit, onShutter: () -> Unit, onSwitchCamera: () -> Unit, onGalleryClick: () -> Unit, modifier: Modifier) {
    val modes = listOf("Video", "Photo", "Portrait")
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
            modes.forEachIndexed { i, label ->
                Text(label.uppercase(), color = if (i == selectedMode) TextPrimary else TextMuted,
                    fontSize = 12.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.3.sp,
                    modifier = Modifier.clickable(remember { MutableInteractionSource() }, null) { onModeChange(i) })
            }
        }
        Spacer(Modifier.height(20.dp))
        Row(Modifier.fillMaxWidth().padding(horizontal = 32.dp),
            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(42.dp).background(Surface2, RoundedCornerShape(8.dp))
                .clickable(remember { MutableInteractionSource() }, null) { onGalleryClick() },
                contentAlignment = Alignment.Center) {
                if (lastPhotoBitmap != null) {
                    Image(lastPhotoBitmap.asImageBitmap(), null, Modifier.size(42.dp).clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop)
                } else { Icon(Icons.Default.GridOn, null, tint = TextMuted, modifier = Modifier.size(20.dp)) }
            }
            if (selectedMode == 0) {
                Box(Modifier.size(72.dp).border(3.dp, if (isRecording) Danger else TextPrimary, CircleShape)
                    .clickable(remember { MutableInteractionSource() }, null) { onShutter() }, contentAlignment = Alignment.Center) {
                    if (isRecording) Box(Modifier.size(28.dp).background(Danger, RoundedCornerShape(4.dp)))
                    else Box(Modifier.size(60.dp).background(Danger, CircleShape))
                }
            } else {
                Box(Modifier.size(72.dp).border(3.dp, TextPrimary, CircleShape)
                    .clickable(remember { MutableInteractionSource() }, null) { onShutter() }, contentAlignment = Alignment.Center) {
                    Box(Modifier.size(60.dp).background(TextPrimary, CircleShape))
                }
            }
            CircleIconButton(Icons.Default.Cameraswitch, TextPrimary, size = 42, onClick = onSwitchCamera)
        }
    }
}

// ── Review ───────────────────────────────────────────────────────────

@Composable
private fun ReviewScreen(processedBytes: ByteArray?, isProcessing: Boolean, selectedFilter: FilterType,
    onSelectFilter: (FilterType) -> Unit, onRetake: () -> Unit, onSave: () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        val bitmap = remember(processedBytes) { processedBytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) } }
        bitmap?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit) }
        if (isProcessing) ProcessingBadge(Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 12.dp))
        ReviewControls(selectedFilter, onSelectFilter, onRetake, onSave,
            Modifier.align(Alignment.BottomStart).navigationBarsPadding().padding(bottom = 24.dp))
    }
}

// ── Components ───────────────────────────────────────────────────────

@Composable
private fun CircleIconButton(icon: ImageVector, tint: Color, size: Int = 36, onClick: () -> Unit) {
    Box(Modifier.size(size.dp).background(Surface2.copy(alpha = 0.6f), CircleShape)
        .clickable(remember { MutableInteractionSource() }, null) { onClick() }, contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = tint, modifier = Modifier.size((size * 0.5).dp))
    }
}

@Composable
private fun ProcessingBadge(modifier: Modifier) {
    Row(modifier.background(Surface.copy(alpha = 0.7f), RoundedCornerShape(14.dp)).padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        CircularProgressIndicator(Modifier.size(12.dp), color = TextMuted, strokeWidth = 1.5.dp)
        Text("Processing", color = TextMuted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun ReviewControls(selectedFilter: FilterType, onSelectFilter: (FilterType) -> Unit,
    onRetake: () -> Unit, onSave: () -> Unit, modifier: Modifier) {
    Column(modifier.fillMaxWidth()) {
        LazyRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(horizontal = 20.dp)) {
            items(RustBridge.supportedFilters()) { filter ->
                val active = selectedFilter == filter
                Text(RustBridge.filterName(filter), color = if (active) Bg else TextMuted, fontSize = 12.sp, fontWeight = FontWeight.Medium,
                    letterSpacing = 0.2.sp, modifier = Modifier.clip(RoundedCornerShape(14.dp))
                        .background(if (active) TextPrimary else Color.Transparent)
                        .then(if (!active) Modifier.border(1.5.dp, AccentDim, RoundedCornerShape(14.dp)) else Modifier)
                        .clickable(remember { MutableInteractionSource() }, null) { onSelectFilter(filter) }
                        .padding(horizontal = 14.dp, vertical = 6.dp))
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            TextButton(onRetake, colors = ButtonDefaults.textButtonColors(contentColor = TextPrimary), shape = RoundedCornerShape(20.dp),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 10.dp)) { Text("Retake", fontSize = 14.sp, fontWeight = FontWeight.Medium) }
            Button(onSave, colors = ButtonDefaults.buttonColors(containerColor = TextPrimary), shape = RoundedCornerShape(20.dp),
                contentPadding = PaddingValues(horizontal = 28.dp, vertical = 10.dp)) { Text("Save", color = Bg, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
        }
    }
}

// ── ImageProxy → JPEG ────────────────────────────────────────────────

private fun imageProxyToJpegBytes(image: ImageProxy): ByteArray {
    val rotation = image.imageInfo.rotationDegrees
    val jpegBytes = if (image.format == ImageFormat.JPEG) {
        val buf = image.planes[0].buffer; ByteArray(buf.remaining()).also { buf.get(it) }
    } else {
        // YUV → NV21 (YCbCr semi-planar: YYYY...VUVU)
        val y = image.planes[0].buffer
        val u = image.planes[1].buffer
        val v = image.planes[2].buffer
        val ySize = y.remaining()
        val uvSize = u.remaining().coerceAtMost(v.remaining())
        val nv21 = ByteArray(ySize + uvSize * 2)
        y.get(nv21, 0, ySize)
        val uvOffset = ySize
        for (i in 0 until uvSize) {
            nv21[uvOffset + i * 2] = v.get(i)
            nv21[uvOffset + i * 2 + 1] = u.get(i)
        }
        val out = ByteArrayOutputStream()
        YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null).compressToJpeg(Rect(0, 0, image.width, image.height), 95, out)
        out.toByteArray()
    }
    return if (rotation != 0) rotateJpeg(jpegBytes, rotation) else jpegBytes
}

private fun rotateJpeg(bytes: ByteArray, degrees: Int): ByteArray {
    val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return bytes
    val rotated = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(degrees.toFloat()) }, true)
    bmp.recycle()
    val out = ByteArrayOutputStream(); rotated.compress(Bitmap.CompressFormat.JPEG, 95, out); rotated.recycle()
    return out.toByteArray()
}

// ── Last photo thumbnail ─────────────────────────────────────────────

private fun loadLastPhotoThumbnail(ctx: Context): Bitmap? {
    val uri = ctx.contentResolver.query(
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI, arrayOf(MediaStore.Images.Media._ID),
        "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?", arrayOf("%Pictures/CameraApp%"),
        "${MediaStore.Images.Media.DATE_ADDED} DESC"
    )?.use { c ->
        if (c.moveToFirst()) {
            val id = c.getLong(c.getColumnIndexOrThrow(MediaStore.Images.Media._ID))
            ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
        } else null
    }
    return uri?.let { try { ctx.contentResolver.loadThumbnail(it, android.util.Size(128, 128), null) } catch (_: Exception) { null } }
}