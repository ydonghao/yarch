# ios/

iOS 原生客户端落位（规约：[../../contract/clients/ios.md](../../contract/clients/ios.md)，前置 [client-shared](../../contract/clients/client-shared.md)；施工计划：[../PLAN.md](../PLAN.md)）。

## yarch-client-ios（第一批已交付）

SPM package（`ContractKit`）——信封解包 / 错误三分类 / traceId 注入 / 超时单点 / 401 单点刷新重放。Swift 6 严格并发、Codable + URLSession async/await、**零第三方依赖**；消费走 git tag 直引（SPM，零注册）。

- **API 面**：`RestResponse/Page` · `ApiError/NetworkError(-1)` · `HttpConfig` · `makeTraceId` · `ApiClient.get/post/call`
- **测试**：Swift Testing（URLProtocol 桩），运行：`swift test`
- **本机注意**：CommandLineTools 缺 `Testing` 模块——完整工具链在 Xcode.app 里，跑测试用
  `DEVELOPER_DIR=/Applications/Xcode.app/Contents/Developer swift test`（不动全局 xcode-select；CI macos runner 自带完整 Xcode 无此问题）

```swift
let client = ApiClient(baseURL: URL(string: "https://api.example.com")!)
let page: Page<User>? = try await client.get("api/v1/users", query: ["page": "1"])
```

## 后续批次（见 PLAN）

- 第二批：`ios17/16` 双基线模板（xcodegen `project.yml` + App 壳 + ContractKit/DesignSystem/Features 本地 SPM package 骨架 + SwiftLint(Airbnb)/SwiftFormat 预接线；`.xcodeproj` 构建验证走 CI macos runner）
- 第三批：`yarch init app --platform ios|both`（clients/create 统一生成器）
