// swift-tools-version:6.0
import PackageDescription

let package = Package(
    name: "Users",
    platforms: [.iOS(.v17), .macOS(.v14)],
    products: [
        .library(name: "Users", targets: ["Users"]),
    ],
    dependencies: [
        // 发版前本地源码消费（对偶 golang replace 行）；yarch-client-ios 打 tag 后改：
        // .package(url: "https://github.com/ydonghao/yarch", from: "0.1.0")
        .package(path: "{{yarchClientPath}}"),
    ],
    targets: [
        .target(
            name: "Users",
            dependencies: [.product(name: "ContractKit", package: "ios")]
        ),
        .testTarget(name: "UsersTests", dependencies: ["Users"]),
    ]
)
