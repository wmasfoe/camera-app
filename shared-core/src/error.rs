use thiserror::Error;

/// 相机应用统一错误类型
/// UniFFI 会把这个映射为 Kotlin enum / Swift enum
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

    #[error("Unknown error: {message}")]
    Unknown { message: String },
}

impl CameraError {
    pub fn unknown(msg: impl Into<String>) -> Self {
        Self::Unknown {
            message: msg.into(),
        }
    }
}
