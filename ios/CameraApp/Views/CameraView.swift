import SwiftUI
import PhotosUI

struct CameraView: View {
    @StateObject private var cameraService = CameraService()

    @State private var showPermissionAlert = false
    @State private var processedImage: UIImage?
    @State private var isProcessing = false
    @State private var showSaveAlert = false
    @State private var saveMessage = ""

    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()

            if cameraService.isAuthorized {
                if let image = processedImage {
                    // 显示处理后的图片
                    Image(uiImage: image)
                        .resizable()
                        .aspectRatio(contentMode: .fit)
                        .ignoresSafeArea()

                    if isProcessing {
                        ProgressView()
                            .progressViewStyle(CircularProgressViewStyle(tint: .white))
                            .scaleEffect(1.5)
                    }

                    // 底部操作栏
                    VStack {
                        Spacer()

                        HStack {
                            // 重拍按钮
                            Button("重拍") {
                                processedImage = nil
                                cameraService.capturedData = nil
                            }
                            .foregroundColor(.white)
                            .padding()
                            .background(Color.gray.opacity(0.6))
                            .cornerRadius(10)

                            Spacer()

                            // 保存按钮
                            Button("保存") {
                                saveToPhotos()
                            }
                            .foregroundColor(.black)
                            .padding()
                            .background(Color.white)
                            .cornerRadius(10)
                        }
                        .padding(.horizontal, 32)
                        .padding(.bottom, 48)
                    }
                } else {
                    // 相机预览
                    CameraPreviewView(session: cameraService.session)
                        .ignoresSafeArea()

                    // 快门按钮
                    VStack {
                        Spacer()

                        Button(action: {
                            takePhoto()
                        }) {
                            Circle()
                                .fill(Color.white)
                                .frame(width: 72, height: 72)
                                .overlay(
                                    Circle()
                                        .stroke(Color.white, lineWidth: 3)
                                        .frame(width: 80, height: 80)
                                )
                        }
                        .padding(.bottom, 48)
                    }
                }
            } else {
                VStack(spacing: 16) {
                    Text("需要相机权限")
                        .font(.title)
                        .foregroundColor(.white)

                    Button("授予权限") {
                        cameraService.requestPermission()
                    }
                    .buttonStyle(.borderedProminent)
                }
            }
        }
        .onAppear {
            cameraService.checkPermission()
        }
        .alert(saveMessage, isPresented: $showSaveAlert) {
            Button("确定", role: .cancel) {}
        }
    }

    private func takePhoto() {
        cameraService.capturePhoto { data in
            guard let jpegData = data else { return }

            isProcessing = true

            // 调用 Rust 处理
            DispatchQueue.global(qos: .userInitiated).async {
                let processedData: Data?
                do {
                    // UniFFI 生成的绑定 — 调用 Rust auto_enhance
                    let processor = ImageProcessor()
                    let result = try processor.autoEnhance(inputImage: jpegData)
                    processedData = Data(result)
                } catch {
                    // 处理失败，用原图
                    processedData = jpegData
                }

                DispatchQueue.main.async {
                    if let data = processedData {
                        processedImage = UIImage(data: data)
                    }
                    isProcessing = false
                }
            }
        }
    }

    private func saveToPhotos() {
        guard let image = processedImage else { return }

        PHPhotoLibrary.requestAuthorization(for: .addOnly) { status in
            guard status == .authorized || status == .limited else {
                DispatchQueue.main.async {
                    saveMessage = "无法访问照片库"
                    showSaveAlert = true
                }
                return
            }

            PHPhotoLibrary.shared().performChanges({
                PHAssetChangeRequest.creationRequestForAsset(from: image)
            }) { success, error in
                DispatchQueue.main.async {
                    if success {
                        saveMessage = "已保存到相册"
                    } else {
                        saveMessage = "保存失败: \(error?.localizedDescription ?? "未知错误")"
                    }
                    showSaveAlert = true
                }
            }
        }
    }
}

// MARK: - 需要 import 的类型（UniFFI 生成后可用）
// 暂时用占位，UniFFI 绑定生成后替换
private struct ImageProcessor {
    func autoEnhance(inputImage: Data) throws -> [UInt8] {
        // TODO: 替换为 UniFFI 生成的 Rust 绑定
        // let result = try camera_shared_core.ImageProcessor().autoEnhance(inputImage: Array(inputImage))
        return Array(inputImage)
    }
}