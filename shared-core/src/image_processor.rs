use crate::error::CameraError;
use crate::types::FilterType;

/// 图像处理器
/// 所有图像处理算法在 Rust 层实现，两端共享
pub struct ImageProcessor;

impl ImageProcessor {
    pub fn new() -> Self {
        Self
    }

    pub fn apply_filter(
        &self,
        input_image: Vec<u8>,
        filter: FilterType,
    ) -> Result<Vec<u8>, CameraError> {
        if input_image.is_empty() {
            return Err(CameraError::ProcessingFailed);
        }

        match filter {
            FilterType::None => Ok(input_image),
            _ => {
                // TODO: 实现实际的滤镜算法
                Ok(input_image)
            }
        }
    }

    pub fn auto_enhance(&self, input_image: Vec<u8>) -> Result<Vec<u8>, CameraError> {
        if input_image.is_empty() {
            return Err(CameraError::ProcessingFailed);
        }
        // TODO: 直方图分析、自动白平衡、对比度拉伸、锐化
        Ok(input_image)
    }

    pub fn compress(&self, input_image: Vec<u8>, quality: u32) -> Result<Vec<u8>, CameraError> {
        if input_image.is_empty() {
            return Err(CameraError::ProcessingFailed);
        }
        if quality == 0 || quality > 100 {
            return Err(CameraError::InvalidConfig);
        }
        // TODO: 用 image crate 重新编码 JPEG
        Ok(input_image)
    }

    pub fn supported_filters(&self) -> Vec<FilterType> {
        vec![
            FilterType::None,
            FilterType::Vivid,
            FilterType::Warm,
            FilterType::Cool,
            FilterType::Noir,
            FilterType::Fade,
        ]
    }

    pub fn filter_name(&self, filter: FilterType) -> String {
        match filter {
            FilterType::None => "Original".to_string(),
            FilterType::Vivid => "Vivid".to_string(),
            FilterType::Warm => "Warm".to_string(),
            FilterType::Cool => "Cool".to_string(),
            FilterType::Noir => "Noir".to_string(),
            FilterType::Fade => "Fade".to_string(),
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_none_filter_returns_original() {
        let processor = ImageProcessor::new();
        let data = vec![1u8, 2, 3, 4];
        let result = processor
            .apply_filter(data.clone(), FilterType::None)
            .unwrap();
        assert_eq!(result, data);
    }

    #[test]
    fn test_compress_invalid_quality() {
        let processor = ImageProcessor::new();
        let data = vec![1u8, 2, 3];
        assert!(processor.compress(data.clone(), 0).is_err());
        assert!(processor.compress(data, 101).is_err());
    }

    #[test]
    fn test_empty_image_returns_error() {
        let processor = ImageProcessor::new();
        assert!(processor.apply_filter(vec![], FilterType::Vivid).is_err());
        assert!(processor.auto_enhance(vec![]).is_err());
        assert!(processor.compress(vec![], 80).is_err());
    }

    #[test]
    fn test_all_filters_have_names() {
        let processor = ImageProcessor::new();
        for filter in processor.supported_filters() {
            let name = processor.filter_name(filter);
            assert!(!name.is_empty());
        }
    }
}
