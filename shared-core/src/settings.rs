use crate::error::CameraError;
use crate::types::AppSettings;
use std::path::PathBuf;

/// 设置管理器
/// 使用 JSON 文件持久化设置（跨平台通用）
pub struct SettingsManager {
    config_path: PathBuf,
}

impl SettingsManager {
    pub fn new(config_path: String) -> Self {
        Self {
            config_path: PathBuf::from(config_path),
        }
    }

    fn config_file(&self) -> PathBuf {
        self.config_path.join("settings.json")
    }

    pub fn load(&self) -> AppSettings {
        let file = self.config_file();
        if !file.exists() {
            return AppSettings::default();
        }

        match std::fs::read_to_string(&file) {
            Ok(json) => serde_json::from_str(&json).unwrap_or_default(),
            Err(_) => AppSettings::default(),
        }
    }

    pub fn save(&self, settings: AppSettings) -> Result<(), CameraError> {
        std::fs::create_dir_all(&self.config_path).map_err(|_| CameraError::StorageError)?;

        let json =
            serde_json::to_string_pretty(&settings).map_err(|_| CameraError::StorageError)?;

        std::fs::write(self.config_file(), json).map_err(|_| CameraError::StorageError)?;

        Ok(())
    }

    pub fn reset_to_default(&self) -> AppSettings {
        AppSettings::default()
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::env;

    #[test]
    fn test_load_default_when_no_file() {
        let dir = env::temp_dir().join("camera_app_test_no_file");
        let _ = std::fs::remove_dir_all(&dir);

        let manager = SettingsManager::new(dir.to_string_lossy().to_string());
        let settings = manager.load();
        assert_eq!(settings.jpeg_quality, 95);

        let _ = std::fs::remove_dir_all(&dir);
    }

    #[test]
    fn test_save_and_load() {
        let dir = env::temp_dir().join("camera_app_test_save_load");
        let _ = std::fs::remove_dir_all(&dir);

        let manager = SettingsManager::new(dir.to_string_lossy().to_string());

        let mut settings = AppSettings::default();
        settings.jpeg_quality = 80;
        settings.grid_lines = false;

        manager.save(settings.clone()).unwrap();
        let loaded = manager.load();

        assert_eq!(loaded.jpeg_quality, 80);
        assert!(!loaded.grid_lines);

        let _ = std::fs::remove_dir_all(&dir);
    }

    #[test]
    fn test_reset_to_default() {
        let dir = env::temp_dir().join("camera_app_test_reset");
        let manager = SettingsManager::new(dir.to_string_lossy().to_string());
        let settings = manager.reset_to_default();
        assert_eq!(settings, AppSettings::default());
    }
}
