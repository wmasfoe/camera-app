// camera-app shared-core
// Rust 共享核心库 — 编译到 Android (.so) 和 iOS (.a)
// 通过 UniFFI 自动生成 Kotlin 和 Swift 绑定

#![allow(clippy::empty_line_after_doc_comments)]
#![allow(clippy::large_const_arrays)]

mod error;
mod image_processor;
mod settings;
mod types;

// 重新导出类型供 UniFFI 使用
pub use error::CameraError;
pub use image_processor::ImageProcessor;
pub use settings::SettingsManager;
pub use types::*;

// 包含 UniFFI 生成的脚手架代码
uniffi::include_scaffolding!("camera");

/// 获取库版本号
fn get_version() -> String {
    env!("CARGO_PKG_VERSION").to_string()
}

/// 获取平台信息（编译目标）
fn get_platform_info() -> String {
    format!("{}-{}", std::env::consts::OS, std::env::consts::ARCH)
}

// ==================== 单元测试 ====================

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_version() {
        let v = get_version();
        assert_eq!(v, "0.1.0");
    }

    #[test]
    fn test_platform_info() {
        let info = get_platform_info();
        assert!(!info.is_empty());
    }

    #[test]
    fn test_default_config() {
        let config = CameraConfig::default();
        assert_eq!(config.width, 1920);
        assert_eq!(config.height, 1080);
        assert_eq!(config.fps, 30);
    }

    #[test]
    fn test_default_settings() {
        let settings = AppSettings::default();
        assert_eq!(settings.jpeg_quality, 95);
        assert!(!settings.mirror_front_camera);
        assert!(settings.grid_lines);
    }

    #[test]
    fn test_filter_names() {
        let processor = ImageProcessor::new();
        assert_eq!(processor.filter_name(FilterType::None), "Original");
        assert_eq!(processor.filter_name(FilterType::Vivid), "Vivid");
        assert_eq!(processor.filter_name(FilterType::Noir), "Noir");
    }

    #[test]
    fn test_supported_filters() {
        let processor = ImageProcessor::new();
        let filters = processor.supported_filters();
        assert!(filters.len() >= 6);
    }
}
