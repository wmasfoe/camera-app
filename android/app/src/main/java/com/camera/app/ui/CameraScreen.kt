package com.camera.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.camera.app.bridge.PhotoSaver
import com.camera.app.bridge.RustBridge
import kotlinx.coroutines.launch
import uniffi.camera_shared_core.FilterType
import java.util.concurrent.Executors

@Composable
fun CameraScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED
        )
    }

    // 拍照后的图片字节（JPEG）
    var capturedBytes by remember { mutableStateOf<ByteArray?>(null) }

    // 处理后的图片字节
    var processedBytes by remember { mutableStateOf<ByteArray?>(null) }

    // 当前选中的滤镜
    var selectedFilter by remember { mutableStateOf(FilterType.NONE) }

    // 是否正在处理
    var isProcessing by remember { mutableStateOf(false) }

    // ImageCapture 用例
    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .setFlashMode(ImageCapture.FLASH_MODE_AUTO)
            .build()
    }

    // 拍照执行器
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

    // 拍照函数
    fun takePhoto() {
        imageCapture.takePicture(
            captureExecutor,
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    // 获取 JPEG 字节
                    val jpegBytes = image.toJpegBytes()
                    image.close()

                    // 传给 Rust 处理
                    scope.launch {
                        isProcessing = true
                        capturedBytes = jpegBytes

                        // 默认先显示原图
                        processedBytes = jpegBytes

                        // 调用 Rust 自动增强
                        val result = RustBridge.autoEnhance(jpegBytes)
                        result.onSuccess { enhanced ->
                            processedBytes = enhanced
                        }
                        isProcessing = false
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    println("拍照失败: ${exception.message}")
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

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        if (hasCameraPermission) {
            if (processedBytes != null) {
                // 显示拍摄/处理后的图片
                val bitmap = remember(processedBytes) {
                    processedBytes?.let {
                        BitmapFactory.decodeByteArray(it, 0, it.size)
                    }
                }

                bitmap?.let {
                    Image(
                        bitmap = it.asImageBitmap(),
                        contentDescription = "拍摄的照片",
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // 处理中指示器
                if (isProcessing) {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = Color.White
                    )
                }

                // 滤镜选择栏
                LazyRow(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 120.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp)
                ) {
                    items(RustBridge.supportedFilters()) { filter ->
                        FilterChip(
                            selected = selectedFilter == filter,
                            onClick = { applyFilter(filter) },
                            label = { Text(RustBridge.filterName(filter)) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color.White,
                                selectedLabelColor = Color.Black,
                                containerColor = Color.DarkGray,
                                labelColor = Color.White
                            )
                        )
                    }
                }

                // 底部操作栏
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 48.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 重拍按钮
                    Button(
                        onClick = {
                            capturedBytes = null
                            processedBytes = null
                            selectedFilter = FilterType.NONE
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.DarkGray
                        )
                    ) {
                        Text("重拍")
                    }

                    // 保存按钮
                    Button(
                        onClick = {
                            val bytes = processedBytes
                            if (bytes != null) {
                                scope.launch {
                                    val uri = PhotoSaver.saveJpegToGallery(context, bytes)
                                    if (uri != null) {
                                        Toast.makeText(context, "已保存到相册", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "保存失败", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White
                        )
                    ) {
                        Text("保存", color = Color.Black)
                    }
                }
            } else {
                // 相机预览
                CameraPreview(
                    imageCapture = imageCapture,
                    modifier = Modifier.fillMaxSize()
                )

                // 快门按钮
                Button(
                    onClick = { takePhoto() },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 48.dp)
                        .size(72.dp),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White
                    )
                ) {}
            }
        } else {
            // 无权限提示
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "需要相机权限",
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = { launcher.launch(Manifest.permission.CAMERA) }) {
                    Text("授予权限")
                }
            }
        }
    }
}

@Composable
fun CameraPreview(
    imageCapture: ImageCapture,
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

                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview,
                        imageCapture  // 绑定 ImageCapture 用例
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }, ContextCompat.getMainExecutor(context))
        },
        modifier = modifier
    )
}