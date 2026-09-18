# Camera App

跨平台相机应用 — Rust 共享核心 + 双端原生 UI

## 架构

```
┌─────────────────────────────────────────────┐
│  UI + 相机控制（各端 100% 原生）              │
│  iOS:     SwiftUI + AVFoundation + Metal     │
│  Android: Compose + CameraX + OpenGL         │
├─────────────────────────────────────────────┤
│  共享核心（Rust + UniFFI）                    │
│  图像处理 / 数据存储 / 业务逻辑 / 数据模型    │
└─────────────────────────────────────────────┘
```

## 目录结构

```
camera-app/
├── shared-core/          Rust 共享核心库
│   ├── src/
│   │   ├── camera.udl    UniFFI 接口定义
│   │   ├── lib.rs        入口
│   │   ├── types.rs      数据类型
│   │   ├── error.rs      错误类型
│   │   ├── image_processor.rs  图像处理
│   │   └── settings.rs   设置管理
│   └── Cargo.toml
├── android/              Android 原生项目 (Compose + CameraX)
├── ios/                  iOS 原生项目 (SwiftUI + AVFoundation)
├── Makefile              一键编译脚本
└── README.md
```

## 技术栈

| 层 | Android | iOS | 共享 |
|----|---------|-----|------|
| UI | Jetpack Compose | SwiftUI | — |
| 相机 | CameraX | AVFoundation | — |
| GPU | OpenGL ES | Metal | — |
| 核心 | — | — | Rust + UniFFI |
| 图像 | — | — | image crate + 自定义算法 |

## 开发

### 前置条件
- Rust 1.70+
- Android Studio (Android 开发)
- Xcode (iOS 开发，需要 Mac)

### 编译共享库

```bash
# 编译 Android .so
make android

# 编译 iOS .framework (需要 Mac)
make ios

# 运行 Rust 测试
make test
```

## License

Private — All rights reserved.