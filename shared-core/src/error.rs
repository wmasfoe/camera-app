use thiserror::Error;

/// 相机应用统一错误类型
#[derive(Debug, Error)]
pub enum CameraError {
    #[error("Camera device not found")]
    DeviceNotFound,

    #[error("Camera permission denied")]
    PermissionDenied,

    #[error("Photo/video capture failed")]
    CaptureFailed,

    #[error("Image processing failed")]
    ProcessingFailed,

    #[error("Invalid camera configuration")]
    InvalidConfig,

    #[error("Storage operation failed")]
    StorageError,

    #[error("Unknown error occurred")]
    Unknown,
}
