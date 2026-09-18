package com.camera.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.camera.app.bridge.PhotoSaver
import com.camera.app.bridge.RustBridge
import kotlinx.coroutines.launch
import uniffi.camera_shared_core.FilterType
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors

// ── Design Tokens ────────────────────────────────────────────────────

private val Bg = Color(0xFF000000)
private val Surface = Color(0xFF1A1A1A)
private val Surface2 = Color(0xFF252525)
private val TextPrimary = Color(0xFFFFFFFF)
private val TextMuted = Color(0xFF8E8E93)
private val AccentDim = Color(0xFF636366)

// ── CameraScreen ─────────────────────────────────────────────────────

@Composable
fun CameraScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED
        )
    }

    // 状态
    var capturedBytes by remember { mutableStateOf<ByteArray?>(null) }
    var processedBytes by remember { mutableStateOf<ByteArray?>(null) }
    var selectedFilter by remember { mutableStateOf(FilterType.NONE) }
    var isProcessing by remember { mutableStateOf(false) }
    var isFrontCamera by remember { mutableStateOf(false) }
    var flashMode by remember { mutableIntStateOf(ImageCapture.FLASH_MODE_AUTO) }
    var selectedMode by remember { mutableIntStateOf(1) } // 0=Video, 1=Photo, 2=Portrait

    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .setFlashMode(flashMode)
            .build()
    }

    val captureExecutor = remember { Executors.newSingleThreadExecutor() }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            launcher.launch(Manifest.permission.CAMERA)
        }
    }

    // 拍照
    fun takePhoto() {
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
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    // silent
                }
            }
        )
    }

    // 应用滤镜
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

    // 重拍
    fun retake() {
        capturedBytes = null
        processedBytes = null
        selectedFilter = FilterType.NONE
    }

    // 保存
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

    // 切换闪光模式
    fun toggleFlash() {
        flashMode = when (flashMode) {
            ImageCapture.FLASH_MODE_AUTO -> ImageCapture.FLASH_MODE_ON
            ImageCapture.FLASH_MODE_ON -> ImageCapture.FLASH_MODE_OFF
            else -> ImageCapture.FLASH_MODE_AUTO
        }
        imageCapture.flashMode = flashMode
    }

    // 切换前后摄像头
    fun switchCamera() {
        isFrontCamera = !isFrontCamera
    }

    // ── UI ──

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Bg)
    ) {
        if (!hasCameraPermission) {
            // 无权限
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "Camera access required",
                    color = TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "Grant permission to start taking photos",
                    color = TextMuted,
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Button(
                    onClick = { launcher.launch(Manifest.permission.CAMERA) },
                    colors = ButtonDefaults.buttonColors(containerColor = TextPrimary),
                    shape = RoundedCornerShape(20.dp),
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 10.dp)
                ) {
                    Text("Allow", color = Bg, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            return@Box
        }

        if (processedBytes != null) {
            // ── Review 界面 ──
            val bitmap = remember(processedBytes) {
                processedBytes?.let {
                    BitmapFactory.decodeByteArray(it, 0, it.size)
                }
            }

            bitmap?.let {
                Image(
                    bitmap = it.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // 处理中
            if (isProcessing) {
                ProcessingBadge(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .statusBarsPadding()
                        .padding(top = 12.dp)
                )
            }

            // 底部：滤镜 + 操作
            ReviewControls(
                selectedFilter = selectedFilter,
                onSelectFilter = { applyFilter(it) },
                onRetake = { retake() },
                onSave = { save() },
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .navigationBarsPadding()
                    .padding(bottom = 24.dp)
            )

        } else {
            // ── 取景器 ──
            CameraPreviewView(
                imageCapture = imageCapture,
                isFrontCamera = isFrontCamera,
                modifier = Modifier.fillMaxSize()
            )

            // 顶部工具栏
            TopBar(
                flashMode = flashMode,
                onFlashToggle = { toggleFlash() },
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )

            // 底部控制
            ViewfinderControls(
                selectedMode = selectedMode,
                onModeChange = { selectedMode = it },
                onShutter = { takePhoto() },
                onSwitchCamera = { switchCamera() },
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .navigationBarsPadding()
                    .padding(bottom = 24.dp)
            )
        }
    }
}

// ── TopBar ───────────────────────────────────────────────────────────

@Composable
private fun TopBar(
    flashMode: Int,
    onFlashToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val flashLabel = when (flashMode) {
        ImageCapture.FLASH_MODE_ON -> "ON"
        ImageCapture.FLASH_MODE_OFF -> "OFF"
        else -> "A"
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 闪光
        CircleTextButton(
            label = flashLabel,
            textColor = if (flashMode == ImageCapture.FLASH_MODE_OFF) AccentDim else TextMuted,
            onClick = onFlashToggle
        )

        // AUTO 标签
        Text(
            "AUTO",
            color = TextMuted,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.5.sp,
            modifier = Modifier
                .background(Surface2.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                .padding(horizontal = 10.dp, vertical = 4.dp)
        )

        // 曝光
        CircleTextButton(label = "EV", textColor = TextMuted, onClick = { /* TODO */ })
    }
}

// ── ViewfinderControls ───────────────────────────────────────────────

@Composable
private fun ViewfinderControls(
    selectedMode: Int,
    onModeChange: (Int) -> Unit,
    onShutter: () -> Unit,
    onSwitchCamera: () -> Unit,
    modifier: Modifier = Modifier
) {
    val modes = listOf("Video", "Photo", "Portrait")

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 模式切换
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

        // 快门行
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 相册
            CircleTextButton(label = "G", textColor = TextPrimary, onClick = { /* TODO */ })

            // 快门按钮
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

            // 翻转摄像头
            CircleTextButton(label = "R", textColor = TextPrimary, onClick = onSwitchCamera)
        }
    }
}

// ── 通用圆形文字按钮 ────────────────────────────────────────────────

@Composable
private fun CircleTextButton(
    label: String,
    textColor: Color,
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
        Text(
            label,
            color = textColor,
            fontSize = if (label.length > 1) 11.sp else 13.sp,
            fontWeight = FontWeight.SemiBold
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
        // 滤镜栏
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
                                1.5.dp,
                                AccentDim,
                                RoundedCornerShape(14.dp)
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

        // 操作按钮
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 重拍
            TextButton(
                onClick = onRetake,
                colors = ButtonDefaults.textButtonColors(contentColor = TextPrimary),
                shape = RoundedCornerShape(20.dp),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 10.dp)
            ) {
                Text("Retake", fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }

            // 保存
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

// ── ImageProxy → JPEG 转换 ──────────────────────────────────────────

private fun imageProxyToJpegBytes(image: ImageProxy): ByteArray {
    if (image.format == ImageFormat.JPEG) {
        val buffer = image.planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        return bytes
    }

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
    return out.toByteArray()
}

// ── CameraPreviewView ────────────────────────────────────────────────

@Composable
fun CameraPreviewView(
    imageCapture: ImageCapture,
    isFrontCamera: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    AndroidView(
        factory = { ctx ->
            PreviewView(ctx).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
            }
        },
        update = { previewView ->
            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()

                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }

                val cameraSelector = if (isFrontCamera) {
                    CameraSelector.DEFAULT_FRONT_CAMERA
                } else {
                    CameraSelector.DEFAULT_BACK_CAMERA
                }

                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview,
                        imageCapture
                    )
                } catch (_: Exception) {}
            }, ContextCompat.getMainExecutor(context))
        },
        modifier = modifier
    )
}