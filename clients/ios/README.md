# ios/

iOS 原生客户端落位（规约：[../../contract/clients/ios.md](../../contract/clients/ios.md)，前置 [client-shared](../../contract/clients/client-shared.md)）：

- **栈**：Swift + SwiftUI + HIG；MVVM + @Observable（M6）；Swift 6 严格并发；依赖管理 SPM 唯一。
- **契约内核**：`yarch-client-ios`（SPM package）——信封解包 / 错误三分类 / traceId 注入，git tag 直引零注册（对照 android 侧发 Central，各取生态最短路径）。
- **工程范式**：xcodegen 生成（`project.yml` 入仓、`.xcodeproj` 不入仓）；模块化本地 SPM package（App 壳 + ContractKit / DesignSystem / Features/*）。
- **双基线模板**：`ios17`（默认，@Observable）/ `ios16`（扩展，降级 ObservableObject）分文件夹；Xcode/SDK 跟当前 stable。
- **机检**：SwiftLint（Airbnb 配置起步，偏差登记在 [digest 附 B](../../docs/references/ios-official-guides-digest.md)）+ SwiftFormat；测试新代码 Swift Testing、UI 测试 XCTest。
- **一行命令**：`yarch init ios <app名>`（App 名按 registry 五节登记，双端包标识一致互为派生）。
