// 数据类型定义 — 与 UniFFI UDL 中的 dictionary/enum 对应

/// 闪光灯模式
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum FlashMode {
    Off,
    On,
    Auto,
    Torch,
}

/// 相机朝向
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum CameraFacing {
    Back,
    Front,
}

/// 滤镜类型
#[derive(Debug, Clone, Copy, PartialEq, Eq, serde::Serialize, serde::Deserialize)]
pub enum FilterType {
    None,
    Vivid,
    Warm,
    Cool,
    Noir,
    Fade,
}

/// 拍摄模式
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum CaptureMode {
    Photo,
    Video,
    Portrait,
    Night,
}

/// 相机配置
#[derive(Debug, Clone)]
pub struct CameraConfig {
    pub width: u32,
    pub height: u32,
    pub fps: u32,
    pub flash: FlashMode,
    pub facing: CameraFacing,
    pub mode: CaptureMode,
    pub enable_hdr: bool,
    pub zoom: f32,
}

impl Default for CameraConfig {
    fn default() -> Self {
        Self {
            width: 1920,
            height: 1080,
            fps: 30,
            flash: FlashMode::Auto,
            facing: CameraFacing::Back,
            mode: CaptureMode::Photo,
            enable_hdr: false,
            zoom: 1.0,
        }
    }
}

/// 拍照结果
#[derive(Debug, Clone)]
pub struct CaptureResult {
    pub file_path: String,
    pub width: u32,
    pub height: u32,
    pub file_size: u64,
    pub timestamp: i64,
    pub is_front_camera: bool,
    pub flash_used: FlashMode,
    pub zoom_used: f32,
}

/// 应用设置
#[derive(Debug, Clone, PartialEq, serde::Serialize, serde::Deserialize)]
pub struct AppSettings {
    pub default_save_path: String,
    pub save_location: bool,
    pub photo_format: String,
    pub jpeg_quality: u32,
    pub mirror_front_camera: bool,
    pub default_filter: FilterType,
    pub shutter_sound: bool,
    pub grid_lines: bool,
    pub location_tag: bool,
}

impl Default for AppSettings {
    fn default() -> Self {
        Self {
            default_save_path: String::new(),
            save_location: true,
            photo_format: "jpeg".to_string(),
            jpeg_quality: 95,
            mirror_front_camera: false,
            default_filter: FilterType::None,
            shutter_sound: true,
            grid_lines: true,
            location_tag: false,
        }
    }
}

/// 处理后的图片信息
#[derive(Debug, Clone)]
pub struct ProcessedInfo {
    pub file_path: String,
    pub width: u32,
    pub height: u32,
    pub file_size: u64,
    pub filter_applied: String,
    pub processing_time_ms: f32,
}
