import SwiftUI
import AVFoundation

struct CameraView: View {
    @StateObject private var cameraService = CameraService()
    @State private var showPermissionAlert = false

    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()

            if cameraService.isAuthorized {
                // 相机预览
                CameraPreviewView(session: cameraService.session)
                    .ignoresSafeArea()

                // 底部控制栏
                VStack {
                    Spacer()

                    HStack {
                        Spacer()

                        // 快门按钮
                        Button(action: {
                            // TODO: 拍照
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

                        Spacer()
                    }
                    .padding(.bottom, 48)
                }
            } else {
                // 无权限提示
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
    }
}

#Preview {
    CameraView()
}