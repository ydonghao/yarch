# {{packageName}}（yarch iOS 模板 · ios17 默认档）

yarch iOS 工程模板（[contract/clients/ios.md](../../../../contract/clients/ios.md) 全条文载体；本档 iOS 17 + @Observable，扩展档见 `../ios16/`）。

## 结构

```
App/                    # xcodegen 壳（project.yml 唯一权威，.xcodeproj 不入仓）
Packages/
  DesignSystem/         # 设计 token 唯一落点（Color(red:) 只许在此）
  Features/
    Login/              # MVVM 样板：@Observable VM + mock Repository + Swift Testing
    Users/              # 契约内核消费样板：ApiClient + ApiError/NetworkError 分流
```

## 起步

```bash
xcodegen generate                        # 产出 {{appClassName}}.xcodeproj（不入仓）
open {{appClassName}}.xcodeproj                 # Xcode 运行
make test                                # feature 包 swift test（Swift Testing）
```

- ContractKit 消费：各 package 声明 `.package(path: "{{yarchClientPath}}")` 指向本仓 `clients/ios`（发版前本地源码，对偶 golang replace 行）；yarch-client-ios 打 tag 后改 `.package(url: "https://github.com/ydonghao/yarch", from: "0.1.0")` 并指向 `clients/ios` 的 tag。
- 机检：SwiftLint（`.swiftlint.yml`，Airbnb 裁剪档）+ SwiftFormat（`.swiftformat`）；新代码测试一律 Swift Testing，XCTest 仅 UI 测试场景（规约七-1）。

## 与 ios16 档的差异

| 项 | 本档（ios17 默认） | ios16（扩展） |
|---|---|---|
| 状态容器 | `@Observable` + `@State`/`@Bindable` | `ObservableObject` + `@StateObject`/`@ObservedObject`（同构降级，规约二-2 表） |
| 17+ API | 直接使用 | `if #available` guard 强制 |
