# Camera App

跨平台相机应用 — Rust 共享核心 + 双端原生 UI

## 架构

```
┌─────────────────────────────────────────────────┐
│  UI + 相机控制（各端 100% 原生）                  │
│  iOS:     SwiftUI + AVFoundation + Metal         │
│  Android: Compose + CameraX + OpenGL             │
├─────────────────────────────────────────────────┤
│  共享核心（Rust + UniFFI）                        │
│  图像处理 / 设置管理 / 数据模型 / 业务逻辑        │
└─────────────────────────────────────────────────┘
```

## 项目结构

```
camera-app/
├── shared-core/          # Rust 共享库 (UniFFI)
│   ├── src/
│   │   ├── lib.rs           入口
│   │   ├── camera.udl       UniFFI 接口定义
│   │   ├── types.rs         数据类型
│   │   ├── error.rs         错误类型
│   │   ├── image_processor.rs  图像处理 (滤镜/增强)
│   │   └── settings.rs      设置管理
│   └── Cargo.toml
│
├── android/              # Android 原生项目 (Compose + CameraX)
│   ├── app/src/main/java/com/camera/app/
│   │   ├── MainActivity.kt
│   │   ├── bridge/          Rust 桥接层
│   │   └── ui/              界面 + 设计 token
│   └── build.gradle.kts
│
├── ios/                  # iOS 原生项目 (SwiftUI + AVFoundation)
│   ├── CameraApp/
│   │   ├── Views/           界面
│   │   └── Services/        相机服务 + Rust 桥接
│   └── CameraApp.xcodeproj/
│
├── design/               # 设计规范
│   ├── DESIGN.md            设计 token + 组件规范
│   └── CameraUI.html        交互原型
│
├── scripts/              # 构建脚本
│   ├── build-android.sh     编译 Rust → Android + 生成 Kotlin 绑定
│   ├── build-ios.sh         编译 Rust → iOS + 生成 Swift 绑定
│   └── release.sh           打 tag 触发 CI 发布
│
├── .github/workflows/    # CI/CD
│   ├── ci.yml               PR/push 自动测试 + 构建
│   └── release.yml          tag 推送自动发布 APK
│
├── Cargo.toml            # Rust workspace
├── Makefile              # 快捷命令
└── README.md
```

## 快速开始

### 前置条件

- Rust 1.70+ (`curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh`)
- Android Studio (Android 开发)
- Xcode (iOS 开发，需要 Mac)

### 常用命令

```bash
# 测试
make test

# 格式化
make fmt

# 编译 Android (需要 Android NDK)
make android

# 编译 iOS (需要 macOS + Xcode)
make ios

# 发布新版本
make release VERSION=v0.2.0-alpha.1

# 清理构建产物
make clean
```

### 手动编译

```bash
# Rust 测试
cargo test --workspace

# 编译 Android .so
cargo ndk -t arm64-v8a build --release

# 编译 iOS .a (macOS)
cargo build --release --target aarch64-apple-ios

# 生成 Kotlin 绑定
cargo run --bin uniffi-bindgen -- generate \
    --library target/aarch64-linux-android/release/libcamera_shared_core.so \
    --language kotlin \
    --out-dir android/app/src/main/java/
```

## 功能

- 相机预览 (4:3 标准比例)
- 拍照 + 视频录制
- 点击对焦 + 捏合变焦
- 闪光灯 (Auto/On/Off)
- 前后摄像头切换
- 网格线 (三分法)
- 曝光补偿滑块
- GPS 地理位置标记 (EXIF)
- RAW DNG 拍摄
- Rust 图像滤镜 (Vivid/Warm/Cool/Noir/Fade)
- 自动增强
- 保存到相册 (MediaStore scoped storage)

## 发布

```bash
# Alpha
./scripts/release.sh v0.2.0-alpha.1

# Beta
./scripts/release.sh v0.2.0-beta.1

# 正式版
./scripts/release.sh v1.0.0
```

CI 自动构建 APK 并发布到 GitHub Releases。

## License

Private — All rights reserved.