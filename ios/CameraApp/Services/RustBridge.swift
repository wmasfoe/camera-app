/**
 * Rust shared library bridge layer
 * UniFFI auto-generates Swift bindings to the Generated/ directory.
 *
 * Build flow:
 * 1. cd shared-core && cargo build --release --target aarch64-apple-ios
 * 2. UniFFI generates Swift files to Generated/
 * 3. Xcode project references the Generated/ Swift files
 *
 * When bindings are generated, replace the placeholder implementations below
 * with actual camera_shared_core calls:
 *   - camera_shared_core.ImageProcessor().autoEnhance(inputImage:)
 *   - camera_shared_core.ImageProcessor().applyFilter(inputImage:filter:)
 *   - camera_shared_core.ImageProcessor().supportedFilters()
 *   - camera_shared_core.ImageProcessor().filterName(filter:)
 *   - camera_shared_core.getVersion()
 */

import Foundation

enum RustBridge {
    /// Check if Rust library is available
    static func isLoaded() -> Bool {
        // TODO: Replace with actual check when UniFFI bindings are generated
        return false
    }

    /// Get Rust library version
    static func getVersion() -> String {
        // TODO: Replace with: try camera_shared_core.getVersion()
        return "0.1.0 (bindings not generated)"
    }

    /// Get supported filter types
    static func supportedFilters() -> [FilterType] {
        // TODO: Replace with: ImageProcessor().supportedFilters().map { ... }
        return FilterType.allCases
    }

    /// Get display name for a filter
    static func filterName(_ filter: FilterType) -> String {
        // TODO: Replace with: ImageProcessor().filterName(filter: filter)
        return filter.rawValue
    }

    /// Auto-enhance image data
    static func autoEnhance(imageData: Data) async -> Data? {
        // TODO: Replace with:
        // let processor = camera_shared_core.ImageProcessor()
        // let result = try processor.autoEnhance(inputImage: Array(imageData))
        // return Data(result)
        return imageData
    }

    /// Apply filter to image data
    static func applyFilter(imageData: Data, filter: FilterType) async -> Data? {
        // TODO: Replace with:
        // let processor = camera_shared_core.ImageProcessor()
        // let filterType = mapToRustFilter(filter)
        // let result = try processor.applyFilter(inputImage: Array(imageData), filter: filterType)
        // return Data(result)
        return imageData
    }
}