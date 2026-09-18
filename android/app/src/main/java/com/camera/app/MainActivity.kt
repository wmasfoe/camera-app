package com.camera.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.camera.app.bridge.RustBridge
import com.camera.app.ui.CameraScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 初始化 Rust 共享库
        RustBridge.init()

        enableEdgeToEdge()
        setContent {
            CameraScreen()
        }
    }
}