/**
 * Rust 共享库桥接层
 * UniFFI 会自动生成 Swift 绑定到 Generated/ 目录
 *
 * 编译流程：
 * 1. cd shared-core && cargo build --release --target aarch64-apple-ios
 * 2. UniFFI 自动生成 Swift 文件到 Generated/
 * 3. Xcode 项目引用 Generated/ 下的 Swift 文件
 */

import Foundation

enum RustBridge {
    /// 检查 Rust 库是否可用
    static func isLoaded() -> Bool {
        do {
            _ = try getCameraSharedCoreVersion()
            return true
        } catch {
            return false
        }
    }

    /// 获取 Rust 库版本
    static func getVersion() -> String {
        do {
            return try getCameraSharedCoreVersion()
        } catch {
            return "not loaded"
        }
    }

    private static func getCameraSharedCoreVersion() throws -> String {
        // UniFFI 生成绑定后，这里会调用 Rust 的 get_version()
        // 暂时返回占位
        return "0.1.0 (bindings not generated)"
    }
}