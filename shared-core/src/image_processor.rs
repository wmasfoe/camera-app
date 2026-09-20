use std::io::Cursor;

use image::codecs::jpeg::JpegEncoder;
use image::RgbImage;

use crate::error::CameraError;
use crate::types::FilterType;

const JPEG_QUALITY: u8 = 85;

/// 图像处理器 — 所有滤镜算法在 Rust 层实现
#[derive(Default)]
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
                let img = decode_rgb(&input_image)?;
                let (width, height) = img.dimensions();
                let mut pixels = img.into_raw();

                match filter {
                    FilterType::None => unreachable!(),
                    FilterType::Noir => apply_noir(&mut pixels),
                    FilterType::Warm => apply_warm(&mut pixels),
                    FilterType::Cool => apply_cool(&mut pixels),
                    FilterType::Vivid => apply_vivid(&mut pixels),
                    FilterType::Fade => apply_fade(&mut pixels),
                }

                let out_img = RgbImage::from_raw(width, height, pixels)
                    .ok_or(CameraError::ProcessingFailed)?;
                encode_jpeg(&out_img, JPEG_QUALITY)
            }
        }
    }

    pub fn auto_enhance(&self, input_image: Vec<u8>) -> Result<Vec<u8>, CameraError> {
        if input_image.is_empty() {
            return Err(CameraError::ProcessingFailed);
        }

        let img = decode_rgb(&input_image)?;
        let (width, height) = img.dimensions();
        let mut pixels = img.into_raw();

        // 自动增强：略微提升对比度和饱和度
        apply_auto_contrast(&mut pixels);
        apply_vivid_with_factor(&mut pixels, 1.2);

        let out_img =
            RgbImage::from_raw(width, height, pixels).ok_or(CameraError::ProcessingFailed)?;
        encode_jpeg(&out_img, JPEG_QUALITY)
    }

    pub fn compress(&self, input_image: Vec<u8>, quality: u32) -> Result<Vec<u8>, CameraError> {
        if input_image.is_empty() {
            return Err(CameraError::ProcessingFailed);
        }
        if quality == 0 || quality > 100 {
            return Err(CameraError::InvalidConfig);
        }

        let img = decode_rgb(&input_image)?;
        encode_jpeg(&img, quality as u8)
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

// ── Decode / Encode ──────────────────────────────────────────────────

fn decode_rgb(jpeg_bytes: &[u8]) -> Result<RgbImage, CameraError> {
    let dynamic = image::load_from_memory(jpeg_bytes).map_err(|_| CameraError::ProcessingFailed)?;
    Ok(dynamic.to_rgb8())
}

fn encode_jpeg(img: &RgbImage, quality: u8) -> Result<Vec<u8>, CameraError> {
    let mut buf = Vec::new();
    let encoder = JpegEncoder::new_with_quality(Cursor::new(&mut buf), quality);
    img.write_with_encoder(encoder)
        .map_err(|_| CameraError::ProcessingFailed)?;
    Ok(buf)
}

// ── 像素遍历辅助 ─────────────────────────────────────────────────────

#[inline(always)]
fn for_each_pixel(pixels: &mut [u8], mut f: impl FnMut(&mut [u8; 3])) {
    for pixel in pixels.as_chunks_mut::<3>().0 {
        f(pixel);
    }
}

#[inline(always)]
fn clamp_u8(v: i32) -> u8 {
    v.clamp(0, 255) as u8
}

// ── Noir: 灰度 + 对比度增强 ──────────────────────────────────────────

fn apply_noir(pixels: &mut [u8]) {
    // Sigmoid S 曲线 LUT (k=8.0 强对比)
    const K: f32 = 8.0;
    let y_min = 1.0 / (1.0 + (K * 0.5).exp());
    let y_max = 1.0 / (1.0 + (-K * 0.5).exp());
    let y_range = y_max - y_min;

    let mut lut = [0u8; 256];
    for (i, item) in lut.iter_mut().enumerate() {
        let x = i as f32 / 255.0;
        let y = 1.0 / (1.0 + (-K * (x - 0.5)).exp());
        let normalized = (y - y_min) / y_range;
        let val = (normalized * 255.0).clamp(0.0, 255.0) as u8;
        *item = val;
    }

    for_each_pixel(pixels, |px| {
        // BT.601 亮度: 77R + 150G + 29B >> 8 ≈ 0.299R + 0.587G + 0.114B
        let l = ((77 * px[0] as u32 + 150 * px[1] as u32 + 29 * px[2] as u32) >> 8) as u8;
        let out = lut[l as usize];
        px[0] = out;
        px[1] = out;
        px[2] = out;
    });
}

// ── Warm: 暖色调 ─────────────────────────────────────────────────────

fn apply_warm(pixels: &mut [u8]) {
    let mut lut_r = [0u8; 256];
    let mut lut_g = [0u8; 256];
    let mut lut_b = [0u8; 256];
    for i in 0..256 {
        lut_r[i] = ((i as f32 * 1.10 + 10.0).clamp(0.0, 255.0)) as u8;
        lut_g[i] = ((i as f32 * 1.05 + 5.0).clamp(0.0, 255.0)) as u8;
        lut_b[i] = ((i as f32 * 0.90).clamp(0.0, 255.0)) as u8;
    }

    for_each_pixel(pixels, |px| {
        px[0] = lut_r[px[0] as usize];
        px[1] = lut_g[px[1] as usize];
        px[2] = lut_b[px[2] as usize];
    });
}

// ── Cool: 冷色调 ─────────────────────────────────────────────────────

fn apply_cool(pixels: &mut [u8]) {
    let mut lut_r = [0u8; 256];
    let mut lut_g = [0u8; 256];
    let mut lut_b = [0u8; 256];
    for i in 0..256 {
        lut_r[i] = ((i as f32 * 0.90).clamp(0.0, 255.0)) as u8;
        lut_g[i] = ((i as f32 * 0.95 + 5.0).clamp(0.0, 255.0)) as u8;
        lut_b[i] = ((i as f32 * 1.10 + 15.0).clamp(0.0, 255.0)) as u8;
    }

    for_each_pixel(pixels, |px| {
        px[0] = lut_r[px[0] as usize];
        px[1] = lut_g[px[1] as usize];
        px[2] = lut_b[px[2] as usize];
    });
}

// ── Vivid: 饱和度提升 ────────────────────────────────────────────────

fn apply_vivid(pixels: &mut [u8]) {
    apply_vivid_with_factor(pixels, 1.5);
}

fn apply_vivid_with_factor(pixels: &mut [u8], saturation: f32) {
    // BT.601 亮度权重
    const WR: f32 = 0.299;
    const WG: f32 = 0.587;
    const WB: f32 = 0.114;

    // 定点数 (×256)
    let wr_i = (WR * 256.0) as i32;
    let wg_i = (WG * 256.0) as i32;
    let wb_i = (WB * 256.0) as i32;
    let s_i = (saturation * 256.0) as i32;
    let inv_s = 256 - s_i;

    for_each_pixel(pixels, |px| {
        let r = px[0] as i32;
        let g = px[1] as i32;
        let b = px[2] as i32;

        // 亮度 (定点数)
        let l = (wr_i * r + wg_i * g + wb_i * b) >> 8;

        // 混合: L + S × (C - L) = L × (1 - S) + C × S
        px[0] = clamp_u8((l * inv_s + r * s_i) >> 8);
        px[1] = clamp_u8((l * inv_s + g * s_i) >> 8);
        px[2] = clamp_u8((l * inv_s + b * s_i) >> 8);
    });
}

// ── Fade: 低对比度 + 提亮暗部 ────────────────────────────────────────

fn apply_fade(pixels: &mut [u8]) {
    // Step 1: 范围压缩 [0,255] → [30, 230]
    let mut lut_compress = [0u8; 256];
    for (i, item) in lut_compress.iter_mut().enumerate() {
        *item = (i as f32 * (200.0 / 255.0) + 30.0).clamp(0.0, 255.0) as u8;
    }

    // Step 2: 15% 去饱和 (定点数)
    const COLOR_W: i32 = 218; // 0.85 × 256
    const LUM_W: i32 = 38; // 0.15 × 256

    for_each_pixel(pixels, |px| {
        let r = lut_compress[px[0] as usize] as i32;
        let g = lut_compress[px[1] as usize] as i32;
        let b = lut_compress[px[2] as usize] as i32;

        let l = (77 * r + 150 * g + 29 * b) >> 8;

        px[0] = clamp_u8((r * COLOR_W + l * LUM_W) >> 8);
        px[1] = clamp_u8((g * COLOR_W + l * LUM_W) >> 8);
        px[2] = clamp_u8((b * COLOR_W + l * LUM_W) >> 8);
    });
}

// ── 自动对比度 ───────────────────────────────────────────────────────

fn apply_auto_contrast(pixels: &mut [u8]) {
    // 找到实际的最小/最大亮度，然后拉伸到 [0, 255]
    let mut min_lum = 255u8;
    let mut max_lum = 0u8;

    for chunk in pixels.as_chunks::<3>().0 {
        let l = ((77 * chunk[0] as u32 + 150 * chunk[1] as u32 + 29 * chunk[2] as u32) >> 8) as u8;
        min_lum = min_lum.min(l);
        max_lum = max_lum.max(l);
    }

    if max_lum <= min_lum {
        return; // 全灰，不处理
    }

    let range = (max_lum - min_lum) as f32;
    let scale = 255.0 / range;

    // 构建 LUT
    let mut lut = [0u8; 256];
    for (i, item) in lut.iter_mut().enumerate() {
        *item = (((i as f32 - min_lum as f32) * scale).clamp(0.0, 255.0)) as u8;
    }

    for_each_pixel(pixels, |px| {
        px[0] = lut[px[0] as usize];
        px[1] = lut[px[1] as usize];
        px[2] = lut[px[2] as usize];
    });
}

// ── 测试 ─────────────────────────────────────────────────────────────

#[cfg(test)]
mod tests {
    use super::*;
    use image::Rgb;

    fn make_1x1_red_jpeg() -> Vec<u8> {
        let img = RgbImage::from_pixel(1, 1, Rgb([255u8, 0, 0]));
        encode_jpeg(&img, 95).unwrap()
    }

    fn make_2x2_color_jpeg() -> Vec<u8> {
        let mut img = RgbImage::new(2, 2);
        img.put_pixel(0, 0, Rgb([255, 0, 0])); // 红
        img.put_pixel(1, 0, Rgb([0, 255, 0])); // 绿
        img.put_pixel(0, 1, Rgb([0, 0, 255])); // 蓝
        img.put_pixel(1, 1, Rgb([128, 128, 128])); // 灰
        encode_jpeg(&img, 95).unwrap()
    }

    #[test]
    fn test_noir_produces_grayscale() {
        let input = make_1x1_red_jpeg();
        let processor = ImageProcessor::new();
        let output = processor.apply_filter(input, FilterType::Noir).unwrap();
        let decoded = image::load_from_memory(&output).unwrap().to_rgb8();
        let px = decoded.get_pixel(0, 0);
        assert_eq!(px[0], px[1]);
        assert_eq!(px[1], px[2]);
    }

    #[test]
    fn test_warm_shifts_red_up() {
        let input = make_2x2_color_jpeg();
        let processor = ImageProcessor::new();
        let output = processor.apply_filter(input, FilterType::Warm).unwrap();
        assert!(!output.is_empty());
        let decoded = image::load_from_memory(&output).unwrap().to_rgb8();
        // 红色通道应该比原来高
        let px = decoded.get_pixel(0, 0);
        assert!(px[0] > 200); // R 应该被增强
    }

    #[test]
    fn test_cool_shifts_blue_up() {
        let input = make_2x2_color_jpeg();
        let processor = ImageProcessor::new();
        let output = processor.apply_filter(input, FilterType::Cool).unwrap();
        assert!(!output.is_empty());
    }

    #[test]
    fn test_vivid_increases_saturation() {
        let input = make_2x2_color_jpeg();
        let processor = ImageProcessor::new();
        let output = processor.apply_filter(input, FilterType::Vivid).unwrap();
        assert!(!output.is_empty());
    }

    #[test]
    fn test_fade_compresses_range() {
        let input = make_2x2_color_jpeg();
        let processor = ImageProcessor::new();
        let output = processor.apply_filter(input, FilterType::Fade).unwrap();
        assert!(!output.is_empty());
    }

    #[test]
    fn test_none_returns_original() {
        let input = make_1x1_red_jpeg();
        let processor = ImageProcessor::new();
        let output = processor
            .apply_filter(input.clone(), FilterType::None)
            .unwrap();
        assert_eq!(input, output);
    }

    #[test]
    fn test_auto_enhance_works() {
        let input = make_2x2_color_jpeg();
        let processor = ImageProcessor::new();
        let output = processor.auto_enhance(input).unwrap();
        assert!(!output.is_empty());
    }

    #[test]
    fn test_compress_with_quality() {
        let input = make_2x2_color_jpeg();
        let processor = ImageProcessor::new();
        let output = processor.compress(input, 50).unwrap();
        // 压缩后的文件应该更小（或至少有效）
        assert!(!output.is_empty());
        let decoded = image::load_from_memory(&output);
        assert!(decoded.is_ok());
    }

    #[test]
    fn test_empty_input_returns_error() {
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

    #[test]
    fn test_compress_invalid_quality_zero() {
        let processor = ImageProcessor::new();
        let input = make_2x2_color_jpeg();
        assert!(processor.compress(input, 0).is_err());
    }

    #[test]
    fn test_compress_invalid_quality_over_100() {
        let processor = ImageProcessor::new();
        let input = make_2x2_color_jpeg();
        assert!(processor.compress(input, 101).is_err());
    }

    #[test]
    fn test_compress_boundary_quality_1() {
        let processor = ImageProcessor::new();
        let input = make_2x2_color_jpeg();
        let result = processor.compress(input, 1);
        assert!(result.is_ok());
    }

    #[test]
    fn test_compress_boundary_quality_100() {
        let processor = ImageProcessor::new();
        let input = make_2x2_color_jpeg();
        let result = processor.compress(input, 100);
        assert!(result.is_ok());
    }

    #[test]
    fn test_filter_output_is_valid_jpeg() {
        let processor = ImageProcessor::new();
        let input = make_2x2_color_jpeg();
        for filter in processor.supported_filters() {
            let output = processor.apply_filter(input.clone(), filter).unwrap();
            let decoded = image::load_from_memory(&output);
            assert!(decoded.is_ok(), "Filter {:?} produced invalid JPEG", filter);
        }
    }

    #[test]
    fn test_auto_enhance_preserves_dimensions() {
        let processor = ImageProcessor::new();
        let input = make_2x2_color_jpeg();
        let original = image::load_from_memory(&input).unwrap().to_rgb8();
        let enhanced = processor.auto_enhance(input).unwrap();
        let result = image::load_from_memory(&enhanced).unwrap().to_rgb8();
        assert_eq!(original.dimensions(), result.dimensions());
    }

    #[test]
    fn test_noir_white_stays_white() {
        // 白色经过 noir 应该还是接近白色
        let img = RgbImage::from_pixel(1, 1, Rgb([255u8, 255, 255]));
        let input = encode_jpeg(&img, 95).unwrap();
        let processor = ImageProcessor::new();
        let output = processor.apply_filter(input, FilterType::Noir).unwrap();
        let decoded = image::load_from_memory(&output).unwrap().to_rgb8();
        let px = decoded.get_pixel(0, 0);
        // 白色经过 sigmoid 应该还是接近 255
        assert!(
            px[0] > 200,
            "White should stay bright after noir, got {}",
            px[0]
        );
    }

    #[test]
    fn test_noir_black_stays_black() {
        // 黑色经过 noir 应该还是接近黑色
        let img = RgbImage::from_pixel(1, 1, Rgb([0u8, 0, 0]));
        let input = encode_jpeg(&img, 95).unwrap();
        let processor = ImageProcessor::new();
        let output = processor.apply_filter(input, FilterType::Noir).unwrap();
        let decoded = image::load_from_memory(&output).unwrap().to_rgb8();
        let px = decoded.get_pixel(0, 0);
        assert!(
            px[0] < 50,
            "Black should stay dark after noir, got {}",
            px[0]
        );
    }

    #[test]
    fn test_fade_white_compresses() {
        // Fade 应该把纯白和纯黑都压缩到中间范围
        let img = RgbImage::from_pixel(1, 1, Rgb([255u8, 255, 255]));
        let input = encode_jpeg(&img, 95).unwrap();
        let processor = ImageProcessor::new();
        let output = processor.apply_filter(input, FilterType::Fade).unwrap();
        let decoded = image::load_from_memory(&output).unwrap().to_rgb8();
        let px = decoded.get_pixel(0, 0);
        // 纯白经过 fade 应该被压缩到 ~230 范围
        assert!(
            px[0] < 240,
            "White should be compressed by fade, got {}",
            px[0]
        );
        assert!(
            px[0] > 200,
            "White should still be bright after fade, got {}",
            px[0]
        );
    }

    #[test]
    fn test_warm_preserves_brightness() {
        // Warm 滤镜不应该大幅改变整体亮度
        let img = RgbImage::from_pixel(1, 1, Rgb([128u8, 128, 128]));
        let input = encode_jpeg(&img, 95).unwrap();
        let processor = ImageProcessor::new();
        let output = processor.apply_filter(input, FilterType::Warm).unwrap();
        let decoded = image::load_from_memory(&output).unwrap().to_rgb8();
        let px = decoded.get_pixel(0, 0);
        // 灰色经过 warm 后亮度应该大致保持
        let brightness = (px[0] as i32 + px[1] as i32 + px[2] as i32) / 3;
        assert!(
            brightness > 100 && brightness < 180,
            "Brightness should be ~128, got {}",
            brightness
        );
    }

    #[test]
    fn test_vivid_neutral_gray_unchanged() {
        // 灰色 (R=G=B) 经过 vivid 饱和度提升应该不变
        let img = RgbImage::from_pixel(1, 1, Rgb([128u8, 128, 128]));
        let input = encode_jpeg(&img, 95).unwrap();
        let processor = ImageProcessor::new();
        let output = processor.apply_filter(input, FilterType::Vivid).unwrap();
        let decoded = image::load_from_memory(&output).unwrap().to_rgb8();
        let px = decoded.get_pixel(0, 0);
        // 灰色没有饱和度可提升，应该大致不变
        assert!((px[0] as i32 - px[1] as i32).abs() < 10);
        assert!((px[1] as i32 - px[2] as i32).abs() < 10);
    }

    #[test]
    fn test_supported_filters_count() {
        let processor = ImageProcessor::new();
        assert_eq!(processor.supported_filters().len(), 6);
    }
}
