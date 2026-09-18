import AVFoundation
import SwiftUI

/// 相机服务 — 管理 AVCaptureSession 生命周期
class CameraService: ObservableObject {
    @Published var isAuthorized = false

    let session = AVCaptureSession()
    private var currentPosition: AVCaptureDevice.Position = .back

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
        guard let device = AVCaptureDevice.default(.builtInWideAngleCamera, for: .video, position: currentPosition),
              let input = try? AVCaptureDeviceInput(device: device) else {
            return
        }

        if session.canAddInput(input) {
            session.addInput(input)
        }

        // 设置预设
        if session.canSetSessionPreset(.high) {
            session.sessionPreset = .high
        }

        // 在后台线程启动
        DispatchQueue.global(qos: .userInitiated).async { [weak self] in
            self?.session.startRunning()
        }
    }

    func switchCamera() {
        currentPosition = currentPosition == .back ? .front : .back
        session.beginConfiguration()
        session.inputs.forEach { session.removeInput($0) }
        configureSession()
    }

    func capturePhoto() {
        // TODO: 接入 AVCapturePhotoOutput
    }
}