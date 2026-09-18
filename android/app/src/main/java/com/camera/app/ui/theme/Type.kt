package com.camera.app.ui.theme

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle

// ── Typography Tokens ────────────────────────────────────────────────
// 使用系统字体，不自定义。参见 design/DESIGN.md §3

val CameraTypography = Typography(
    // 页面标题: 16sp, SemiBold
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 16.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    // 按钮文字: 14sp, Medium
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
    ),
    // 正文/说明: 13sp, Normal
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 13.sp,
        fontWeight = FontWeight.Normal,
    ),
    // 标签/模式切换: 12sp, Medium, letterSpacing 0.3
    labelLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.3.sp,
    ),
    // 状态标签 (AUTO): 11sp, SemiBold, letterSpacing 0.5
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.5.sp,
    ),
)
