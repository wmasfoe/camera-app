import SwiftUI
import PhotosUI
import CoreLocation

// MARK: - Camera View (Root)

/// Root camera view — orchestrates viewfinder, review, permissions, and all camera actions.
/// Matches Android CameraScreen functionality 1:1.
struct CameraView: View {
    @StateObject private var cameraService = CameraService()
    @StateObject private var locationService = LocationService()

    // Mode / state
    @State private var selectedMode: CaptureMode = .photo
    @State private var flashMode: FlashMode = .auto
    @State private var showGrid: Bool = false
    @State private var enableRaw: Bool = false
    @State private var enableLocation: Bool = false

    // Photo review
    @State private var capturedImageData: Data?
    @State private var processedImageData: Data?
    @State private var isProcessing = false

    // Alerts
    @State private var showAlert = false
    @State private var alertMessage = ""

    var body: some View {
        ZStack {
            CameraTokens.swiftBg.ignoresSafeArea()

            if cameraService.isAuthorized {
                if let reviewData = processedImageData {
                    // Review screen
                    ReviewView(
                        imageData: reviewData,
                        isProcessing: $isProcessing,
                        onSave: { savePhoto() },
                        onRetake: { retake() },
                        onFilterSelected: { filter in applyFilter(filter) }
                    )
                } else {
                    // Viewfinder
                    ViewfinderView(
                        cameraService: cameraService,
                        locationService: locationService,
                        flashMode: $flashMode,
                        selectedMode: $selectedMode,
                        showGrid: $showGrid,
                        enableRaw: $enableRaw,
                        enableLocation: $enableLocation,
                        onShutter: { handleShutter() },
                        onSwitchCamera: { cameraService.switchCamera() },
                        onCycleLens: { cameraService.cycleLens() }
                    )

                    // Processing overlay
                    if isProcessing {
                        Color.black.opacity(0.8)
                            .ignoresSafeArea()
                            .allowsHitTesting(false)

                        ProgressView()
                            .progressViewStyle(CircularProgressViewStyle(tint: .white))
                            .scaleEffect(1.5)
                    }
                }
            } else {
                // Permission screen
                permissionScreen
            }
        }
        .onAppear {
            cameraService.checkPermission()
        }
        .alert(alertMessage, isPresented: $showAlert) {
            Button("OK", role: .cancel) {}
        }
        .preferredColorScheme(.dark)
    }

    // MARK: - Permission Screen

    private var permissionScreen: some View {
        VStack(spacing: 16) {
            Text("Camera access required")
                .font(.system(size: 16, weight: .semibold))
                .foregroundColor(CameraTokens.swiftTextPrimary)

            Text("Grant permission to start taking photos")
                .font(.system(size: 13))
                .foregroundColor(CameraTokens.swiftTextMuted)

            Button {
                cameraService.requestPermission()
            } label: {
                Text("Allow")
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundColor(CameraTokens.swiftBg)
                    .padding(.horizontal, 24)
                    .padding(.vertical, 10)
                    .background(
                        RoundedRectangle(cornerRadius: 20)
                            .fill(CameraTokens.swiftTextPrimary)
                    )
            }
        }
    }

    // MARK: - Shutter Handler

    private func handleShutter() {
        switch selectedMode {
        case .video:
            if cameraService.isRecording {
                cameraService.stopRecording()
            } else {
                cameraService.startRecording()
            }

        case .photo, .portrait:
            takePhoto()
        }
    }

    // MARK: - Take Photo

    private func takePhoto() {
        // Vibrate
        let generator = UIImpactFeedbackGenerator(style: .light)
        generator.impactOccurred()

        cameraService.capturePhoto(flashMode: flashMode) { data in
            guard let data = data else {
                alertMessage = "Capture failed"
                showAlert = true
                return
            }

            capturedImageData = data
            processedImageData = data
            isProcessing = true

            // Apply auto-enhancement via Rust
            Task {
                let enhanced = await applyAutoEnhance(to: data)
                await MainActor.run {
                    processedImageData = enhanced ?? data
                    isProcessing = false
                }
            }

            // Refresh thumbnail
            Task {
                // Small delay for Photos library to index
                try? await Task.sleep(nanoseconds: 500_000_000)
            }
        }
    }

    // MARK: - Auto Enhancement

    private func applyAutoEnhance(to imageData: Data) async -> Data? {
        // UniFFI binding — call Rust autoEnhance
        // When UniFFI bindings are generated, replace this with:
        // let processor = camera_shared_core.ImageProcessor()
        // let result = try processor.autoEnhance(inputImage: Array(imageData))
        // return Data(result)

        // Placeholder: return original data
        return imageData
    }

    // MARK: - Apply Filter

    private func applyFilter(_ filter: FilterType) {
        guard let originalData = capturedImageData else { return }

        isProcessing = true

        Task {
            // UniFFI binding — call Rust applyFilter
            // When UniFFI bindings are generated, replace this with:
            // let processor = camera_shared_core.ImageProcessor()
            // let result = try processor.applyFilter(inputImage: Array(originalData), filter: filterType)
            // processedImageData = Data(result)

            // Placeholder: return original data
            await MainActor.run {
                processedImageData = originalData
                isProcessing = false
            }
        }
    }

    // MARK: - Save Photo

    private func savePhoto() {
        guard let data = processedImageData else { return }

        Task {
            var location: CLLocation? = nil
            if enableLocation {
                location = await locationService.currentLocation()
            }

            let saved = await PhotoSaver.saveJpeg(data, location: location)
            await MainActor.run {
                if saved {
                    retake()
                    // Refresh thumbnail
                    Task {
                        try? await Task.sleep(nanoseconds: 300_000_000)
                    }
                } else {
                    alertMessage = "Failed to save photo"
                    showAlert = true
                }
            }
        }
    }

    // MARK: - Retake

    private func retake() {
        capturedImageData = nil
        processedImageData = nil
        isProcessing = false
        cameraService.markProcessingDone()
    }
}

// MARK: - Preview

#Preview {
    CameraView()
        .ignoresSafeArea()
}