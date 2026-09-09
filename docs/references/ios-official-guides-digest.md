# iOS 规约来源解析（Swift 官方 + Airbnb + 工具链与工程生成选型）

> **定位**：服务于 [contract/clients/ios.md](../../contract/clients/ios.md) 评审的解析材料（来源展开、选型对比、偏差登记）。**规范性条文一律以 [../../contract/](../../contract/README.md) 为准**，本文件不承载权威条文。
> 调研与拍板：2026-09-09（M1-M7 决策清单，唯一登记处 contract/README.md 关键架构决策登记表）。

## A. Swift API Design Guidelines（官方，API 设计基准）

- 出处：[swift.org/documentation/api-design-guidelines](https://swift.org/documentation/api-design-guidelines/)（WWDC 2016 官方课程起持续演进）。
- 核心原则：**清晰优先于简短**（clarity at point of use）；省略冗余词（`view.removeFromSuperview()` 而非 `view.remove(fromSuperview:())`）；弱类型参数靠参数标签补语义（`move(from:to:)`）；工厂用 `make` 前缀；副作用的 mutating/非 mutating 语义对称。
- yarch 取法：**引用级条文**（ios.md 一-2）——全文以官方为准、yarch 不复制改写。理由：它是 Swift 生态唯一无争议权威，且标准库与 SwiftUI API 本身按它设计，复制反而漂移。

## B. Airbnb Swift Style Guide 逐块取舍（M2 拍板基准）

- 出处：[airbnb.tech/opensource/swift-style-guide](https://airbnb.tech/opensource/swift-style-guide/)。选型理由：社区最流行 + **SwiftLint/SwiftFormat 配置开源**（`airbnb.swiftlint.yml` 可直接继承），机检零启动成本。
- 候选对比：Google 指南（[google.github.io/swift](https://google.github.io/swift/)）为其内部工具链定制（google-swiftfmt 管线），外部团队适配成本高；Kodeco 指南是 SwiftLint 规则集的描述基准、偏教学。**Airbnb 胜出**。

| 主题 | Airbnb 口径 | yarch 取舍 | 条文 |
|---|---|---|---|
| 行宽 | 100 字符 | 采纳 | 一-3 |
| 强制解包 / `try!` | 生产禁用（测试/明确不变量豁免） | 采纳 | 一-3 |
| guard 早退 | 优先减少嵌套 | 采纳 | 一-3 |
| 类型默认不可变 | `let` 优先 | 采纳 | 一-4 |
| closure 捕获 | 显式 `weak self` 说明生命周期 | 采纳（构不成环可注释豁免） | 一-4 |
| 文档注释 | `///`，公开 API 必写 | 采纳 | 一-3 |
| 平台惯例命名 | delegate/dataSource 按 SDK 签名 | 采纳并显式写明"不适用缩写规则" | 一-5 |
| spacing / 换行细节 | 随 SwiftFormat 配置 | 全量交机检，不进人读条文 | 九-1 |

> 偏差登记口径：今后与 Airbnb 的任何偏差**逐条登记在本表**（新增行），禁口头豁免（ios.md 九-2 的 `disable:next` 就地注释是运行时出口，治理出口在这里）。

## C. 机检组合：SwiftLint + SwiftFormat（vs 官方 swift-format）

- [SwiftLint](https://github.com/realm/swiftlint)：200+ 条 AST 级规则（风格 + 代码味 + 约定），规则描述基准为 Kodeco 指南；社区事实标准。
- SwiftFormat：专注排版/空白格式化，`--lint` 进 CI。
- 分工纪律：**格式类规则在 SwiftLint 侧关闭**（由 SwiftFormat 独占），防两边打架——配置随模板入仓（ios.md 九-1）。
- 不选官方 [swift-format](https://github.com/swiftlang/swift-format) 的理由：SE-0250 明确官方立场是**不强推单一风格**（默认配置仅为 Swift 项目自身风格、可配置）；规则数量与代码味检查远少于 SwiftLint。官方Formatter保留为观察项，不阻塞。

## D. 工程生成选型：xcodegen vs Tuist（M3 拍板）

| 维度 | [xcodegen](https://github.com/yonaskolb/XcodeGen) | Tuist |
|---|---|---|
| 形态 | 单一二进制；YAML spec → `.xcodeproj` | 完整工程框架（生成 + 缓存 + 模板 + 依赖图） |
| 心智成本 | 低：一个 `project.yml`，学完即会 | 高：manifest DSL、版本演进快、breaking 变更多 |
| 与 yarch 生成器契合 | **高**：脚手架只需产 YAML（声明式、好 diff） | 中：模板体系与 Tuist 自身模板重叠 |
| `.xcodeproj` 不入仓 | 原生工作流（clone 后 `xcodegen generate`） | 同样支持 |
| 独有能力 | — | 远程缓存 / 预编译加速（团队规模收益） |

- 结论：**xcodegen**（M3）。yarch 是规约 + 脚手架体系，生成器产声明式 YAML 是最短路径；Tuist 的缓存收益在多团队大型仓才兑现，其版本重量与 yarch「可复现、低魔法」立场冲突。观察哨：若未来出现单仓百模块规模，重评 Tuist 缓存。

## E. HIG 与 iOS 26 设计语言

- 出处：[Apple HIG](https://developer.apple.com/design/human-interface-guidelines)、[Liquid Glass 技术总览](https://developer.apple.com/documentation/technologyoverviews/liquid-glass)（2025 WWDC 起，iOS 26）。
- 要点：新设计语言三原则「层级 / 和谐 / 一致」；玻璃效果属于**功能性层级**（工具栏、控件、导航），内容区不套用；系统组件自适应获得，禁自绘模拟。
- 条文化：ios.md 四-1（HIG 遵循 + Liquid Glass 层级纪律）。设计规约不复制 HIG 细节，引用级。

## F. 测试：Swift Testing vs XCTest

- 出处：Apple 官方（Xcode 16+ / Swift 6 内置），[Swift Testing 速览](https://developer.apple.com/documentation/xcode/adding-tests-to-your-xcode-project)；2025 年持续增强（自定义 trait 等），与 XCTest 的定向互通提案在演进中。
- 现状：单元/集成测试官方钦定新框架（`@Test` / `#expect` / `#require`、参数化）；**UI 测试仍依赖 XCTest**。
- 条文化：ios.md 七-1——新代码 Swift Testing 唯一；XCTest 仅 UI 测试与互通场景，新文件选用须登记理由。与 android 侧 JUnit 分层同构（client-shared 五）。

## G. 依赖管理：SPM 唯一

- SwiftPM 是官方唯一活跃生态；CocoaPods 已进入维护模式（官方 2024 宣布，不再接收功能性演进）——新工程禁用（ios.md 三-2），存量迁移按退役节奏评审。
- `yarch-client-ios`（契约内核）以 SPM package 形态 git tag 直引，零注册成本（对照 android 侧发 Maven Central——复用 java 栈发版基础设施，两端发布通道不对称是刻意的：各取生态最短路径）。

## H. 架构来源说明（MVVM + @Observable，M6）

- Apple 官方**无架构手册**（对比 Google 的 Guide to app architecture）——这是 iOS 侧唯一需要 yarch 自研条文层的原因（ios.md 二）。
- 设计基线：与 android 侧概念同构（client-shared 五方言表：ViewModel+StateFlow ↔ @Observable、UDF 同源）；TCA（The Composable Architecture）为社区强依赖框架路线，函数式心智与依赖引入成本高，不入册。
- iOS 16 档降级 `ObservableObject + @Published`：@Observable 宏要求 iOS 17+；降级是**同构实现**不是第二架构（ios.md 二-2 表），双档模板分文件夹（M4）。

## I. 结论

iOS 侧「官方 API 设计 + Airbnb 风格 + SwiftLint/SwiftFormat 机检 + xcodegen/SPM 工程」拼装闭环，每块都是该维度社区事实标准；架构层 yarch 以双端同构自研补位。本 digest 转为条文溯源材料（改动 ios.md 时先读 B 表对齐偏差登记）。
