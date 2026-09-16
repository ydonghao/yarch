// swift-tools-version:6.0
import PackageDescription

let package = Package(
    name: "DesignSystem",
    platforms: [.iOS(.v16), .macOS(.v14)],
    products: [
        .library(name: "DesignSystem", targets: ["DesignSystem"]),
    ],
    targets: [
        .target(name: "DesignSystem"),
    ]
)
