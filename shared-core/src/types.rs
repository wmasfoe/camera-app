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

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_camera_config_default() {
        let config = CameraConfig::default();
        assert_eq!(config.width, 1920);
        assert_eq!(config.height, 1080);
        assert_eq!(config.fps, 30);
        assert_eq!(config.flash, FlashMode::Auto);
        assert_eq!(config.facing, CameraFacing::Back);
        assert_eq!(config.mode, CaptureMode::Photo);
        assert!(!config.enable_hdr);
        assert!((config.zoom - 1.0).abs() < f32::EPSILON);
    }

    #[test]
    fn test_camera_config_custom() {
        let config = CameraConfig {
            width: 3840,
            height: 2160,
            fps: 60,
            flash: FlashMode::On,
            facing: CameraFacing::Front,
            mode: CaptureMode::Video,
            enable_hdr: true,
            zoom: 2.5,
        };
        assert_eq!(config.width, 3840);
        assert_eq!(config.fps, 60);
        assert_eq!(config.flash, FlashMode::On);
        assert_eq!(config.facing, CameraFacing::Front);
        assert!(config.enable_hdr);
    }

    #[test]
    fn test_app_settings_default() {
        let settings = AppSettings::default();
        assert_eq!(settings.jpeg_quality, 95);
        assert_eq!(settings.photo_format, "jpeg");
        assert!(settings.save_location);
        assert!(!settings.mirror_front_camera);
        assert_eq!(settings.default_filter, FilterType::None);
        assert!(settings.shutter_sound);
        assert!(settings.grid_lines);
        assert!(!settings.location_tag);
    }

    #[test]
    fn test_app_settings_serde_roundtrip() {
        let settings = AppSettings::default();
        let json = serde_json::to_string(&settings).unwrap();
        let loaded: AppSettings = serde_json::from_str(&json).unwrap();
        assert_eq!(settings, loaded);
    }

    #[test]
    fn test_app_settings_serde_custom() {
        let mut settings = AppSettings::default();
        settings.jpeg_quality = 80;
        settings.grid_lines = false;
        settings.default_filter = FilterType::Noir;

        let json = serde_json::to_string(&settings).unwrap();
        let loaded: AppSettings = serde_json::from_str(&json).unwrap();
        assert_eq!(loaded.jpeg_quality, 80);
        assert!(!loaded.grid_lines);
        assert_eq!(loaded.default_filter, FilterType::Noir);
    }

    #[test]
    fn test_filter_type_serde() {
        let filters = vec![
            FilterType::None,
            FilterType::Vivid,
            FilterType::Warm,
            FilterType::Cool,
            FilterType::Noir,
            FilterType::Fade,
        ];
        for filter in filters {
            let json = serde_json::to_string(&filter).unwrap();
            let loaded: FilterType = serde_json::from_str(&json).unwrap();
            assert_eq!(filter, loaded);
        }
    }

    #[test]
    fn test_flash_mode_variants() {
        assert_ne!(FlashMode::Off, FlashMode::On);
        assert_ne!(FlashMode::Auto, FlashMode::Torch);
        assert_eq!(FlashMode::Auto, FlashMode::Auto);
    }

    #[test]
    fn test_camera_facing_variants() {
        assert_ne!(CameraFacing::Back, CameraFacing::Front);
    }

    #[test]
    fn test_capture_mode_variants() {
        assert_ne!(CaptureMode::Photo, CaptureMode::Video);
        assert_ne!(CaptureMode::Portrait, CaptureMode::Night);
    }

    #[test]
    fn test_capture_result_fields() {
        let result = CaptureResult {
            file_path: "/test/photo.jpg".to_string(),
            width: 4000,
            height: 3000,
            file_size: 2_500_000,
            timestamp: 1700000000000,
            is_front_camera: false,
            flash_used: FlashMode::Auto,
            zoom_used: 1.0,
        };
        assert_eq!(result.file_path, "/test/photo.jpg");
        assert_eq!(result.width, 4000);
        assert!(!result.is_front_camera);
    }

    #[test]
    fn test_processed_info_fields() {
        let info = ProcessedInfo {
            file_path: "/test/processed.jpg".to_string(),
            width: 4000,
            height: 3000,
            file_size: 1_800_000,
            filter_applied: "Noir".to_string(),
            processing_time_ms: 45.5,
        };
        assert_eq!(info.filter_applied, "Noir");
        assert!(info.processing_time_ms > 0.0);
    }
}
