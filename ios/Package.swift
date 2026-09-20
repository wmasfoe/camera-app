// swift-tools-version: 5.9
// Package.swift — SPM wrapper for the Rust shared-core static library
//
// This lets Xcode projects add the Rust library as a Swift package dependency.
// The actual .a and .swift bindings must be generated first via:
//   ./scripts/build-ios.sh
//
// Usage in Xcode:
//   1. File → Add Package Dependencies → add local package at ios/
//   2. Or add to your Package.swift: .package(path: "../shared-core/ios")

import PackageDescription

let package = Package(
    name: "CameraSharedCore",
    platforms: [
        .iOS(.v16),
        .macOS(.v13),
    ],
    products: [
        .library(
            name: "CameraSharedCore",
            targets: ["CameraSharedCore"]
        ),
    ],
    targets: [
        .target(
            name: "CameraSharedCore",
            path: "CameraApp/Generated",
            exclude: ["camera_shared_core.h"],
            linkerSettings: [
                .linkedLibrary("camera_shared_core"),
                .linkedFramework("Security"),
                .linkedFramework("Foundation"),
            ]
        ),
    ]
)