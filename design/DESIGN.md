# Camera App — Design System

本文件定义了 camera-app 的完整设计规范。所有 UI 代码（Android Compose / iOS SwiftUI）必须遵循此规范。

---

## 1. 设计原则

- **预览优先** — 相机预览填满屏幕，控件尽量少
- **纯黑背景** — OLED safe，不用渐变
- **系统字体** — 不自定义字体
- **零 emoji** — 不使用任何 emoji
- **原生控件** — 用平台原生组件，不用自定义图标库
- **功能可见** — 每个控件的交互状态必须清晰（默认 / 按下 / 选中）

---

## 2. Color Tokens

### Android (Compose)

```kotlin
private val Bg          = Color(0xFF000000)   // 纯黑背景
private val Surface     = Color(0xFF1A1A1A)   // 预览/图片背景
private val Surface2    = Color(0xFF252525)   // 按钮/控件背景
private val TextPrimary = Color(0xFFFFFFFF)   // 主文字
private val TextMuted   = Color(0xFF8E8E93)   // 次要文字
private val AccentDim   = Color(0xFF636366)   // 边框/分割线
```

### iOS (Swift)

```swift
static let bg          = Color(red: 0, green: 0, blue: 0)              // #000000
static let surface     = Color(red: 0.102, green: 0.102, blue: 0.102)  // #1A1A1A
static let surface2    = Color(red: 0.145, green: 0.145, blue: 0.145)  // #252525
static let textPrimary = Color(red: 1, green: 1, blue: 1)              // #FFFFFF
static let textMuted   = Color(red: 0.557, green: 0.557, blue: 0.576)  // #8E8E93
static let accentDim   = Color(red: 0.388, green: 0.388, blue: 0.400)  // #636366
```

### CSS Variables

```css
--bg:          #000000;
--surface:     #1a1a1a;
--surface-2:   #252525;
--text:        #ffffff;
--text-muted:  #8e8e93;
--accent-dim:  #636366;
```

### 使用规则

| 场景 | Token |
|------|-------|
| 页面背景 | `Bg` |
| 相机预览/图片背景 | `Surface` |
| 按钮/控件背景 | `Surface2` (alpha 0.4-0.6) |
| 标题/主要操作文字 | `TextPrimary` |
| 标签/次要文字/状态指示 | `TextMuted` |
| 边框/分割线 | `AccentDim` |
| 选中态 pill 填充 | `TextPrimary` |
| 选中态 pill 文字 | `Bg` |

---

## 3. Typography

### 字体

使用系统字体，不自定义：
- Android: `FontFamily.Default`（Roboto）
- iOS: `.system()`（SF Pro）

### 字号与字重

| 场景 | Size | Weight | LetterSpacing |
|------|------|--------|---------------|
| 页面标题 | 16sp | SemiBold (600) | — |
| 按钮文字 | 14sp | Medium (500) / SemiBold (600) | — |
| 正文/说明 | 13sp | Normal (400) | — |
| 标签/模式切换 | 12sp | Medium (500) | 0.3sp |
| 状态标签 (AUTO) | 11sp | SemiBold (600) | 0.5sp |

### 使用规则

- 大写标签（AUTO, VIDEO, PHOTO）用 `letterSpacing = 0.5.sp`
- 按钮文字不加大写，保持原样
- 不使用 italic

---

## 4. Spacing

| 元素 | 尺寸 |
|------|------|
| 快门按钮直径 | 72dp |
| 快门外环宽度 | 3dp |
| 快门内圆直径 | 60dp |
| 侧边按钮 (相册/翻转) | 42dp |
| 顶部按钮 (闪光/曝光) | 36dp |
| 滤镜 pill 内边距 | 14dp horizontal, 6dp vertical |
| 滤镜 pill 圆角 | 14dp |
| 滤镜 pill 间距 | 10dp |
| 操作按钮圆角 | 20dp |
| 操作按钮内边距 | 24-28dp horizontal, 10dp vertical |
| 底部安全区 | 24dp (navigationBarsPadding 之上) |
| 水平边距 | 16-20dp |

---

## 5. Component Library

### 5.1 快门按钮 (Shutter)

```
结构: 外环(3dp border) + 内圆(实心)
颜色: 外环 = TextPrimary, 内圆 = TextPrimary
交互: 按下时 scale(0.92), 内圆 scale(0.9)
```

Compose:
```kotlin
Box(
    modifier = Modifier
        .size(72.dp)
        .border(3.dp, TextPrimary, CircleShape)
        .clickable { onShutter() },
    contentAlignment = Alignment.Center
) {
    Box(
        modifier = Modifier
            .size(60.dp)
            .background(TextPrimary, CircleShape)
    )
}
```

SwiftUI:
```swift
ZStack {
    Circle()
        .strokeBorder(Color.textPrimary, lineWidth: 3)
        .frame(width: 72, height: 72)
    Circle()
        .fill(Color.textPrimary)
        .frame(width: 60, height: 60)
}
```

### 5.2 圆形图标按钮 (Circle Icon Button)

```
结构: 圆形背景 + 文字/图标
尺寸: 36dp (顶部) / 42dp (底部)
背景: Surface2, alpha 0.4-0.6
文字: TextMuted (顶部) / TextPrimary (底部)
```

Compose:
```kotlin
IconButton(
    onClick = { /* ... */ },
    modifier = Modifier
        .size(36.dp)                        // 或 42.dp
        .background(Surface2.copy(alpha = 0.6f), CircleShape)
) {
    Text("A", color = TextMuted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
}
```

### 5.3 滤镜 Pill

```
结构: 圆角胶囊 (14dp radius)
默认态: 透明背景 + 1.5dp AccentDim 边框 + TextMuted 文字
选中态: TextPrimary 背景 + Bg 文字 + 无边框
```

Compose:
```kotlin
Text(
    label,
    color = if (isActive) Bg else TextMuted,
    fontSize = 12.sp,
    fontWeight = FontWeight.Medium,
    letterSpacing = 0.2.sp,
    modifier = Modifier
        .clip(RoundedCornerShape(14.dp))
        .background(if (isActive) TextPrimary else Color.Transparent)
        .then(
            if (!isActive) Modifier.border(1.5.dp, AccentDim, RoundedCornerShape(14.dp))
            else Modifier
        )
        .clickable { /* ... */ }
        .padding(horizontal = 14.dp, vertical = 6.dp)
)
```

### 5.4 操作按钮 (Action Button)

**主要按钮 (Primary)** — 白底黑字:
```kotlin
Button(
    onClick = { /* ... */ },
    colors = ButtonDefaults.buttonColors(containerColor = TextPrimary),
    shape = RoundedCornerShape(20.dp),
    contentPadding = PaddingValues(horizontal = 28.dp, vertical = 10.dp)
) {
    Text("Save", color = Bg, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
}
```

**次要按钮 (Secondary)** — 灰底白字:
```kotlin
TextButton(
    onClick = { /* ... */ },
    colors = ButtonDefaults.textButtonColors(contentColor = TextPrimary),
    shape = RoundedCornerShape(20.dp),
    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 10.dp)
) {
    Text("Retake", fontSize = 14.sp, fontWeight = FontWeight.Medium)
}
```

### 5.5 状态标签 (Status Badge)

```
结构: 圆角胶囊 (12dp radius)
背景: Surface2, alpha 0.6
文字: TextMuted, 11sp, SemiBold, letterSpacing 0.5
```

Compose:
```kotlin
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
```

### 5.6 处理中指示器 (Processing Badge)

```
结构: 圆角胶囊 (14dp radius) + 圆形进度 + 文字
背景: Surface, alpha 0.7
进度圈: 12dp, TextMuted, strokeWidth 1.5dp
文字: "Processing", TextMuted, 12sp, Medium
```

---

## 6. Screen Layouts

### 6.1 取景器 (Viewfinder)

```
┌─────────────────────────┐
│  [Flash]  [AUTO]  [EV]  │  ← TopBar (statusBarsPadding + 16dp h + 8dp v)
│                         │
│                         │
│     Camera Preview      │  ← 填满屏幕
│                         │
│                         │
│  VIDEO  PHOTO  PORTRAIT │  ← 模式切换 (居中)
│                         │
│  [G]   [快门]    [R]   │  ← 底部控制 (navigationBarsPadding + 24dp)
└─────────────────────────┘
```

### 6.2 拍照后 / 滤镜选择 (Review)

```
┌─────────────────────────┐
│                         │
│                         │
│     Captured Photo      │  ← 填满屏幕
│                         │
│   [Processing badge]    │  ← 居中顶部，仅处理中显示
│                         │
│                         │
│ Original Vivid Warm ... │  ← 滤镜 pill 横滑 (20dp 水平 padding)
│                         │
│   [Retake]     [Save]   │  ← 操作按钮 (20dp 水平 padding)
└─────────────────────────┘
```

### 6.3 无权限 (Permission)

```
┌─────────────────────────┐
│                         │
│                         │
│   Camera access         │  ← 标题: 16sp, SemiBold, TextPrimary
│      required           │
│                         │
│   Grant permission      │  ← 说明: 13sp, TextMuted
│   to start taking       │
│      photos             │
│                         │
│       [Allow]           │  ← Primary 按钮
│                         │
└─────────────────────────┘
```

---

## 7. Animation

| 场景 | 动画 | 参数 |
|------|------|------|
| 状态切换 (取景器↔Review) | fade | `fadeIn()` + `fadeOut()` |
| 快门反馈 | scale | `scale(0.92)` on press |
| 处理中 badge | 旋转 | `CircularProgressIndicator` 自带动画 |

- 不使用 slide、spring、bounce 等夸张动画
- 所有动画持续时间 < 300ms
- 尊重 `prefers-reduced-motion`

---

## 8. 禁止事项

- 不用 emoji
- 不用渐变背景
- 不用 glassmorphism / blur 装饰
- 不用自定义图标字体
- 不用彩色装饰（整个 app 只有黑白色系）
- 不用圆角超过 20dp 的卡片
- 不在按钮上加图标（用文字代替）
- 不使用 Material Design 默认的 FAB、Card、Chip 样式

---

## 9. 文件组织

```
ui/
├── theme/
│   ├── Color.kt          // 所有 Color token
│   ├── Type.kt           // 字号/字重定义
│   └── Theme.kt          // MaterialTheme 覆盖
├── components/
│   ├── ShutterButton.kt
│   ├── CircleIconButton.kt
│   ├── FilterPill.kt
│   ├── ActionButton.kt
│   ├── StatusBadge.kt
│   └── ProcessingBadge.kt
├── screens/
│   ├── ViewfinderScreen.kt
│   ├── ReviewScreen.kt
│   └── PermissionScreen.kt
└── CameraScreen.kt       // 主入口，组合各 screen
```

---

## 10. Checklist (新页面/组件提交前)

- [ ] 背景是纯黑 `#000`
- [ ] 文字颜色只用 `TextPrimary` 或 `TextMuted`
- [ ] 控件背景只用 `Surface2` (带 alpha)
- [ ] 边框只用 `AccentDim`
- [ ] 字号在 {11, 12, 13, 14, 16} sp 范围内
- [ ] 字重在 {Normal, Medium, SemiBold} 范围内
- [ ] 圆角在 {12, 14, 20} dp 范围内
- [ ] 没有 emoji
- [ ] 没有渐变
- [ ] 没有自定义字体
- [ ] 按下态有视觉反馈
- [ ] 使用系统字体
