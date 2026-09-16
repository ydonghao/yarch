// swift-tools-version:6.0
import PackageDescription

let package = Package(
    name: "yarch-client-ios",
    platforms: [
        // 异步 URLSession（data(for:)）需 iOS 15/macOS 12；测试用 Duration 版 Task.sleep 需 macOS 13。
        // 库自身的最低面宽于消费方基线（App 模板档 ios16/ios17，见 contract/clients/ios.md 六）
        .iOS(.v15),
        .macOS(.v13),
    ],
    products: [
        .library(name: "ContractKit", targets: ["ContractKit"]),
    ],
    targets: [
        .target(name: "ContractKit"),
        .testTarget(
            name: "ContractKitTests",
            dependencies: ["ContractKit"],
            path: "Tests/ContractKitTests"
        ),
    ]
)
