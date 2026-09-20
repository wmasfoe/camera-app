# iOS Camera App — Build Plan

## Overview

This plan covers everything needed to bring the iOS camera app to feature parity with the Android app: UniFFI binding generation, Xcode project setup, per-feature implementation, and CI pipeline.

---

## Phase 1: UniFFI Bindings & Xcode Project Setup

### 1.1 UniFFI Swift Binding Generation

The Rust shared-core at `shared-core/` already has:
- `Cargo.toml` with `crate-type = ["cdylib", "staticlib"]` and UniFFI 0.28
- `src/camera.udl` defining all types (enums, dictionaries, interfaces)
- `src/uniffi-bindgen.rs` binary for code generation
- `scripts/build-ios.sh` that builds `aarch64-apple-ios` and generates Swift

**What needs to happen:**

```
cd shared-core
cargo build --release --target aarch64-apple-ios
cargo run --bin uniffi-bindgen -- generate \
    --library target/aarch64-apple-ios/release/libcamera_shared_core.a \
    --language swift \
    --out-dir ../ios/CameraApp/Generated/
```

This produces:
- `ios/CameraApp/Generated/camera_shared_core.swift` — all Swift bindings
- `ios/CameraApp/Generated/camera_shared_coreFFI.h` — C FFI header
- `ios/CameraApp/Generated/camera_shared_core.modulemap` — module map

**Also need simulator slice for development:**
```
cargo build --release --target aarch64-apple-ios-sim
```
Then create XCFramework combining device + simulator `.a` files.

### 1.2 XCFramework Creation

Create `scripts/build-ios-xcframework.sh`:

```bash
#!/bin/bash
set -euo pipefail

SHARED_CORE="shared-core"
IOS_DIR="ios"
FRAMEWORK_DIR="$IOS_DIR/Frameworks"
GENERATED_DIR="$IOS_DIR/CameraApp/Generated"

# Build for device
cargo build --release --target aarch64-apple-ios -p camera-shared-core

# Build for simulator (Apple Silicon)
cargo build --release --target aarch64-apple-ios-sim -p camera-shared-core

# Create XCFramework
mkdir -p "$FRAMEWORK_DIR"
xcodebuild -create-xcframework \
    -library "$SHARED_CORE/target/aarch64-apple-ios/release/libcamera_shared_core.a" \
    -headers "$GENERATED_DIR" \
    -library "$SHARED_CORE/target/aarch64-apple-ios-sim/release/libcamera_shared_core.a" \
    -headers "$GENERATED_DIR" \
    -output "$FRAMEWORK_DIR/CameraSharedCore.xcframework"

# Generate Swift bindings (from device build)
cargo run --bin uniffi-bindgen -- generate \
    --library "$SHARED_CORE/target/aarch64-apple-ios/release/libcamera_shared_core.a" \
    --language swift \
    --out-dir "$GENERATED_DIR/"
```

### 1.3 Xcode Project Setup

**Create `ios/CameraApp.xcodeproj`** (or use `.xcworkspace` with SPM):

**Option A — XcodeProj (recommended):**
- Create via Xcode or `xcodegen` with a `project.yml`:

```yaml
name: CameraApp
options:
  bundleIdPrefix: com.camera.app
  deploymentTarget:
    iOS: "17.0"
  xcodeVersion: "15.0"
settings:
  SWIFT_OBJC_BRIDGING_HEADER: ""
targets:
  CameraApp:
    type: application
    platform: iOS
    sources:
      - CameraApp
      - CameraApp/Generated
    settings:
      OTHER_LDFLAGS: ["-lcamera_shared_core"]
      LIBRARY_SEARCH_PATHS: ["$(PROJECT_DIR)/Frameworks/CameraSharedCore.xcframework/ios-arm64"]
      HEADER_SEARCH_PATHS: ["$(PROJECT_DIR)/Generated"]
    dependencies:
      - framework: Frameworks/CameraSharedCore.xcframework
        embed: true
    info:
      path: CameraApp/Info.plist
      properties:
        NSCameraUsageDescription: "Camera access for photos and videos"
        NSMicrophoneUsageDescription: "Microphone access for video recording"
        NSLocationWhenInUseUsageDescription: "Location for GPS tagging photos"
        NSPhotoLibraryAddUsageDescription: "Save photos and videos to your library"
```

**Option B — Manual Xcode project:**
- Create new iOS App project in Xcode
- Drag `Generated/` folder into project
- Add XCFramework to "Frameworks, Libraries, and Embedded Content"
- Set `LIBRARY_SEARCH_PATHS` and `HEADER_SEARCH_PATHS` in Build Settings
- Set deployment target to iOS 17.0

### 1.4 Info.plist Required Keys

```xml
<key>NSCameraUsageDescription</key>
<string>Camera access for photos and videos</string>
<key>NSMicrophoneUsageDescription</key>
<string>Microphone access for video recording</string>
<key>NSLocationWhenInUseUsageDescription</key>
<string>Location for GPS tagging photos</string>
<key>NSPhotoLibraryAddUsageDescription</key>
<string>Save photos and videos to your library</string>
```

---

## Phase 2: Feature Implementation Map

### Target File Structure

```
ios/CameraApp/
├── CameraAppApp.swift                 # Entry point (exists)
├── Info.plist                         # NEW: permissions + config
├── Generated/                         # NEW: UniFFI output (auto-generated)
│   ├── camera_shared_core.swift
│   ├── camera_shared_coreFFI.h
│   └── camera_shared_core.modulemap
├── Services/
│   ├── RustBridge.swift               # REWRITE: real UniFFI integration
│   ├── CameraService.swift            # REWRITE: full AVFoundation
│   ├── LocationService.swift          # NEW: CoreLocation manager
│   ├── PhotoLibraryService.swift      # NEW: PHPhotoLibrary save + EXIF
│   └── VideoRecordingService.swift    # NEW: AVCaptureMovieFileOutput
├── ViewModels/
│   └── CameraViewModel.swift          # NEW: @Observable state machine
├── Views/
│   ├── CameraView.swift               # REWRITE: full-featured main view
│   ├── CameraPreviewView.swift        # MODIFY: add gesture recognizers
│   ├── ControlOverlay.swift           # NEW: top/bottom control bars
│   ├── FilterSelector.swift           # NEW: horizontal filter strip
│   ├── ExposureSlider.swift           # NEW: orientation-aware slider
│   ├── ZoomPresets.swift              # NEW: .5x/1x/2x/5x buttons
│   ├── ReviewScreen.swift             # NEW: retake/save with filters
│   ├── GridOverlay.swift              # NEW: rule-of-thirds grid
│   └── FocusIndicator.swift           # NEW: tap-to-focus ring
├── Models/
│   └── CameraMode.swift               # NEW: Photo/Video/Portrait enum
└── Utilities/
    ├── EXIFWriter.swift               # NEW: GPS EXIF injection
    └── DeviceCapabilities.swift       # NEW: lens detection
```

---

### Feature-by-Feature Implementation

#### F1: CameraX Preview → AVFoundation Preview
**iOS API:** `AVCaptureSession` + `AVCaptureVideoPreviewLayer`
**Status:** Basic version exists in `CameraService.swift`
**Changes needed:**
- `CameraPreviewView.swift`: Add `UIGestureRecognizer` for tap-to-focus and pinch-to-zoom
- `CameraService.swift`: Expose `AVCaptureDevice` reference for focus/zoom/exposure control
- Use `AVCaptureVideoDataOutput` for real-time frame access (needed for grid overlay rendering)

#### F2: Tap-to-Focus + Focus Indicator
**iOS API:** `AVCaptureDevice.focusPointOfInterest`, `AVCaptureDevice.focusMode`
**Files:** `CameraPreviewView.swift` (gesture), `FocusIndicator.swift` (animated ring), `CameraService.swift` (focus API)
**Implementation:**
- `UITapGestureRecognizer` on preview → convert point to `previewLayer.captureDevicePointOfInterest`
- Call `device.setFocusPointOfInterest(point)` + `.autoFocus`
- Animate focus ring at tap location, auto-dismiss after 1.2s

#### F3: Pinch-to-Zoom
**iOS API:** `AVCaptureDevice.videoZoomFactor`
**Files:** `CameraPreviewView.swift` (gesture), `CameraService.swift` (zoom API)
**Implementation:**
- `UIPinchGestureRecognizer` → map scale to `device.videoZoomFactor` clamped to `[1.0, device.activeFormat.videoMaxZoomFactor]`
- Smooth ramp: `device.ramp(toVideoZoomFactor:withRate:)`

#### F4: Photo/Video/Portrait Mode Switching
**iOS API:** `AVCaptureSession` reconfiguration, `AVCaptureMovieFileOutput`
**Files:** `CameraMode.swift` (enum), `CameraService.swift` (session config), `VideoRecordingService.swift`, `ControlOverlay.swift` (mode picker)
**Implementation:**
- Mode picker as horizontal segmented control at bottom
- Photo mode: `AVCapturePhotoOutput` (current)
- Video mode: Add `AVCaptureMovieFileOutput` to session, start/stop recording
- Portrait mode: `AVCapturePhotoOutput` with `AVCapturePhotoSettings.depthDataDeliveryEnabled` (requires dual/triple camera)

#### F5: Front/Back Camera Switching + Ultra-Wide Detection
**iOS API:** `AVCaptureDevice.DiscoverySession`
**Files:** `CameraService.swift`, `DeviceCapabilities.swift`, `ControlOverlay.swift`
**Implementation:**
- `DiscoverySession(deviceTypes: [.builtInWideAngleCamera, .builtInUltraWideCamera, .builtInTelephotoCamera], mediaType: .video, position: .back)`
- Detect available lenses, show `.5x` button only if ultra-wide exists
- Lens cycling: reconfigure session with selected `AVCaptureDevice`

#### F6: Flash Modes (Auto/On/Off/Torch)
**iOS API:** `AVCapturePhotoSettings.flashMode`, `AVCaptureDevice.torchMode`
**Files:** `CameraService.swift`, `ControlOverlay.swift`
**Implementation:**
- Cycle through `.auto`, `.on`, `.off` for photo capture
- Torch mode for video recording
- Update `AVCapturePhotoSettings.flashMode` per capture

#### F7: Grid Overlay Toggle
**iOS API:** Custom `CAShapeLayer` or SwiftUI `Canvas`
**Files:** `GridOverlay.swift`, `CameraViewModel.swift`
**Implementation:**
- Rule-of-thirds grid drawn as semi-transparent lines
- Toggle via settings button in control bar
- Persist with `UserDefaults` (bridged to Rust `SettingsManager` via UniFFI)

#### F8: Exposure Compensation Slider
**iOS API:** `AVCaptureDevice.exposureTargetBias`
**Files:** `ExposureSlider.swift`, `CameraService.swift`
**Implementation:**
- Read `device.minExposureTargetBias` / `maxExposureTargetBias`
- Map slider [-1...+1] to bias range
- Orientation-aware: vertical slider in portrait, horizontal in landscape
- Use `GeometryReader` + `DeviceOrientation` publisher

#### F9: Zoom Presets (.5x / 1x / 2x / 5x)
**iOS API:** `AVCaptureDevice.videoZoomFactor`
**Files:** `ZoomPresets.swift`, `CameraService.swift`
**Implementation:**
- Horizontal button row above shutter
- Map preset to actual zoom factor (`.5x` = ultra-wide switch if available, else 0.5× digital)
- Animate zoom transition with `ramp(toVideoZoomFactor:withRate:)`
- Highlight active preset based on current zoom

#### F10: GPS Location Tagging
**iOS API:** `CoreLocation` (`CLLocationManager`)
**Files:** `LocationService.swift` (NEW), `CameraViewModel.swift`, `PhotoLibraryService.swift`
**Implementation:**
- `CLLocationManager` with `requestWhenInUseAuthorization()`
- `startUpdatingLocation()` + use `lastLocation` as fast fallback (mirrors Android pattern)
- Store `CLLocation` on capture, inject into EXIF via `CGImageDestination` metadata
- Respect `AppSettings.save_location` from Rust SettingsManager

#### F11: RAW DNG Capture
**iOS API:** `AVCapturePhotoOutput` with `AVCapturePhotoSettings.rawPhotoPixelFormatType`
**Files:** `CameraService.swift`, `PhotoLibraryService.swift`
**Implementation:**
- Check `photoOutput.availableRawPhotoPixelFormatTypes`
- For RAW: use `AVCapturePhotoSettings(rawPixelFormatType: kCVPixelFormatType_14Bayer_RGGB)` (or device-specific)
- `AVCapturePhoto.fileDataRepresentation()` returns DNG data
- Save DNG separately via `PHAssetCreationRequest` with `.dng` resource
- NOTE: iOS RAW is simpler than Android Camera2 — no need to pause/unbind preview

#### F12: Filter Selection (UniFFI)
**iOS API:** UniFFI `ImageProcessor.apply_filter`
**Files:** `RustBridge.swift`, `FilterSelector.swift`, `ReviewScreen.swift`
**Implementation:**
- `RustBridge.swift` wraps `camera_shared_core.ImageProcessor().applyFilter(inputImage:filter:)`
- Convert `[UInt8]` ↔ `Data` at bridge boundary
- Filter selector as horizontal `ScrollView` of thumbnails
- Generate preview thumbnails: apply each filter to a small downsampled version of captured image

#### F13: Auto-Enhance (UniFFI)
**iOS API:** UniFFI `ImageProcessor.auto_enhance`
**Files:** `RustBridge.swift`, `CameraView.swift`
**Implementation:**
- After capture, call `RustBridge.autoEnhance(imageData:)` on background queue
- Show progress indicator during processing
- Fall back to original on error (existing pattern in CameraView.swift)

#### F14: Save to Gallery with EXIF GPS
**iOS API:** `Photos` framework (`PHPhotoLibrary`, `PHAssetCreationRequest`)
**Files:** `PhotoLibraryService.swift`, `EXIFWriter.swift`
**Implementation:**
- `PHPhotoLibrary.shared().performChanges` with `PHAssetChangeRequest`
- For JPEG: Use `CGImageDestination` to write JPEG with GPS EXIF dictionary
- EXIF GPS dict: `{kCGImagePropertyGPSDictionary: [Latitude, Longitude, Altitude, TimeStamp]}`
- For RAW+JPEG: `PHAssetCreationRequest` with `.photo` and `.alternatePhoto` resources

#### F15: Review Screen with Retake/Save + Filters
**iOS API:** SwiftUI full-screen cover
**Files:** `ReviewScreen.swift`, `FilterSelector.swift`
**Implementation:**
- Full-screen `.sheet` or navigation cover showing captured image
- Bottom: filter strip (from UniFFI `supportedFilters()`)
- Buttons: Retake (dismiss) + Save (write to gallery with EXIF)
- Tapping a filter calls UniFFI `applyFilter` → updates preview in real-time

#### F16: Video Recording with Duration Indicator
**iOS API:** `AVCaptureMovieFileOutput`
**Files:** `VideoRecordingService.swift`, `ControlOverlay.swift`, `CameraService.swift`
**Implementation:**
- Add `AVCaptureMovieFileOutput` to `AVCaptureSession`
- `startRecording(to: recordingDelegate:)` → file URL in temp dir
- Timer publishing elapsed seconds → red dot + "00:15" indicator
- Stop → save to Photos via `PHPhotoLibrary`
- Mirror Android: audio enabled, HD quality

#### F17: Landscape Layout Adaptation
**iOS API:** SwiftUI `GeometryReader`, `UIDevice.orientation`, `NotificationCenter` publisher
**Files:** `CameraView.swift`, `ControlOverlay.swift`, `ExposureSlider.swift`
**Implementation:**
- `@Environment(\.horizontalSizeClass)` + device orientation publisher
- Portrait: controls at bottom, vertical exposure slider
- Landscape: controls on trailing edge, horizontal exposure slider
- Preview layer: `videoGravity = .resizeAspectFill` (fills regardless of orientation)
- Status bar / safe area awareness

---

## Phase 3: RustBridge.swift Rewrite

Replace the placeholder with real UniFFI integration:

```swift
import Foundation
import camera_shared_core  // UniFFI-generated module

enum RustBridge {
    private static let processor = ImageProcessor()
    private static let settings = SettingsManager(configPath: settingsPath())

    // MARK: - Version
    static func isLoaded() -> Bool {
        (try? getVersion()) != nil
    }
    static func getVersion() -> String {
        camera_shared_core.getVersion()
    }

    // MARK: - Image Processing
    static func applyFilter(imageData: Data, filter: FilterType) throws -> Data {
        let result = try processor.applyFilter(
            inputImage: [UInt8](imageData),
            filter: filter
        )
        return Data(result)
    }

    static func autoEnhance(imageData: Data) throws -> Data {
        let result = try processor.autoEnhance(inputImage: [UInt8](imageData))
        return Data(result)
    }

    static func compress(imageData: Data, quality: UInt32) throws -> Data {
        let result = try processor.compress(inputImage: [UInt8](imageData), quality: quality)
        return Data(result)
    }

    static func supportedFilters() -> [FilterType] {
        processor.supportedFilters()
    }

    static func filterName(_ filter: FilterType) -> String {
        processor.filterName(filter: filter)
    }

    // MARK: - Settings
    static func loadSettings() -> AppSettings {
        settings.load()
    }
    static func saveSettings(_ s: AppSettings) throws {
        try settings.save(settings: s)
    }
    static func resetSettings() -> AppSettings {
        settings.resetToDefault()
    }

    // MARK: - Helpers
    private static func settingsPath() -> String {
        let dir = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
        return dir.appendingPathComponent("settings.json").path
    }
}
```

---

## Phase 4: CI Pipeline (GitHub Actions)

### 4.1 Build Script for CI (`scripts/ci-ios.sh`)

```bash
#!/bin/bash
set -euo pipefail

# Install Rust iOS targets
rustup target add aarch64-apple-ios aarch64-apple-ios-sim

# Build Rust library
cd shared-core
cargo build --release --target aarch64-apple-ios
cargo build --release --target aarch64-apple-ios-sim

# Generate Swift bindings
cargo run --bin uniffi-bindgen -- generate \
    --library target/aarch64-apple-ios/release/libcamera_shared_core.a \
    --language swift \
    --out-dir ../ios/CameraApp/Generated/

# Create XCFramework
cd ..
xcodebuild -create-xcframework \
    -library shared-core/target/aarch64-apple-ios/release/libcamera_shared_core.a \
    -headers ios/CameraApp/Generated \
    -library shared-core/target/aarch64-apple-ios-sim/release/libcamera_shared_core.a \
    -headers ios/CameraApp/Generated \
    -output ios/Frameworks/CameraSharedCore.xcframework
```

### 4.2 GitHub Actions Workflow

**File: `.github/workflows/ios.yml`**

```yaml
name: iOS Build & Test

on:
  push:
    branches: [main, develop]
    paths:
      - 'shared-core/**'
      - 'ios/**'
      - '.github/workflows/ios.yml'
  pull_request:
    paths:
      - 'shared-core/**'
      - 'ios/**'

env:
  CARGO_TERM_COLOR: always

jobs:
  build-rust:
    name: Build Rust → iOS
    runs-on: macos-15
    steps:
      - uses: actions/checkout@v4

      - name: Install Rust toolchain
        uses: dtolnay/rust-toolchain@stable
        with:
          targets: aarch64-apple-ios,aarch64-apple-ios-sim

      - name: Cache Cargo
        uses: actions/cache@v4
        with:
          path: |
            ~/.cargo/registry
            ~/.cargo/git
            shared-core/target
          key: cargo-ios-${{ hashFiles('shared-core/Cargo.lock') }}

      - name: Build shared-core (device)
        run: cargo build --release --target aarch64-apple-ios -p camera-shared-core
        working-directory: shared-core

      - name: Build shared-core (simulator)
        run: cargo build --release --target aarch64-apple-ios-sim -p camera-shared-core
        working-directory: shared-core

      - name: Run Rust tests
        run: cargo test --workspace

      - name: Generate Swift bindings
        run: |
          mkdir -p ios/CameraApp/Generated
          cargo run --bin uniffi-bindgen -- generate \
            --library target/aarch64-apple-ios/release/libcamera_shared_core.a \
            --language swift \
            --out-dir ../ios/CameraApp/Generated/
        working-directory: shared-core

      - name: Create XCFramework
        run: |
          mkdir -p ios/Frameworks
          xcodebuild -create-xcframework \
            -library shared-core/target/aarch64-apple-ios/release/libcamera_shared_core.a \
            -headers ios/CameraApp/Generated \
            -library shared-core/target/aarch64-apple-ios-sim/release/libcamera_shared_core.a \
            -headers ios/CameraApp/Generated \
            -output ios/Frameworks/CameraSharedCore.xcframework

      - name: Upload XCFramework
        uses: actions/upload-artifact@v4
        with:
          name: CameraSharedCore-xcframework
          path: ios/Frameworks/CameraSharedCore.xcframework

      - name: Upload Swift bindings
        uses: actions/upload-artifact@v4
        with:
          name: swift-bindings
          path: ios/CameraApp/Generated/

  build-xcode:
    name: Build Xcode Project
    runs-on: macos-15
    needs: build-rust
    steps:
      - uses: actions/checkout@v4

      - name: Download XCFramework
        uses: actions/download-artifact@v4
        with:
          name: CameraSharedCore-xcframework
          path: ios/Frameworks/CameraSharedCore.xcframework

      - name: Download Swift bindings
        uses: actions/download-artifact@v4
        with:
          name: swift-bindings
          path: ios/CameraApp/Generated/

      - name: Select Xcode
        run: sudo xcode-select -s /Applications/Xcode_15.4.app

      - name: Build (simulator)
        run: |
          xcodebuild build \
            -project ios/CameraApp.xcodeproj \
            -scheme CameraApp \
            -destination 'platform=iOS Simulator,name=iPhone 15 Pro,OS=17.5' \
            -configuration Debug \
            CODE_SIGNING_ALLOWED=NO \
            | xcpretty

      - name: Build (device, no codesign)
        run: |
          xcodebuild build \
            -project ios/CameraApp.xcodeproj \
            -scheme CameraApp \
            -destination 'generic/platform=iOS' \
            -configuration Release \
            CODE_SIGNING_ALLOWED=NO \
            | xcpretty

  test:
    name: Unit Tests
    runs-on: macos-15
    needs: build-rust
    steps:
      - uses: actions/checkout@v4

      - name: Download artifacts
        uses: actions/download-artifact@v4
        with:
          name: CameraSharedCore-xcframework
          path: ios/Frameworks/CameraSharedCore.xcframework

      - name: Download Swift bindings
        uses: actions/download-artifact@v4
        with:
          name: swift-bindings
          path: ios/CameraApp/Generated/

      - name: Run tests
        run: |
          xcodebuild test \
            -project ios/CameraApp.xcodeproj \
            -scheme CameraApp \
            -destination 'platform=iOS Simulator,name=iPhone 15 Pro,OS=17.5' \
            CODE_SIGNING_ALLOWED=NO \
            | xcpretty
```

### 4.3 CI Checklist

| Step | What | Artifact |
|------|------|----------|
| Rust build (device) | `cargo build --release --target aarch64-apple-ios` | `libcamera_shared_core.a` |
| Rust build (sim) | `cargo build --release --target aarch64-apple-ios-sim` | `libcamera_shared_core.a` |
| Rust tests | `cargo test --workspace` | pass/fail |
| Swift binding gen | `uniffi-bindgen generate` | `camera_shared_core.swift` |
| XCFramework | `xcodebuild -create-xcframework` | `CameraSharedCore.xcframework` |
| Xcode build (sim) | `xcodebuild build` | `.app` bundle |
| Xcode build (device) | `xcodebuild build` generic | `.app` bundle |
| Unit tests | `xcodebuild test` | test results |

---

## Phase 5: Implementation Priority Order

| Priority | Feature | Complexity | Dependencies |
|----------|---------|------------|-------------|
| P0 | UniFFI binding generation + XCFramework | Medium | Rust toolchain on macOS |
| P0 | Xcode project creation | Medium | XCFramework ready |
| P0 | RustBridge.swift rewrite (real UniFFI) | Low | Generated bindings |
| P1 | CameraViewModel (state machine) | Medium | RustBridge |
| P1 | Tap-to-focus + focus indicator | Low | CameraService |
| P1 | Pinch-to-zoom | Low | CameraService |
| P1 | Front/back switching | Low | CameraService |
| P1 | Flash modes | Low | CameraService |
| P2 | Filter selector (UniFFI) | Medium | RustBridge |
| P2 | Auto-enhance (UniFFI) | Low | RustBridge |
| P2 | Review screen with retake/save | Medium | Filter selector |
| P2 | Save to gallery with EXIF GPS | Medium | PhotoLibraryService |
| P2 | Grid overlay | Low | CameraViewModel |
| P3 | Exposure slider (orientation-aware) | Medium | CameraService |
| P3 | Zoom presets | Low | CameraService |
| P3 | GPS location tagging | Medium | LocationService |
| P3 | Video recording | Medium | CameraService |
| P3 | Photo/Video/Portrait modes | Medium | Multiple services |
| P4 | RAW DNG capture | High | CameraService, device check |
| P4 | Ultra-wide detection | Medium | DiscoverySession |
| P4 | Landscape layout adaptation | Medium | All views |
| P5 | CI pipeline | Medium | All above |

---

## Key Gotchas

1. **UniFFI version**: UDL uses UniFFI 0.28. Swift bindings generated must match the version in `Cargo.toml`. Don't mix versions.

2. **XCFramework vs raw .a**: Xcode won't link a raw `.a` cleanly across simulator/device. Always use XCFramework.

3. **Simulator architecture**: On Apple Silicon CI runners (macos-14+), simulator is `aarch64-apple-ios-sim`, not `x86_64`. The plan targets ARM simulators only.

4. **RAW on iOS**: Unlike Android Camera2, iOS `AVCapturePhotoOutput` handles RAW natively without unbinding the preview. Much simpler.

5. **Crate-type**: `shared-core/Cargo.toml` has both `cdylib` and `staticlib`. For iOS static linking, `staticlib` is correct. For XCFramework headers, the `cdylib` headers from UniFFI generation are still needed.

6. **Privacy strings**: Missing `NSCameraUsageDescription` etc. will crash on launch. Must be in Info.plist before first camera use.

7. **Photos permission**: iOS 14+ has `.addOnly` authorization level — use `PHPhotoLibrary.requestAuthorization(for: .addOnly)` to avoid needing full photo library access.