import Photos
import CoreLocation
import UIKit

/// Saves photos and videos to the Photos library with optional GPS metadata
enum PhotoSaver {

    // MARK: - Save JPEG

    /// Save JPEG data to Photos library with optional GPS coordinates
    static func saveJpeg(_ data: Data, location: CLLocation? = nil) async -> Bool {
        await withCheckedContinuation { continuation in
            PHPhotoLibrary.requestAuthorization(for: .addOnly) { status in
                guard status == .authorized || status == .limited else {
                    continuation.resume(returning: false)
                    return
                }

                PHPhotoLibrary.shared().performChanges({
                    let request = PHAssetCreationRequest.forAsset()
                    let options = PHAssetResourceCreationOptions()
                    options.shouldMoveFile = false
                    request.addResource(with: .photo, data: data, options: options)

                    if let location = location {
                        request.location = location
                    }

                    request.creationDate = Date()
                }) { success, error in
                    if let error = error {
                        print("Photo save error: \(error.localizedDescription)")
                    }
                    continuation.resume(returning: success)
                }
            }
        }
    }

    // MARK: - Save Video

    /// Save a video file to Photos library with optional GPS coordinates
    static func saveVideo(at url: URL, location: CLLocation? = nil) async -> Bool {
        await withCheckedContinuation { continuation in
            PHPhotoLibrary.requestAuthorization(for: .addOnly) { status in
                guard status == .authorized || status == .limited else {
                    continuation.resume(returning: false)
                    return
                }

                PHPhotoLibrary.shared().performChanges({
                    let request = PHAssetCreationRequest.forAsset()
                    let options = PHAssetResourceCreationOptions()
                    options.shouldMoveFile = false
                    request.addResource(with: .video, fileURL: url, options: options)

                    if let location = location {
                        request.location = location
                    }

                    request.creationDate = Date()
                }) { success, error in
                    if let error = error {
                        print("Video save error: \(error.localizedDescription)")
                    }
                    continuation.resume(returning: success)
                }
            }
        }
    }

    // MARK: - Last Photo Thumbnail

    /// Fetch the most recent photo thumbnail from the library
    static func fetchLastPhotoThumbnail(size: CGSize = CGSize(width: 80, height: 80)) async -> UIImage? {
        await withCheckedContinuation { continuation in
            PHPhotoLibrary.requestAuthorization(for: .readAddOnly) { status in
                guard status == .authorized || status == .limited else {
                    continuation.resume(returning: nil)
                    return
                }

                let options = PHFetchOptions()
                options.sortDescriptors = [NSSortDescriptor(key: "creationDate", ascending: false)]
                options.fetchLimit = 1

                let result = PHAsset.fetchAssets(with: .image, options: options)
                guard let asset = result.firstObject else {
                    continuation.resume(returning: nil)
                    return
                }

                let imageOptions = PHImageRequestOptions()
                imageOptions.isSynchronous = true
                imageOptions.deliveryMode = .fastFormat
                imageOptions.resizeMode = .fast

                PHImageManager.default().requestImage(
                    for: asset,
                    targetSize: size,
                    contentMode: .aspectFill,
                    options: imageOptions
                ) { image, _ in
                    continuation.resume(returning: image)
                }
            }
        }
    }
}