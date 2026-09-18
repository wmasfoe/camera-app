package com.camera.app.ui

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import android.net.Uri
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.MediaStore
import android.view.MotionEvent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
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
import com.camera.app.bridge.PhotoSaver
import com.camera.app.bridge.RustBridge
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import uniffi.camera_shared_core.FilterType
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

// ── Design Tokens ────────────────────────────────────────────────────

private val Bg = Color(0xFF000000)
private val Surface = Color(0xFF1A1A1A)
private val Surface2 = Color(0xFF252525)
private val TextPrimary = Color(0xFFFFFFFF)
private val TextMuted = Color(0xFF8E8E93)
private val AccentDim = Color(0xFF636366)
private val GridColor = Color(0x33FFFFFF)

// ── CameraScreen ─────────────────────────────────────────────────────

@Composable
fun CameraScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED
        )
    }

    // 拍摄状态
    var capturedBytes by remember { mutableStateOf<ByteArray?>(null) }
    var processedBytes by remember { mutableStateOf<ByteArray?>(null) }
    var selectedFilter by remember { mutableStateOf(FilterType.NONE) }
    var isProcessing by remember { mutableStateOf(false) }

    // 相机控制
    var isFrontCamera by remember { mutableStateOf(false) }
    var flashMode by remember { mutableIntStateOf(ImageCapture.FLASH_MODE_AUTO) }
    var selectedMode by remember { mutableIntStateOf(1) }
    var zoomRatio by remember { mutableFloatStateOf(1f) }
    var showGrid by remember { mutableStateOf(false) }

    // 对焦指示
    var focusPoint by remember { mutableStateOf<Offset?>(null) }
    var showFocusRing by remember { mutableStateOf(false) }

    // 快门闪白
    var showFlash by remember { mutableStateOf(false) }

    // 上次拍照预览
    var lastPhotoBitmap by remember { mutableStateOf<Bitmap?>(null) }

    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .setFlashMode(flashMode)
            .setTargetAspectRatio(androidx.camera.core.AspectRatio.RATIO_4_3)
            .build()
    }

    // CameraControl 引用
    var cameraControl by remember { mutableStateOf<androidx.camera.core.CameraControl?>(null) }

    val captureExecutor = remember { Executors.newSingleThreadExecutor() }
    val vibrator = remember {
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            launcher.launch(Manifest.permission.CAMERA)
        }
        // 加载上次拍的照片
        lastPhotoBitmap = loadLastPhotoThumbnail(context)
    }

    // 对焦动画消失
    LaunchedEffect(showFocusRing) {
        if (showFocusRing) {
            delay(1200)
            showFocusRing = false
        }
    }

    // 快门闪白消失
    LaunchedEffect(showFlash) {
        if (showFlash) {
            delay(120)
            showFlash = false
        }
    }

    // 拍照
    fun takePhoto() {
        // 快门反馈
        showFlash = true
        vibrator?.vibrate(VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE))

        imageCapture.takePicture(
            captureExecutor,
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    val jpegBytes = imageProxyToJpegBytes(image)
                    image.close()

                    scope.launch {
                        isProcessing = true
                        capturedBytes = jpegBytes
                        processedBytes = jpegBytes

                        val result = RustBridge.autoEnhance(jpegBytes)
                        result.onSuccess { enhanced ->
                            processedBytes = enhanced
                        }
                        isProcessing = false

                        // 更新上次拍照预览
                        lastPhotoBitmap = loadLastPhotoThumbnail(context)
                    }
                }

                override fun onError(exception: ImageCaptureException) {}
            }
        )
    }

    // 对焦
    fun onTapToFocus(x: Float, y: Float) {
        focusPoint = Offset(x, y)
        showFocusRing = true

        val ctrl = cameraControl ?: return
        val factory = (context as? android.app.Activity)?.let {
            // meteringPointFactory 从 PreviewView 获取
            null // 下面通过 PreviewView 的 meteringPointFactory 处理
        }

        // 使用 CameraControl 的 point metering
        val point = androidx.camera.core.MeteringPointFactory
            .createPoint(x.toDouble(), y.toDouble())
        val action = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE)
            .setAutoCancelDuration(3, TimeUnit.SECONDS)
            .build()
        ctrl.startFocusAndMetering(action)
    }

    // 变焦
    fun onZoom(delta: Float) {
        val ctrl = cameraControl ?: return
        zoomRatio = (zoomRatio * delta).coerceIn(1f, 10f)
        ctrl.setZoomRatio(zoomRatio)
    }

    fun applyFilter(filter: FilterType) {
        val bytes = capturedBytes ?: return
        selectedFilter = filter
        scope.launch {
            isProcessing = true
            if (filter == FilterType.NONE) {
                processedBytes = bytes
            } else {
                val result = RustBridge.applyFilter(bytes, filter)
                result.onSuccess { filtered ->
                    processedBytes = filtered
                }
            }
            isProcessing = false
        }
    }

    fun retake() {
        capturedBytes = null
        processedBytes = null
        selectedFilter = FilterType.NONE
    }

    fun save() {
        val bytes = processedBytes ?: return
        scope.launch {
            val uri = PhotoSaver.saveJpegToGallery(context, bytes)
            if (uri != null) {
                Toast.makeText(context, "Saved", Toast.LENGTH_SHORT).show()
                retake()
            } else {
                Toast.makeText(context, "Failed", Toast.LENGTH_SHORT).show()
            }
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
        isFrontCamera = !isFrontCamera
        zoomRatio = 1f
    }

    // ── UI ──

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Bg)
    ) {
        if (!hasCameraPermission) {
            PermissionScreen(
                onRequest = { launcher.launch(Manifest.permission.CAMERA) }
            )
            return@Box
        }

        if (processedBytes != null) {
            // ── Review ──
            ReviewScreen(
                processedBytes = processedBytes,
                isProcessing = isProcessing,
                selectedFilter = selectedFilter,
                onSelectFilter = { applyFilter(it) },
                onRetake = { retake() },
                onSave = { save() }
            )
        } else {
            // ── Viewfinder ──
            ViewfinderScreen(
                imageCapture = imageCapture,
                isFrontCamera = isFrontCamera,
                zoomRatio = zoomRatio,
                showGrid = showGrid,
                showFocusRing = showFocusRing,
                focusPoint = focusPoint,
                showFlash = showFlash,
                flashMode = flashMode,
                selectedMode = selectedMode,
                lastPhotoBitmap = lastPhotoBitmap,
                onCameraReady = { ctrl -> cameraControl = ctrl },
                onTapToFocus = { x, y -> onTapToFocus(x, y) },
                onZoom = { delta -> onZoom(delta) },
                onShutter = { takePhoto() },
                onFlashToggle = { toggleFlash() },
                onSwitchCamera = { switchCamera() },
                onModeChange = { selectedMode = it },
                onGridToggle = { showGrid = !showGrid },
                onGalleryClick = { /* TODO: open gallery */ }
            )
        }
    }
}

// ── Permission Screen ────────────────────────────────────────────────

@Composable
private fun PermissionScreen(onRequest: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "Camera access required",
            color = TextPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Grant permission to start taking photos",
            color = TextMuted,
            fontSize = 13.sp
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = onRequest,
            colors = ButtonDefaults.buttonColors(containerColor = TextPrimary),
            shape = RoundedCornerShape(20.dp),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 10.dp)
        ) {
            Text("Allow", color = Bg, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

// ── Viewfinder Screen ────────────────────────────────────────────────

@Composable
private fun ViewfinderScreen(
    imageCapture: ImageCapture,
    isFrontCamera: Boolean,
    zoomRatio: Float,
    showGrid: Boolean,
    showFocusRing: Boolean,
    focusPoint: Offset?,
    showFlash: Boolean,
    flashMode: Int,
    selectedMode: Int,
    lastPhotoBitmap: Bitmap?,
    onCameraReady: (androidx.camera.core.CameraControl) -> Unit,
    onTapToFocus: (Float, Float) -> Unit,
    onZoom: (Float) -> Unit,
    onShutter: () -> Unit,
    onFlashToggle: () -> Unit,
    onSwitchCamera: () -> Unit,
    onModeChange: (Int) -> Unit,
    onGridToggle: () -> Unit,
    onGalleryClick: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        // 相机预览 (4:3 比例，居中)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTransformGestures { _, _, zoom, _ ->
                        if (zoom != 1f) onZoom(zoom)
                    }
                }
        ) {
            CameraPreviewWithFocus(
                imageCapture = imageCapture,
                isFrontCamera = isFrontCamera,
                showGrid = showGrid,
                showFocusRing = showFocusRing,
                focusPoint = focusPoint,
                onTap = { x, y -> onTapToFocus(x, y) },
                onCameraReady = onCameraReady,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(3f / 4f)
                    .align(Alignment.Center)
            )
        }

        // 快门闪白
        if (showFlash) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White.copy(alpha = 0.7f))
            )
        }

        // 顶部工具栏
        TopBar(
            flashMode = flashMode,
            showGrid = showGrid,
            onFlashToggle = onFlashToggle,
            onGridToggle = onGridToggle,
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        )

        // 变焦倍数
        if (zoomRatio > 1.05f) {
            Text(
                "${String.format("%.1f", zoomRatio)}x",
                color = TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 64.dp)
                    .background(Surface2.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }

        // 底部控制
        BottomControls(
            selectedMode = selectedMode,
            lastPhotoBitmap = lastPhotoBitmap,
            onModeChange = onModeChange,
            onShutter = onShutter,
            onSwitchCamera = onSwitchCamera,
            onGalleryClick = onGalleryClick,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .navigationBarsPadding()
                .padding(bottom = 24.dp)
        )
    }
}

// ── CameraPreview with Focus & Grid ──────────────────────────────────

@Composable
private fun CameraPreviewWithFocus(
    imageCapture: ImageCapture,
    isFrontCamera: Boolean,
    showGrid: Boolean,
    showFocusRing: Boolean,
    focusPoint: Offset?,
    onTap: (Float, Float) -> Unit,
    onCameraReady: (androidx.camera.core.CameraControl) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    Box(modifier = modifier.clip(RoundedCornerShape(4.dp))) {
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).apply {
                    scaleType = PreviewView.ScaleType.FIT_CENTER
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE

                    // 点击对焦
                    setOnTouchListener { _, event ->
                        if (event.action == MotionEvent.ACTION_UP) {
                            onTap(event.x, event.y)
                        }
                        true
                    }
                }
            },
            update = { previewView ->
                val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()

                    val preview = Preview.Builder()
                        .setTargetAspectRatio(androidx.camera.core.AspectRatio.RATIO_4_3)
                        .build()
                        .also {
                            it.surfaceProvider = previewView.surfaceProvider
                        }

                    val cameraSelector = if (isFrontCamera) {
                        CameraSelector.DEFAULT_FRONT_CAMERA
                    } else {
                        CameraSelector.DEFAULT_BACK_CAMERA
                    }

                    try {
                        cameraProvider.unbindAll()
                        val camera = cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            cameraSelector,
                            preview,
                            imageCapture
                        )
                        onCameraReady(camera.cameraControl)
                    } catch (_: Exception) {}
                }, ContextCompat.getMainExecutor(context))
            },
            modifier = Modifier.fillMaxSize()
        )

        // 网格线叠加
        if (showGrid) {
            GridOverlay(modifier = Modifier.fillMaxSize())
        }

        // 对焦环
        if (showFocusRing && focusPoint != null) {
            FocusRing(
                x = focusPoint.x,
                y = focusPoint.y,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

// ── Grid Overlay ─────────────────────────────────────────────────────

@Composable
private fun GridOverlay(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        // 竖线 (三分)
        drawLine(GridColor, Offset(w / 3, 0f), Offset(w / 3, h), strokeWidth = 1f)
        drawLine(GridColor, Offset(w * 2 / 3, 0f), Offset(w * 2 / 3, h), strokeWidth = 1f)

        // 横线 (三分)
        drawLine(GridColor, Offset(0f, h / 3), Offset(w, h / 3), strokeWidth = 1f)
        drawLine(GridColor, Offset(0f, h * 2 / 3), Offset(w, h * 2 / 3), strokeWidth = 1f)
    }
}

// ── Focus Ring ───────────────────────────────────────────────────────

@Composable
private fun FocusRing(x: Float, y: Float, modifier: Modifier = Modifier) {
    val alpha by animateFloatAsState(
        targetValue = 0f,
        animationSpec = tween(durationMillis = 1200),
        label = "focusFade"
    )

    // 对焦环在 AndroidView 坐标系中定位，这里用 Canvas 画
    Canvas(modifier = modifier) {
        drawCircle(
            color = TextPrimary.copy(alpha = 0.8f),
            radius = 40f,
            center = Offset(x, y),
            style = Stroke(width = 2f)
        )
    }
}

// ── TopBar ───────────────────────────────────────────────────────────

@Composable
private fun TopBar(
    flashMode: Int,
    showGrid: Boolean,
    onFlashToggle: () -> Unit,
    onGridToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val flashIcon = when (flashMode) {
        ImageCapture.FLASH_MODE_ON -> Icons.Default.FlashOn
        ImageCapture.FLASH_MODE_OFF -> Icons.Default.FlashOff
        else -> Icons.Default.FlashAuto
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircleIconButton(
            icon = flashIcon,
            tint = if (flashMode == ImageCapture.FLASH_MODE_OFF) AccentDim else TextMuted,
            onClick = onFlashToggle
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // 网格线
            CircleIconButton(
                icon = Icons.Default.GridOn,
                tint = if (showGrid) TextPrimary else TextMuted,
                onClick = onGridToggle
            )

            // EV
            CircleIconButton(
                icon = Icons.Default.Brightness6,
                tint = TextMuted,
                onClick = { /* TODO: EV slider */ }
            )
        }
    }
}

// ── Bottom Controls ──────────────────────────────────────────────────

@Composable
private fun BottomControls(
    selectedMode: Int,
    lastPhotoBitmap: Bitmap?,
    onModeChange: (Int) -> Unit,
    onShutter: () -> Unit,
    onSwitchCamera: () -> Unit,
    onGalleryClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val modes = listOf("Video", "Photo", "Portrait")

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Mode switch
        Row(
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            modes.forEachIndexed { index, label ->
                Text(
                    label.uppercase(),
                    color = if (index == selectedMode) TextPrimary else TextMuted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.3.sp,
                    modifier = Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onModeChange(index) }
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Shutter row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 上次拍照预览
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(Surface2, RoundedCornerShape(8.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onGalleryClick() },
                contentAlignment = Alignment.Center
            ) {
                if (lastPhotoBitmap != null) {
                    Image(
                        bitmap = lastPhotoBitmap.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.GridOn,
                        contentDescription = null,
                        tint = TextMuted,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Shutter
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .border(3.dp, TextPrimary, CircleShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onShutter() },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .background(TextPrimary, CircleShape)
                )
            }

            // Switch camera
            CircleIconButton(
                icon = Icons.Default.Cameraswitch,
                tint = TextPrimary,
                size = 42,
                onClick = onSwitchCamera
            )
        }
    }
}

// ── Review Screen ────────────────────────────────────────────────────

@Composable
private fun ReviewScreen(
    processedBytes: ByteArray?,
    isProcessing: Boolean,
    selectedFilter: FilterType,
    onSelectFilter: (FilterType) -> Unit,
    onRetake: () -> Unit,
    onSave: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        val bitmap = remember(processedBytes) {
            processedBytes?.let {
                BitmapFactory.decodeByteArray(it, 0, it.size)
            }
        }

        bitmap?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        }

        if (isProcessing) {
            ProcessingBadge(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 12.dp)
            )
        }

        ReviewControls(
            selectedFilter = selectedFilter,
            onSelectFilter = onSelectFilter,
            onRetake = onRetake,
            onSave = onSave,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .navigationBarsPadding()
                .padding(bottom = 24.dp)
        )
    }
}

// ── CircleIconButton ─────────────────────────────────────────────────

@Composable
private fun CircleIconButton(
    icon: ImageVector,
    tint: Color,
    size: Int = 36,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .background(Surface2.copy(alpha = 0.6f), CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size((size * 0.5).dp)
        )
    }
}

// ── ProcessingBadge ──────────────────────────────────────────────────

@Composable
private fun ProcessingBadge(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .background(Surface.copy(alpha = 0.7f), RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(12.dp),
            color = TextMuted,
            strokeWidth = 1.5.dp
        )
        Text("Processing", color = TextMuted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

// ── ReviewControls ───────────────────────────────────────────────────

@Composable
private fun ReviewControls(
    selectedFilter: FilterType,
    onSelectFilter: (FilterType) -> Unit,
    onRetake: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(horizontal = 20.dp)
        ) {
            items(RustBridge.supportedFilters()) { filter ->
                val isActive = selectedFilter == filter
                Text(
                    RustBridge.filterName(filter),
                    color = if (isActive) Bg else TextMuted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.2.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (isActive) TextPrimary else Color.Transparent)
                        .then(
                            if (!isActive) Modifier.border(
                                1.5.dp, AccentDim, RoundedCornerShape(14.dp)
                            ) else Modifier
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onSelectFilter(filter) }
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(
                onClick = onRetake,
                colors = ButtonDefaults.textButtonColors(contentColor = TextPrimary),
                shape = RoundedCornerShape(20.dp),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 10.dp)
            ) {
                Text("Retake", fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }

            Button(
                onClick = onSave,
                colors = ButtonDefaults.buttonColors(containerColor = TextPrimary),
                shape = RoundedCornerShape(20.dp),
                contentPadding = PaddingValues(horizontal = 28.dp, vertical = 10.dp)
            ) {
                Text("Save", color = Bg, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

// ── ImageProxy → JPEG (带旋转) ──────────────────────────────────────

private fun imageProxyToJpegBytes(image: ImageProxy): ByteArray {
    val rotation = image.imageInfo.rotationDegrees

    if (image.format == ImageFormat.JPEG) {
        val buffer = image.planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        // JPEG 需要旋转
        return if (rotation != 0) rotateJpeg(bytes, rotation) else bytes
    }

    // YUV → JPEG
    val yBuffer = image.planes[0].buffer
    val uBuffer = image.planes[1].buffer
    val vBuffer = image.planes[2].buffer

    val ySize = yBuffer.remaining()
    val uSize = uBuffer.remaining()
    val vSize = vBuffer.remaining()

    val nv21 = ByteArray(ySize + uSize + vSize)
    yBuffer.get(nv21, 0, ySize)
    vBuffer.get(nv21, ySize, vSize)
    uBuffer.get(nv21, ySize + vSize, uSize)

    val yuvImage = YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
    val out = ByteArrayOutputStream()
    yuvImage.compressToJpeg(Rect(0, 0, image.width, image.height), 95, out)
    val jpegBytes = out.toByteArray()

    return if (rotation != 0) rotateJpeg(jpegBytes, rotation) else jpegBytes
}

private fun rotateJpeg(jpegBytes: ByteArray, degrees: Int): ByteArray {
    val bitmap = BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)
        ?: return jpegBytes

    val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
    val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    bitmap.recycle()

    val out = ByteArrayOutputStream()
    rotated.compress(Bitmap.CompressFormat.JPEG, 95, out)
    rotated.recycle()
    return out.toByteArray()
}

// ── 加载上次拍照缩略图 ──────────────────────────────────────────────

private fun loadLastPhotoThumbnail(context: Context): Bitmap? {
    val projection = arrayOf(MediaStore.Images.Media._ID)
    val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

    val query = context.contentResolver.query(
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
        projection,
        "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?",
        arrayOf("%Pictures/CameraApp%"),
        sortOrder
    )

    query?.use { cursor ->
        if (cursor.moveToFirst()) {
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val id = cursor.getLong(idColumn)
            val uri = ContentUris.withAppendedId(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id
            )
            return try {
                context.contentResolver.loadThumbnail(uri, android.util.Size(128, 128), null)
            } catch (_: Exception) {
                null
            }
        }
    }
    return null
}