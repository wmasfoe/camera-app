import SwiftUI
import AVFoundation
import CoreLocation

// MARK: - Viewfinder View

/// Camera viewfinder with full control overlay — matches Android ViewfinderScreen
struct ViewfinderView: View {
    @ObservedObject var cameraService: CameraService
    @ObservedObject var locationService: LocationService

    // State bindings from parent
    @Binding var flashMode: FlashMode
    @Binding var selectedMode: CaptureMode
    @Binding var showGrid: Bool
    @Binding var enableRaw: Bool
    @Binding var enableLocation: Bool

    // Internal state
    @State private var showExposureSlider = false
    @State private var focusPoint: CGPoint?
    @State private var showFocusRing = false
    @State private var showShutterFlash = false
    @State private var lastPhotoThumbnail: UIImage?
    @State private var currentZoomDisplay: String = ""

    // Callbacks
    let onShutter: () -> Void
    let onSwitchCamera: () -> Void
    let onCycleLens: () -> Void

    @Environment(\.horizontalSizeClass) private var horizontalSizeClass

    var body: some View {
        GeometryReader { geometry in
            let isLandscape = geometry.size.width > geometry.size.height

            ZStack {
                // Camera preview
                cameraPreviewLayer(in: geometry)

                // Grid overlay
                if showGrid {
                    GridOverlayView()
                        .allowsHitTesting(false)
                }

                // Focus ring
                if showFocusRing, let point = focusPoint {
                    FocusRingView(point: point)
                        .allowsHitTesting(false)
                }

                // Shutter flash
                if showShutterFlash {
                    Color.white.opacity(0.7)
                        .ignoresSafeArea()
                        .allowsHitTesting(false)
                        .transition(.opacity)
                }

                // Recording indicator
                if cameraService.isRecording {
                    recordingIndicator
                        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
                        .padding(.top, 60)
                }

                // Zoom factor display
                if cameraService.currentZoomFactor > 1.05 {
                    zoomBadge
                        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
                        .padding(.top, 56)
                }

                // Status badges (GPS, RAW)
                statusBadges
                    .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topTrailing)
                    .padding(.top, 56)
                    .padding(.trailing, 16)

                // Layout-specific controls
                if isLandscape {
                    landscapeLayout(geometry: geometry)
                } else {
                    portraitLayout(geometry: geometry)
                }
            }
        }
        .onAppear {
            loadLastThumbnail()
        }
    }

    // MARK: - Camera Preview Layer

    private func cameraPreviewLayer(in geometry: GeometryProxy) -> some View {
        CameraPreviewUIView(session: cameraService.session)
            .ignoresSafeArea()
            .contentShape(Rectangle())
            .onTapGesture(count: 1) { location in
                handleTapToFocus(at: location, in: geometry)
            }
            .gesture(
                MagnificationGesture()
                    .onChanged { scale in
                        cameraService.zoom(by: scale)
                    }
            )
            .simultaneousGesture(
                DragGesture(minimumDistance: 20)
                    .onChanged { value in
                        // Horizontal drag zoom: right = zoom in, left = zoom out
                        let delta = value.translation.width / 800.0
                        cameraService.zoom(by: 1.0 + delta)
                    }
            )
    }

    // MARK: - Portrait Layout

    private func portraitLayout(geometry: GeometryProxy) -> some View {
        VStack(spacing: 0) {
            // Top bar
            topBar
                .padding(.horizontal, 16)
                .padding(.top, 8)

            Spacer()

            // Exposure slider (vertical, right side)
            if showExposureSlider {
                HStack {
                    Spacer()
                    exposureSlider(isLandscape: false)
                        .padding(.trailing, 12)
                }
            }

            Spacer()

            // Bottom controls
            VStack(spacing: 12) {
                zoomPresets
                bottomControls
            }
            .padding(.bottom, 16)
            .padding(.bottom, geometry.safeAreaInsets.bottom)
        }
    }

    // MARK: - Landscape Layout

    private func landscapeLayout(geometry: GeometryProxy) -> some View {
        HStack(spacing: 0) {
            // Top-left: flash, grid, exposure, location
            VStack {
                topBar
                    .padding(.horizontal, 12)
                    .padding(.top, 8)
                Spacer()
            }

            Spacer()

            // Bottom: exposure slider horizontal
            if showExposureSlider {
                VStack {
                    Spacer()
                    exposureSlider(isLandscape: true)
                        .padding(.bottom, 12)
                }
            }

            Spacer()

            // Right side controls
            VStack(spacing: 16) {
                // Zoom presets (vertical)
                VStack(spacing: 6) {
                    ForEach(ZoomPreset.defaults) { preset in
                        zoomPresetButton(preset: preset)
                    }
                }

                Divider_()

                // Mode selector (vertical)
                ForEach(CaptureMode.allCases) { mode in
                    modeButton(mode: mode, vertical: true)
                }

                Divider_()

                // Shutter
                shutterButton

                Divider_()

                // Lens cycle
                if cameraService.backCameraCount > 1 {
                    circleIconButton(systemName: "camera.aperture", tint: CameraTokens.swiftAccentGreen) {
                        onCycleLens()
                    }
                }

                // Switch camera
                circleIconButton(systemName: "camera.rotate.fill", tint: CameraTokens.swiftTextPrimary) {
                    onSwitchCamera()
                }
            }
            .padding(.trailing, 12)
            .padding(.vertical, 16)
        }
    }

    // MARK: - Top Bar

    private var topBar: some View {
        HStack {
            // Flash toggle
            circleIconButton(systemName: flashMode.iconName, tint: flashMode == .off ? CameraTokens.swiftAccentDim : CameraTokens.swiftTextMuted) {
                flashMode.cycle()
            }

            Spacer()

            HStack(spacing: 6) {
                // Grid toggle
                circleIconButton(systemName: "grid", tint: showGrid ? CameraTokens.swiftTextPrimary : CameraTokens.swiftTextMuted) {
                    showGrid.toggle()
                }

                // Exposure toggle
                circleIconButton(systemName: "sun.max.fill", tint: CameraTokens.swiftTextMuted) {
                    withAnimation(.easeInOut(duration: 0.2)) {
                        showExposureSlider.toggle()
                    }
                }

                // Location toggle
                circleIconButton(systemName: "location.fill", tint: enableLocation ? CameraTokens.swiftTextPrimary : CameraTokens.swiftTextMuted) {
                    if enableLocation {
                        enableLocation = false
                        locationService.disable()
                    } else {
                        enableLocation = true
                        locationService.enable()
                    }
                }

                // RAW toggle
                if cameraService.isRawSupported {
                    Button {
                        enableRaw.toggle()
                    } label: {
                        Text("R")
                            .font(.system(size: 11, weight: .bold))
                            .foregroundColor(enableRaw ? CameraTokens.swiftTextPrimary : CameraTokens.swiftTextMuted)
                            .frame(width: 36, height: 36)
                            .background(Circle().fill(CameraTokens.swiftSurface2.opacity(0.6)))
                    }
                }
            }
        }
    }

    // MARK: - Bottom Controls

    private var bottomControls: some View {
        HStack {
            // Gallery thumbnail
            Button {
                // Open Photos app
                if let url = URL(string: "photos-redirect://") {
                    UIApplication.shared.open(url)
                }
            } label: {
                ZStack {
                    RoundedRectangle(cornerRadius: 10)
                        .fill(CameraTokens.swiftSurface2)
                        .frame(width: 42, height: 42)

                    if let thumbnail = lastPhotoThumbnail {
                        Image(uiImage: thumbnail)
                            .resizable()
                            .aspectRatio(contentMode: .fill)
                            .frame(width: 42, height: 42)
                            .clipShape(RoundedRectangle(cornerRadius: 10))
                    } else {
                        Image(systemName: "photo.on.rectangle")
                            .font(.system(size: 20))
                            .foregroundColor(CameraTokens.swiftTextMuted)
                    }
                }
            }

            Spacer()

            // Mode selector
            HStack(spacing: 20) {
                ForEach(CaptureMode.allCases) { mode in
                    modeButton(mode: mode, vertical: false)
                }
            }

            Spacer()

            // Shutter + Camera Switch
            HStack(spacing: 12) {
                // Lens cycle button
                if cameraService.backCameraCount > 1 {
                    circleIconButton(systemName: "camera.aperture", tint: CameraTokens.swiftAccentGreen, size: 38) {
                        onCycleLens()
                    }
                }

                // Switch camera
                circleIconButton(systemName: "camera.rotate.fill", tint: CameraTokens.swiftTextPrimary, size: 42) {
                    onSwitchCamera()
                }
            }
        }
        .padding(.horizontal, 32)
    }

    // MARK: - Mode Button

    private func modeButton(mode: CaptureMode, vertical: Bool) -> some View {
        Button {
            if cameraService.isRecording && mode != .video {
                cameraService.stopRecording()
            }
            selectedMode = mode
        } label: {
            VStack(spacing: 3) {
                Text(mode.displayName)
                    .font(.system(size: 12, weight: selectedMode == mode ? .bold : .medium))
                    .foregroundColor(selectedMode == mode ? CameraTokens.swiftTextPrimary : CameraTokens.swiftTextMuted)
                    .tracking(0.5)

                if selectedMode == mode {
                    Rectangle()
                        .fill(CameraTokens.swiftTextPrimary)
                        .frame(width: 20, height: 2)
                        .cornerRadius(1)
                } else {
                    Rectangle()
                        .fill(Color.clear)
                        .frame(width: 20, height: 2)
                }
            }
        }
    }

    // MARK: - Zoom Presets

    private var zoomPresets: some View {
        HStack(spacing: 8) {
            ForEach(ZoomPreset.defaults) { preset in
                zoomPresetButton(preset: preset)
            }
        }
    }

    private func zoomPresetButton(preset: ZoomPreset) -> some View {
        let isActive = abs(cameraService.currentZoomFactor - preset.factor) < 0.15
        return Button {
            cameraService.setZoom(factor: preset.factor)
        } label: {
            Text(preset.label)
                .font(.system(size: 11, weight: .semibold))
                .foregroundColor(isActive ? CameraTokens.swiftBg : CameraTokens.swiftTextMuted)
                .frame(width: 36, height: 36)
                .background(Circle().fill(isActive ? CameraTokens.swiftTextPrimary : CameraTokens.swiftSurface2.opacity(0.7)))
        }
    }

    // MARK: - Shutter Button

    private var shutterButton: some View {
        Button {
            onShutter()
        } label: {
            ZStack {
                // Outer ring
                Circle()
                    .strokeBorder(selectedMode == .video && !cameraService.isRecording ? CameraTokens.swiftDanger : CameraTokens.swiftTextPrimary, lineWidth: 3)
                    .frame(width: 72, height: 72)

                // Inner fill
                if selectedMode == .video {
                    if cameraService.isRecording {
                        RoundedRectangle(cornerRadius: 6)
                            .fill(CameraTokens.swiftDanger)
                            .frame(width: 28, height: 28)
                    } else {
                        Circle()
                            .fill(CameraTokens.swiftDanger)
                            .frame(width: 60, height: 60)
                    }
                } else {
                    Circle()
                        .fill(CameraTokens.swiftTextPrimary)
                        .frame(width: 60, height: 60)
                }
            }
        }
    }

    // MARK: - Exposure Slider

    private func exposureSlider(isLandscape: Bool) -> some View {
        Group {
            if isLandscape {
                HStack(spacing: 8) {
                    Image(systemName: "sun.max.fill")
                        .font(.system(size: 16))
                        .foregroundColor(CameraTokens.swiftTextMuted)

                    Slider(value: $cameraService.exposureCompensation, in: -1...1)
                        .frame(width: 280)
                        .tint(CameraTokens.swiftTextPrimary)
                        .onChange(of: cameraService.exposureCompensation) { newValue in
                            cameraService.setExposure(normalized: newValue)
                        }

                    Text(String(format: "%.1f", cameraService.exposureCompensation))
                        .font(.system(size: 11, weight: .medium))
                        .foregroundColor(CameraTokens.swiftTextMuted)
                        .frame(width: 30)
                }
                .frame(height: 40)
            } else {
                VStack(spacing: 6) {
                    Text(String(format: "%.1f", cameraService.exposureCompensation))
                        .font(.system(size: 10, weight: .medium))
                        .foregroundColor(CameraTokens.swiftTextMuted)

                    // Vertical slider using rotation
                    Slider(value: $cameraService.exposureCompensation, in: -1...1)
                        .rotationEffect(.degrees(-90))
                        .frame(width: 250, height: 44)
                        .tint(CameraTokens.swiftTextPrimary)
                        .onChange(of: cameraService.exposureCompensation) { newValue in
                            cameraService.setExposure(normalized: newValue)
                        }

                    Image(systemName: "sun.max.fill")
                        .font(.system(size: 16))
                        .foregroundColor(CameraTokens.swiftTextMuted)
                }
                .frame(width: 44, height: 250)
            }
        }
    }

    // MARK: - Recording Indicator

    private var recordingIndicator: some View {
        HStack(spacing: 8) {
            Circle()
                .fill(CameraTokens.swiftDanger)
                .frame(width: 8, height: 8)

            Text(formatDuration(cameraService.recordingDuration))
                .font(.system(size: 13, weight: .semibold))
                .foregroundColor(CameraTokens.swiftTextPrimary)
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 6)
        .background(
            RoundedRectangle(cornerRadius: 14)
                .fill(Color.black.opacity(0.8))
        )
    }

    // MARK: - Zoom Badge

    private var zoomBadge: some View {
        Text(String(format: "%.1fx", cameraService.currentZoomFactor))
            .font(.system(size: 13, weight: .semibold))
            .foregroundColor(CameraTokens.swiftTextPrimary)
            .padding(.horizontal, 10)
            .padding(.vertical, 4)
            .background(
                RoundedRectangle(cornerRadius: 10)
                    .fill(CameraTokens.swiftSurface2.opacity(0.6))
            )
    }

    // MARK: - Status Badges

    private var statusBadges: some View {
        VStack(spacing: 4) {
            // GPS badge
            if enableLocation {
                HStack(spacing: 4) {
                    Image(systemName: "location.fill")
                        .font(.system(size: 12))
                        .foregroundColor(locationBadgeColor)

                    Text(locationBadgeText)
                        .font(.system(size: 10, weight: .medium))
                        .foregroundColor(locationBadgeColor)
                }
                .padding(.horizontal, 8)
                .padding(.vertical, 3)
                .background(
                    RoundedRectangle(cornerRadius: 10)
                        .fill(CameraTokens.swiftSurface2.opacity(0.6))
                )
            }

            // RAW badge
            if enableRaw && cameraService.isRawSupported {
                HStack(spacing: 4) {
                    Image(systemName: "camera.aperture")
                        .font(.system(size: 12))
                        .foregroundColor(CameraTokens.swiftTextMuted)

                    Text("RAW")
                        .font(.system(size: 10, weight: .medium))
                        .foregroundColor(CameraTokens.swiftTextMuted)
                }
                .padding(.horizontal, 8)
                .padding(.vertical, 3)
                .background(
                    RoundedRectangle(cornerRadius: 10)
                        .fill(CameraTokens.swiftSurface2.opacity(0.6))
                )
            }
        }
    }

    private var locationBadgeColor: Color {
        switch locationService.status {
        case .acquired:
            return CameraTokens.swiftAccentGreen
        default:
            return CameraTokens.swiftTextMuted
        }
    }

    private var locationBadgeText: String {
        switch locationService.status {
        case .acquired:
            return "GPS ✓"
        case .locating:
            return "定位中..."
        case .disabled:
            return "GPS"
        }
    }

    // MARK: - Helpers

    private func handleTapToFocus(at point: CGPoint, in geometry: GeometryProxy) {
        guard !cameraService.isRecording else { return }

        focusPoint = point
        showFocusRing = true
        cameraService.focus(at: point, in: geometry.size)

        // Hide focus ring after 1.2s
        DispatchQueue.main.asyncAfter(deadline: .now() + 1.2) {
            withAnimation(.easeOut(duration: 0.3)) {
                showFocusRing = false
            }
        }
    }

    private func loadLastThumbnail() {
        Task {
            let thumbnail = await PhotoSaver.fetchLastPhotoThumbnail()
            await MainActor.run {
                lastPhotoThumbnail = thumbnail
            }
        }
    }

    private func formatDuration(_ duration: TimeInterval) -> String {
        let minutes = Int(duration) / 60
        let seconds = Int(duration) % 60
        return String(format: "%d:%02d", minutes, seconds)
    }

    // MARK: - Reusable Components

    private func circleIconButton(systemName: String, tint: Color, size: CGFloat = 36, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Image(systemName: systemName)
                .font(.system(size: size * 0.5))
                .foregroundColor(tint)
                .frame(width: size, height: size)
                .background(Circle().fill(CameraTokens.swiftSurface2.opacity(0.6)))
        }
    }
}

// MARK: - Grid Overlay

struct GridOverlayView: View {
    var body: some View {
        GeometryReader { geometry in
            let w = geometry.size.width
            let h = geometry.size.height

            Path { path in
                // Vertical lines
                path.move(to: CGPoint(x: w / 3, y: 0))
                path.addLine(to: CGPoint(x: w / 3, y: h))
                path.move(to: CGPoint(x: w * 2 / 3, y: 0))
                path.addLine(to: CGPoint(x: w * 2 / 3, y: h))
                // Horizontal lines
                path.move(to: CGPoint(x: 0, y: h / 3))
                path.addLine(to: CGPoint(x: w, y: h / 3))
                path.move(to: CGPoint(x: 0, y: h * 2 / 3))
                path.addLine(to: CGPoint(x: w, y: h * 2 / 3))
            }
            .stroke(CameraTokens.swiftGridColor, lineWidth: 0.5)
        }
        .ignoresSafeArea()
    }
}

// MARK: - Focus Ring

struct FocusRingView: View {
    let point: CGPoint
    @State private var opacity: Double = 1.0

    var body: some View {
        Circle()
            .strokeBorder(Color.white.opacity(opacity), lineWidth: 2)
            .frame(width: 80, height: 80)
            .position(point)
            .onAppear {
                withAnimation(.easeOut(duration: 1.2)) {
                    opacity = 0.0
                }
            }
    }
}

// MARK: - Divider (vertical in landscape)

private struct Divider_: View {
    var body: some View {
        Rectangle()
            .fill(CameraTokens.swiftAccentDim.opacity(0.3))
            .frame(width: 24, height: 1)
    }
}

// MARK: - Camera Preview UIViewRepresentable

struct CameraPreviewUIView: UIViewRepresentable {
    let session: AVCaptureSession

    func makeUIView(context: Context) -> PreviewUIView {
        let view = PreviewUIView()
        view.previewLayer.session = session
        view.previewLayer.videoGravity = .resizeAspectFill
        return view
    }

    func updateUIView(_ uiView: PreviewUIView, context: Context) {
        uiView.previewLayer.frame = uiView.bounds
    }
}

/// Custom UIView that lays out its preview layer properly
class PreviewUIView: UIView {
    override class var layerClass: AnyClass {
        AVCaptureVideoPreviewLayer.self
    }

    var previewLayer: AVCaptureVideoPreviewLayer {
        layer as! AVCaptureVideoPreviewLayer
    }

    override func layoutSubviews() {
        super.layoutSubviews()
        previewLayer.frame = bounds
    }
}