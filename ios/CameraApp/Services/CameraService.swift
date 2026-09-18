import AVFoundation
import SwiftUI

/// 相机服务 — 管理 AVCaptureSession + 拍照
class CameraService: NSObject, ObservableObject {
    @Published var isAuthorized = false
    @Published var capturedData: Data?

    let session = AVCaptureSession()
    private let photoOutput = AVCapturePhotoOutput()
    private var currentPosition: AVCaptureDevice.Position = .back
    private var captureCompletion: ((Data?) -> Void)?

    override init() {
        super.init()
    }

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

    private func configureSession() {
        session.beginConfiguration()
        defer { session.commitConfiguration() }

        // 添加视频输入
        guard let device = AVCaptureDevice.default(
            .builtInWideAngleCamera,
            for: .video,
            position: currentPosition
        ),
        let input = try? AVCaptureDeviceInput(device: device) else {
            return
        }

        if session.canAddInput(input) {
            session.addInput(input)
        }

        // 添加照片输出
        if session.canAddOutput(photoOutput) {
            session.addOutput(photoOutput)
            photoOutput.isHighResolutionCaptureEnabled = true
            // 确保输出 JPEG
            if photoOutput.availablePhotoCodecTypes.contains(.jpeg) {
                // 设置将在拍照时指定
            }
        }

        // 设置预设
        if session.canSetSessionPreset(.photo) {
            session.sessionPreset = .photo
        }

        // 在后台线程启动
        DispatchQueue.global(qos: .userInitiated).async { [weak self] in
            self?.session.startRunning()
        }
    }

    /// 拍照，返回 JPEG Data
    func capturePhoto(completion: @escaping (Data?) -> Void) {
        captureCompletion = completion

        let settings = AVCapturePhotoSettings()

        // 强制 JPEG 输出
        if photoOutput.availablePhotoCodecTypes.contains(.jpeg) {
            settings.previewPhotoFormat = [
                AVVideoCodecKey: AVVideoCodecType.jpeg
            ]
        }

        // 启用高分辨率
        settings.isHighResolutionPhotoEnabled = true

        photoOutput.capturePhoto(with: settings, delegate: self)
    }

    func switchCamera() {
        currentPosition = currentPosition == .back ? .front : .back
        session.beginConfiguration()
        session.inputs.forEach { session.removeInput($0) }
        configureSession()
    }
}

// MARK: - AVCapturePhotoDelegate

extension CameraService: AVCapturePhotoCaptureDelegate {
    func photoOutput(
        _ output: AVCapturePhotoOutput,
        didFinishProcessingPhoto photo: AVCapturePhoto,
        error: Error?
    ) {
        if let error = error {
            print("拍照失败: \(error)")
            captureCompletion?(nil)
            return
        }

        // 获取 JPEG 数据
        guard let data = photo.fileDataRepresentation() else {
            captureCompletion?(nil)
            return
        }

        DispatchQueue.main.async { [weak self] in
            self?.capturedData = data
            self?.captureCompletion?(data)
        }
    }
}