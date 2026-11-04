// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "ABITVCore",
    platforms: [
        .iOS(.v16),
        .macOS(.v13)
    ],
    products: [
        .library(
            name: "ABITVCore",
            targets: ["ABITVCore"]
        )
    ],
    targets: [
        .target(
            name: "ABITVCore",
            path: "ABITV/Core"
        ),
        .testTarget(
            name: "ABITVTests",
            dependencies: ["ABITVCore"],
            path: "Tests/ABITVTests"
        )
    ]
)
