import AVFoundation
import UIKit
import Combine

/// Full-featured camera service — manages AVCaptureSession, photo capture, video recording,
/// zoom, focus, exposure, flash, and multi-camera support.
class CameraService: NSObject, ObservableObject {

    // MARK: - Published State

    @Published var isAuthorized = false
    @Published var isSessionRunning = false
    @Published var capturedImageData: Data?
    @Published var isCapturing = false

    // Camera capabilities
    @Published var backCameraCount: Int = 1
    @Published var isRawSupported: Bool = false
    @Published var currentZoomFactor: CGFloat = 1.0
    @Published var minZoomFactor: CGFloat = 1.0
    @Published var maxZoomFactor: CGFloat = 10.0
    @Published var exposureCompensation: Float = 0.0
    @Published var exposureRange: ClosedRange<Float> = -2.0...2.0

    // Video
    @Published var isRecording = false
    @Published var recordingDuration: TimeInterval = 0

    // MARK: - Session & Devices

    let session = AVCaptureSession()
    private var videoInput: AVCaptureDeviceInput?
    private var currentPosition: AVCaptureDevice.Position = .back
    private var currentDevice: AVCaptureDevice?
    private let photoOutput = AVCapturePhotoOutput()
    private let movieOutput = AVCaptureMovieFileOutput()
    private var videoRecordingURL: URL?
    private var recordingTimer: Timer?

    // Completion handlers
    private var photoCompletion: ((Data?) -> Void)?

    // MARK: - Initialization

    override init() {
        super.init()
    }

    // MARK: - Permission

    func checkPermission() {
        switch AVCaptureDevice.authorizationStatus(for: .video) {
        case .authorized:
            isAuthorized = true
            configureSession()
        case .notDetermined:
            requestPermission()
        default:
            isAuthorized = false
        }
    }

    func requestPermission() {
        AVCaptureDevice.requestAccess(for: .video) { [weak self] granted in
            DispatchQueue.main.async {
                self?.isAuthorized = granted
                if granted {
                    self?.configureSession()
                }
            }
        }
    }

    // MARK: - Session Configuration

    private func configureSession() {
        session.beginConfiguration()

        // Session preset
        if session.canSetSessionPreset(.photo) {
            session.sessionPreset = .photo
        }

        // Remove existing inputs
        for input in session.inputs {
            session.removeInput(input)
        }

        // Remove existing outputs
        for output in session.outputs {
            session.removeOutput(output)
        }

        // Configure back camera
        guard let device = AVCaptureDevice.default(.builtInWideAngleCamera, for: .video, position: currentPosition) else {
            session.commitConfiguration()
            return
        }

        currentDevice = device

        do {
            let input = try AVCaptureDeviceInput(device: device)
            if session.canAddInput(input) {
                session.addInput(input)
                videoInput = input
            }
        } catch {
            print("Camera input error: \(error)")
            session.commitConfiguration()
            return
        }

        // Photo output
        photoOutput.isHighResolutionCaptureEnabled = true
        if session.canAddOutput(photoOutput) {
            session.addOutput(photoOutput)
        }

        // Movie output
        if session.canAddOutput(movieOutput) {
            session.addOutput(movieOutput)
        }

        // Zoom range
        updateZoomRange(for: device)

        session.commitConfiguration()

        // Start session on background thread
        DispatchQueue.global(qos: .userInitiated).async { [weak self] in
            self?.session.startRunning()
            DispatchQueue.main.async {
                self?.isSessionRunning = true
            }
        }

        // Count back cameras
        enumerateBackCameras()
    }

    /// Count available back cameras (wide, ultra-wide, telephoto)
    private func enumerateBackCameras() {
        var count = 0
        // Wide angle (default)
        if AVCaptureDevice.default(.builtInWideAngleCamera, for: .video, position: .back) != nil {
            count += 1
        }
        // Ultra wide
        if AVCaptureDevice.default(.builtInUltraWideCamera, for: .video, position: .back) != nil {
            count += 1
        }
        // Telephoto
        if AVCaptureDevice.default(.builtInTelephotoCamera, for: .video, position: .back) != nil {
            count += 1
        }
        backCameraCount = max(count, 1)
    }

    // MARK: - Zoom

    private func updateZoomRange(for device: AVCaptureDevice) {
        minZoomFactor = device.minAvailableVideoZoomFactor
        maxZoomFactor = min(device.maxAvailableVideoZoomFactor, 10.0)
        currentZoomFactor = min(max(currentZoomFactor, minZoomFactor), maxZoomFactor)
    }

    /// Set zoom to a specific factor
    func setZoom(factor: CGFloat) {
        guard let device = currentDevice else { return }
        let clamped = min(max(factor, minZoomFactor), maxZoomFactor)
        do {
            try device.lockForConfiguration()
            device.videoZoomFactor = clamped
            device.unlockForConfiguration()
        } catch {
            print("Zoom error: \(error)")
        }
        currentZoomFactor = clamped
    }

    /// Adjust zoom by a multiplier (for pinch/pan gestures)
    func zoom(by multiplier: CGFloat) {
        setZoom(factor: currentZoomFactor * multiplier)
    }

    // MARK: - Focus

    /// Focus at a point in the preview layer coordinate space
    func focus(at point: CGPoint, in previewSize: CGSize) {
        guard let device = currentDevice, device.isFocusPointOfInterestSupported else { return }

        let focusPoint = CGPoint(x: point.x / previewSize.width, y: point.y / previewSize.height)

        do {
            try device.lockForConfiguration()
            device.focusPointOfInterest = focusPoint
            device.focusMode = .autoFocus
            if device.isExposurePointOfInterestSupported {
                device.exposurePointOfInterest = focusPoint
                device.exposureMode = .autoExpose
            }
            device.unlockForConfiguration()
        } catch {
            print("Focus error: \(error)")
        }

        // Reset to continuous after 3 seconds
        DispatchQueue.main.asyncAfter(deadline: .now() + 3) { [weak self] in
            guard let device = self?.currentDevice else { return }
            do {
                try device.lockForConfiguration()
                if device.isFocusModeSupported(.continuousAutoFocus) {
                    device.focusMode = .continuousAutoFocus
                }
                if device.isExposureModeSupported(.continuousAutoExposure) {
                    device.exposureMode = .continuousAutoExposure
                }
                device.unlockForConfiguration()
            } catch {}
        }
    }

    // MARK: - Exposure

    /// Set exposure compensation (-1.0 to 1.0 normalized, mapped to device range)
    func setExposure(normalized value: Float) {
        guard let device = currentDevice else { return }
        let range = device.minExposureTargetBias...device.maxExposureTargetBias
        let half = (range.upperBound + range.lowerBound) / 2; let halfDiff = (range.upperBound - range.lowerBound) / 2; let index = value * halfDiff + half
        let clampedIndex = min(max(index, range.lowerBound), range.upperBound)

        do {
            try device.lockForConfiguration()
            device.setExposureTargetBias(clampedIndex) { _ in }
            device.unlockForConfiguration()
        } catch {}

        exposureCompensation = value
    }

    /// Get current exposure range info
    func updateExposureRange() {
        guard let device = currentDevice else { return }
        let minEV = device.minExposureTargetBias
        let maxEV = device.maxExposureTargetBias
        exposureRange = Float(minEV)...Float(maxEV)
    }

    // MARK: - Flash

    /// Set torch mode for video recording
    func setTorch(mode: AVCaptureDevice.TorchMode) {
        guard let device = currentDevice, device.hasTorch else { return }
        do {
            try device.lockForConfiguration()
            device.torchMode = mode
            device.unlockForConfiguration()
        } catch {}
    }

    // MARK: - Photo Capture

    /// Capture a photo. Returns JPEG data via completion handler.
    func capturePhoto(flashMode: FlashMode = .auto, completion: @escaping (Data?) -> Void) {
        photoCompletion = completion
        isCapturing = true

        var settings = AVCapturePhotoSettings()

        // Use HEIF if available, else JPEG
        if photoOutput.availablePhotoCodecTypes.contains(.hevc) {
            settings = AVCapturePhotoSettings(format: [AVVideoCodecKey: AVVideoCodecType.hevc])
        }

        // Flash
        if photoOutput.supportedFlashModes.contains(flashMode.avFlashMode) {
            settings.flashMode = flashMode.avFlashMode
        }

        settings.isHighResolutionPhotoEnabled = true

        // Enable lens correction if available
        if #available(iOS 17.0, *) {
            // Lens stabilization not available on all devices
        }

        photoOutput.capturePhoto(with: settings, delegate: self)
    }

    // MARK: - Video Recording

    /// Start video recording
    func startRecording() {
        guard !movieOutput.isRecording else { return }

        // Request audio permission and add audio input
        addAudioInputIfNeeded()

        let tempDir = FileManager.default.temporaryDirectory
        let fileName = "VID_\(DateFormatter.fileNameFormatter.string(from: Date())).mp4"
        let fileURL = tempDir.appendingPathComponent(fileName)
        videoRecordingURL = fileURL

        let connection = movieOutput.connection(with: .video)
        if connection?.isVideoOrientationSupported == true {
            connection?.videoOrientation = .portrait
        }

        // Enable video stabilization
        if connection?.isVideoStabilizationSupported == true {
            connection?.preferredVideoStabilizationMode = .auto
        }

        movieOutput.startRecording(to: fileURL, recordingDelegate: self)
        isRecording = true

        // Start duration timer
        recordingDuration = 0
        recordingTimer = Timer.scheduledTimer(withTimeInterval: 1.0, repeats: true) { [weak self] _ in
            self?.recordingDuration += 1
        }
    }

    /// Stop video recording
    func stopRecording() {
        guard movieOutput.isRecording else { return }
        movieOutput.stopRecording()
        isRecording = false
        recordingTimer?.invalidate()
        recordingTimer = nil
        removeAudioInputIfNeeded()
    }

    // MARK: - Camera Switching

    /// Switch between front and back camera
    func switchCamera() {
        currentPosition = currentPosition == .back ? .front : .back
        currentZoomFactor = 1.0
        exposureCompensation = 0.0

        session.beginConfiguration()

        // Remove current input
        if let currentInput = videoInput {
            session.removeInput(currentInput)
        }

        // Add new input
        guard let device = AVCaptureDevice.default(.builtInWideAngleCamera, for: .video, position: currentPosition) else {
            session.commitConfiguration()
            return
        }

        currentDevice = device

        do {
            let newInput = try AVCaptureDeviceInput(device: device)
            if session.canAddInput(newInput) {
                session.addInput(newInput)
                videoInput = newInput
            }
        } catch {
            print("Camera switch error: \(error)")
        }

        // Update zoom range
        updateZoomRange(for: device)

        session.commitConfiguration()
    }

    /// Cycle through back cameras (wide → ultra-wide → telephoto)
    func cycleLens() {
        guard currentPosition == .back, backCameraCount > 1 else { return }

        // Determine current device type
        let currentType = currentDevice?.deviceType
        let nextType: AVCaptureDevice.DeviceType

        switch currentType {
        case .builtInWideAngleCamera:
            if AVCaptureDevice.default(.builtInUltraWideCamera, for: .video, position: .back) != nil {
                nextType = .builtInUltraWideCamera
            } else if AVCaptureDevice.default(.builtInTelephotoCamera, for: .video, position: .back) != nil {
                nextType = .builtInTelephotoCamera
            } else {
                return
            }
        case .builtInUltraWideCamera:
            if AVCaptureDevice.default(.builtInTelephotoCamera, for: .video, position: .back) != nil {
                nextType = .builtInTelephotoCamera
            } else {
                nextType = .builtInWideAngleCamera
            }
        case .builtInTelephotoCamera:
            nextType = .builtInWideAngleCamera
        default:
            nextType = .builtInWideAngleCamera
        }

        guard let device = AVCaptureDevice.default(nextType, for: .video, position: .back) else { return }

        currentZoomFactor = 1.0
        exposureCompensation = 0.0

        session.beginConfiguration()

        if let currentInput = videoInput {
            session.removeInput(currentInput)
        }

        currentDevice = device

        do {
            let newInput = try AVCaptureDeviceInput(device: device)
            if session.canAddInput(newInput) {
                session.addInput(newInput)
                videoInput = newInput
            }
        } catch {
            print("Lens switch error: \(error)")
        }

        updateZoomRange(for: device)
        session.commitConfiguration()
    }

    // MARK: - Audio

    private func addAudioInputIfNeeded() {
        // Check if audio input already exists
        for input in session.inputs {
            if let deviceInput = input as? AVCaptureDeviceInput, deviceInput.device.hasMediaType(.audio) {
                return
            }
        }

        guard let audioDevice = AVCaptureDevice.default(for: .audio) else { return }
        do {
            let audioInput = try AVCaptureDeviceInput(device: audioDevice)
            if session.canAddInput(audioInput) {
                session.addInput(audioInput)
            }
        } catch {}
    }

    private func removeAudioInputIfNeeded() {
        for input in session.inputs {
            if let deviceInput = input as? AVCaptureDeviceInput, deviceInput.device.hasMediaType(.audio) {
                session.removeInput(input)
            }
        }
    }

    // MARK: - Processing State

    func markProcessingDone() {
        isCapturing = false
    }
}

// MARK: - AVCapturePhotoCaptureDelegate

extension CameraService: AVCapturePhotoCaptureDelegate {
    func photoOutput(
        _ output: AVCapturePhotoOutput,
        didFinishProcessingPhoto photo: AVCapturePhoto,
        error: Error?
    ) {
        if let error = error {
            print("Photo capture error: \(error)")
            DispatchQueue.main.async { [weak self] in
                self?.photoCompletion?(nil)
                self?.isCapturing = false
            }
            return
        }

        guard let data = photo.fileDataRepresentation() else {
            DispatchQueue.main.async { [weak self] in
                self?.photoCompletion?(nil)
                self?.isCapturing = false
            }
            return
        }

        DispatchQueue.main.async { [weak self] in
            self?.capturedImageData = data
            self?.photoCompletion?(data)
        }
    }
}

// MARK: - AVCaptureFileOutputRecordingDelegate

extension CameraService: AVCaptureFileOutputRecordingDelegate {
    func fileOutput(
        _ output: AVCaptureFileOutput,
        didFinishRecordingTo outputFileURL: URL,
        from connections: [AVCaptureConnection],
        error: Error?
    ) {
        if let error = error {
            print("Video recording error: \(error)")
            return
        }

        // Save to Photos library
        Task {
            let saved = await PhotoSaver.saveVideo(at: outputFileURL)
            await MainActor.run {
                if saved {
                    print("Video saved to Photos")
                }
            }
            // Clean up temp file
            try? FileManager.default.removeItem(at: outputFileURL)
        }
    }
}

// MARK: - DateFormatter Extension

private extension DateFormatter {
    static let fileNameFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.dateFormat = "yyyyMMdd_HHmmss"
        formatter.locale = Locale(identifier: "en_US_POSIX")
        return formatter
    }()
}