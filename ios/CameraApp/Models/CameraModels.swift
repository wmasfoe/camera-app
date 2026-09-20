import AVFoundation
import CoreLocation
import SwiftUI
import UIKit

// MARK: - CaptureMode extensions (UniFFI generated)

extension CaptureMode: CaseIterable {
    public static var allCases: [CaptureMode] = [.video, .photo, .portrait, .night]
}

extension CaptureMode: Identifiable {
    public var id: String { "\(self)" }
}

extension CaptureMode {
    var displayName: String {
        switch self {
        case .video: return "VIDEO"
        case .photo: return "PHOTO"
        case .portrait: return "PORTRAIT"
        case .night: return "NIGHT"
        }
    }
}

// MARK: - FlashMode extensions (UniFFI generated)

extension FlashMode {
    var avFlashMode: AVCaptureDevice.FlashMode {
        switch self {
        case .auto: return .auto
        case .on: return .on
        case .off: return .off
        case .torch: return .on
        }
    }

    var torchMode: AVCaptureDevice.TorchMode {
        switch self {
        case .on, .torch: return .on
        default: return .off
        }
    }

    var iconName: String {
        switch self {
        case .auto: return "bolt.badge.automatic"
        case .on: return "bolt.fill"
        case .off: return "bolt.slash"
        case .torch: return "bolt.fill"
        }
    }

    mutating func cycle() {
        switch self {
        case .auto: self = .on
        case .on: self = .off
        case .off: self = .auto
        case .torch: self = .auto
        }
    }
}

// MARK: - FilterType extensions (UniFFI generated)

extension FilterType: CaseIterable {
    public static var allCases: [FilterType] = [.none, .vivid, .warm, .cool, .noir, .fade]
}

extension FilterType: Identifiable {
    public var id: String { "\(self)" }
}

extension FilterType: RawRepresentable {
    public init?(rawValue: String) {
        switch rawValue {
        case "None": self = .none
        case "Vivid": self = .vivid
        case "Warm": self = .warm
        case "Cool": self = .cool
        case "Noir": self = .noir
        case "Fade": self = .fade
        default: return nil
        }
    }

    public var rawValue: String {
        switch self {
        case .none: return "None"
        case .vivid: return "Vivid"
        case .warm: return "Warm"
        case .cool: return "Cool"
        case .noir: return "Noir"
        case .fade: return "Fade"
        }
    }
}

enum LocationStatus {
    case disabled
    case locating
    case acquired(CLLocation)
}

// MARK: - Camera Position Helper

enum CameraLens: Equatable {
    case wide
    case ultraWide
    case telephoto
    case front
}

// MARK: - Zoom Preset

struct ZoomPreset: Identifiable {
    let factor: CGFloat
    let label: String
    var id: CGFloat { factor }

    static let defaults: [ZoomPreset] = [
        ZoomPreset(factor: 0.5, label: ".5"),
        ZoomPreset(factor: 1.0, label: "1"),
        ZoomPreset(factor: 2.0, label: "2"),
        ZoomPreset(factor: 5.0, label: "5"),
    ]
}

// MARK: - Design Tokens (matches Android)

enum CameraTokens {
    static let bg = UIColor.black
    static let surface = UIColor(red: 0.102, green: 0.102, blue: 0.102, alpha: 1)
    static let surface2 = UIColor(red: 0.145, green: 0.145, blue: 0.145, alpha: 1)
    static let textPrimary = UIColor.white
    static let textMuted = UIColor(red: 0.557, green: 0.557, blue: 0.576, alpha: 1)
    static let accentDim = UIColor(red: 0.388, green: 0.388, blue: 0.4, alpha: 1)
    static let accentGreen = UIColor(red: 0.188, green: 0.820, blue: 0.345, alpha: 1) // #30D158
    static let gridColor = UIColor.white.withAlphaComponent(0.2)
    static let danger = UIColor(red: 1.0, green: 0.271, blue: 0.227, alpha: 1) // #FF453A

    // SwiftUI Color wrappers
    static let swiftBg = Color.black
    static let swiftSurface2 = Color(.sRGB, red: 0.145, green: 0.145, blue: 0.145, opacity: 1)
    static let swiftTextPrimary = Color.white
    static let swiftTextMuted = Color(.sRGB, red: 0.557, green: 0.557, blue: 0.576, opacity: 1)
    static let swiftAccentDim = Color(.sRGB, red: 0.388, green: 0.388, blue: 0.4, opacity: 1)
    static let swiftAccentGreen = Color(.sRGB, red: 0.188, green: 0.820, blue: 0.345, opacity: 1)
    static let swiftDanger = Color(.sRGB, red: 1.0, green: 0.271, blue: 0.227, opacity: 1)
    static let swiftGridColor = Color.white.opacity(0.2)
}