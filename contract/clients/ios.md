# iOS 客户端开发规约（v1.0 已定稿）

> **状态：已定稿**（2026-09-09 经 M1-M7 决策清单拍板）。
> 等级：**【强制】**违反即缺陷；**【推荐】**默认遵守、评审后可豁免；**【参考】**正向引导。
> 前置依赖（硬）：[client-shared.md](client-shared.md)——本文件只写 iOS 方言条文，概念与跨平台契约以它为准。
> 风格权威：API 设计 = [Swift API Design Guidelines](https://swift.org/documentation/api-design-guidelines/)（官方）；代码风格 = [Airbnb Swift Style Guide](https://airbnb.tech/opensource/swift-style-guide/)（M2 拍板，SwiftLint/SwiftFormat 配置开源可直接机检）。来源解析见 [digest](../../docs/references/ios-official-guides-digest.md)。
> 约束对象：yarch 体系全部 iOS 原生工程（Swift + SwiftUI 技术栈）。Objective-C 仅限桥接存量库，禁新代码。

## 一、语言与风格

1. 【强制】Swift 是唯一开发语言；与存量 ObjC 的互访收敛在桥接层（一个 wrapper 目标 / 目录），业务代码禁直接 import ObjC 头。
2. 【强制】命名与 API 设计遵循官方 Swift API Design Guidelines：清晰优先（clarity at point of use）、省略冗余词、参数标签承担语法角色（`move(from:to:)`）、工厂 `make` 前缀、副作用的_mutating_ 语义——**本条为引用级条文，全文以官方为准，不复制改写**。
3. 【强制】代码风格以 Airbnb 指南为基线（行宽 100、`///` 文档注释、`guard` 早退、禁强制解包 `!` 与 `try!`——除测试与字面量常量场景），由 SwiftLint + SwiftFormat 落机检（九）；与 Airbnb 的偏差逐条登记在 digest 附 B，**禁口头豁免**。
4. 【强制】类型默认不可变：`let` 优先，`var` 出现须有变更理由；closure 捕获列表显式 `[weak self]`（构不成引用环可豁免并注释）。
5. 【参考】delegate / dataSource 等平台惯例命名（`tableView(_:didSelectRowAt:)`）以 SDK 签名为准，不适用 Airbnb 缩写规则。

## 二、架构（MVVM + @Observable，M6 拍板）

1. 【强制】单向数据流：View 是状态的纯函数（SwiftUI 声明式），**View 不改状态只发意图**；ViewModel 持有全部可变状态与业务编排。
2. 【强制】状态容器按基线分档（六-2），语义同构、禁混用：

| 档 | 状态容器 | 绑定 |
|---|---|---|
| `ios17`（默认） | `@Observable final class` ViewModel | `@State` / `@Bindable` |
| `ios16`（扩展） | `ObservableObject` + `@Published`（@Observable 不可用时的同构降级） | `@StateObject` / `@ObservedObject` |

3. 【强制】ViewModel 禁 import SwiftUI（UI 类型零依赖，可测性前提，八）；Toast / 导航等一次性事件以可标识的 enum 事件暴露，View 侧消费后清除。
4. 【强制】Repository 是数据唯一来源（client-shared 五）：ViewModel 只依赖 Repository 协议，禁直接持 URLSession / 契约内核之外的 HTTP 客户端。
5. 【强制】SwiftUI 是唯一 UI 框架：系统能力缺失场景（少见复杂视图 / 相机预览等）允许 UIViewControllerRepresentable 包装，**须经评审登记**； UIKit 页面级新代码一律否决。
6. 【强制】登录态持有与刷新收敛在认证模块单点（client-shared 一-6）；业务 feature 经注入的取凭证端口拿 token。

## 三、工程结构（xcodegen + SPM，M3 拍板）

1. 【强制】工程由 [xcodegen](https://github.com/yonaskolb/XcodeGen) 生成：`project.yml` 入仓为唯一权威，**`.xcodeproj` 不入仓**（clone 后 `xcodegen generate` 产出）——消除工程文件合并冲突与手改漂移，配置即代码。
2. 【强制】SwiftPM 是唯一依赖管理器：禁 CocoaPods 新工程（存量仅维护至退役）；第三方依赖一律 SPM 引用，版本以精确版本 / 次版本锁定（`from:` 允许、主版本浮动须登记理由）。
3. 【强制】模块化以本地 SPM package 划分（目录即模块，比多 target 轻）：

```
App/                    # xcodegen 壳 target：组装、AppDelegate、根导航
Packages/
  ContractKit/          # yarch-client-ios 消费与封装（信封/认证/trace）
  DesignSystem/         # 设计 token、组件库
  Features/
    Login/  Home/ ...   # 一域一 package，feature 间禁互相依赖
```

4. 【强制】工程由脚手架一行命令创建（`yarch init ios <app名>`），模板分基线文件夹（六-2）；`cp -r` 既有工程当模板不可接受（对齐全局脚手架铁律）。
5. 【推荐】feature package 对外只暴露入口 View 与路由注册；内部视图不 public。

## 四、UI（SwiftUI + HIG）

1. 【强制】遵循 Apple [HIG](https://developer.apple.com/design/human-interface-guidelines)：系统组件优先（导航、列表、表单不自绘）；iOS 26 设计语言（Liquid Glass）应用于**功能性层级**（工具栏 / 控件 / 导航），内容区不套玻璃效果——对齐官方「层级 / 和谐 / 一致」三原则。
2. 【强制】设计 token（颜色 / 字体 / 间距）集中 DesignSystem package（`Color` / `Font` 扩展或资源目录语义名）；**SwiftUI 代码出现硬编码 `Color(red:...)` / `.font(.system(size: 14))` 即缺陷**（同 client-shared 概念表 token 纪律）。
3. 【强制】深色模式与 Dynamic Type 从 token 天然获得（Asset Catalog 语义色 + 相对字体）；禁按环境手写两套样式分支。
4. 【推荐】可复用视图抽 DesignSystem 前先在 feature 内验证三次——过早抽公共组件与过早抽象同罪。

## 五、并发（Swift 6 口径）

1. 【强制】严格并发检查开启（`StrictConcurrency` 目标）：数据竞争在编译期暴露；`@MainActor` 标注 UI 绑定的 ViewModel 与视图逻辑。
2. 【强制】异步一律 async / await + 结构化 Task：`Task {}` 仅限用户意图触发（按钮动作）；**禁 GCD 新代码**（`DispatchQueue.main.async` 仅存量维护）、禁 `Task.detached` 无理由使用。
3. 【强制】取消传导（client-shared 三-3）：Task 取消必须传导到网络层并正常收尾；禁把 `CancellationError` 吞掉或转译为 `ApiError`。
4. 【强制】跨并发域的类型 `Sendable` 合规；ViewModel 可变状态全部留在主 actor。
5. 【参考】combine 不入新代码（async/await + @Observable 官方承接）；存量 combine 维护不强制迁移。

## 六、基线（双档）

1. 【强制】部署基线双档，**模板按文件夹分档**、创建期选定、同一工程禁中途混档：

| 档 | 最低部署 | 定位 | 差异条文 |
|---|---|---|---|
| `ios17`（默认） | iOS 17 | @Observable / Swift 6 完整能力 | 架构条文二-2 正式口径 |
| `ios16`（扩展） | iOS 16 | 覆盖存量设备 | 状态容器降级 ObservableObject（二-2 表）；17+ API 必须 `if #available` guard；@BackDeployed 慎用（评审） |

2. 【强制】Xcode / SDK 跟随当前 stable；`project.yml` 中部署版本由模板档固定，业务工程禁随手改。
3. 【强制】档位调整（17 → 16）为基线变更评审项：涉及降级面（@Observable 迁移、availability guard）全量核查。
4. 【参考】下沉基线的动机应量化（目标设备占比），不带数据的下沉不受理。

## 七、测试

1. 【强制】**Swift Testing 承接新代码全部单元 / 集成测试**（`@Test` / `#expect` / `#require`，Xcode 16+ 官方框架）；XCTest 仅保留两类场景：UI 测试、与既有测试的互通——新文件选 XCTest 须登记理由。
2. 【强制】可测性分层与 android 同构：ViewModel / Repository 单测为基础盘（零 UI 依赖，二-3 是前提）；关键交互以 UI 测试覆盖。
3. 【强制】契约内核库（`yarch-client-ios`）自身测试覆盖 client-shared 全部机检条目（信封解包 / 错误三分类 / 取消传导 / traceId 注入）。
4. 【推荐】测试命名以行为描述为准（`@Test("登录过期时刷新凭证并重放请求")`）；参数化测试（`@Test(arguments:)）`优先于复制粘贴用例。

## 八、发布与合规

1. 【强制】`PrivacyInfo.xcprivacy`（privacy manifest）必配且与实际收集一致：所需 API 理由（UserDefaults 等）、数据收集类型逐项核对；第三方 SDK 的 manifest 合并检查进发布清单。
2. 【强制】权限用途文案（Info.plist 的 `NS*UsageDescription`）逐权限具体说明——空文案、通用文案（"需要访问相机"式）即驳回；未申请的权限声明同样即缺陷（最小权限）。
3. 【强制】签名与描述文件不入仓：证书走 CI secrets / 钥匙串注入；`project.yml` 只含团队 ID 与 entitlements 声明。
4. 【强制】发布通道：TestFlight（internal → external）→ App Store 分阶段放量；构建号（CFBundleVersion）单调递增。
5. 【推荐】App Review 高频驳回项自查进发布清单（crash on launch / 权限文案 / 内购外引导支付 / 占位内容）。

## 九、机检与构建

1. 【强制】双重机检，CI（macOS runner）全绿是合并门槛：

| 工具 | 职责 | 配置 |
|---|---|---|
| SwiftLint | 风格 / 代码味（200+ 规则，AST 级） | Airbnb 配置起步，偏差见 digest 附 B；`.swiftlint.yml` 入仓 |
| SwiftFormat | 格式化（空白 / 排版） | `.swiftformat` 入仓，`--lint` 进 CI |

2. 【强制】豁免必须带文件内 `// swiftlint:disable:next 规则 原因` 就地注释——**禁全局 disable 规则**（规则级豁免走业务仓 `docs/waivers.md`，对齐豁免流程）。
3. 【强制】CI 腿：lint + 单测（Swift Testing）+ UI 冒烟（XCTest）三段全绿；`.xcodeproj` 由 `xcodegen generate` 在 CI 首步产出（不入仓的代价是任何机器可复现）。

## 十、可机检条文清单

> 对齐 [../README.md](../README.md)「机检路线」；落地载体为模板 CI + `yarch-client-ios` 单测 + project.yml 结构校验。

| 条文 | 机检手段 | 阶段 |
|---|---|---|
| 一-3 禁强制解包 | SwiftLint `force_unwrap` / `force_try` | CI |
| 二-3 ViewModel 禁 import SwiftUI | SwiftLint custom rule（import 白名单） | CI |
| 二-5 UIKit 页面级新代码 | 依赖解析（UIViewControllerRepresentable 白名单登记制） | CI |
| 三-1 .xcodeproj 不入仓 | .gitignore 检查 + CI 生成冒烟 | CI |
| 三-2 禁 CocoaPods | 仓库扫描（Podfile 存在即报） | CI |
| 四-2 禁硬编码 token | SwiftLint custom rule（`Color(red:` / `.system(size:` 模式） | CI |
| 五-2 禁 GCD 新代码 | SwiftLint `dispatch_queue` 类规则 | CI |
| 七-1 新测试文件框架 | 脚本扫描：新文件含 `import XCTest` 且非 UI 测试 target 即告警 | CI |
| 八-2 权限文案非空且具体 | 脚本解析 Info.plist 语义检查 | 发布前 |
| 九-2 全局 disable 禁 | 脚本扫描 `disable:` 无 `:next`/`:this` 修饰 | CI |

---

## 附：来源与记录

- 风格基准 Airbnb（M2 拍板）：最流行社区指南 + SwiftLint/SwiftFormat 配置开源（机检零启动成本）；Google 指南为其内部工具链定制不入册；官方 swift-format 刻意不强推单一风格（SE-0250 立场），故风格取社区事实标准。API 设计取官方指南（唯一无争议权威）。
- 工程生成 xcodegen（M3 拍板）：YAML → .xcodeproj 的单一二进制，生成器友好、配置即代码；Tuist 功能更重（缓存 / 模板生态）但版本重量与心智成本高，不入册。选型对比见 digest 附 D。
- 架构 MVVM + @Observable（M6 拍板）：官方无架构手册，以与 android 侧 UDF/ViewModel 概念同构为设计基线（client-shared 五）；TCA（函数式社区强依赖框架）不入册。
- 测试 Swift Testing（Xcode 16+ 官方，替代 XCTest 做单元测试；UI 测试仍 XCTest）；隐私 manifest 为 App Store 现行要求；iOS 26 Liquid Glass 对齐 HIG 层级原则。
- 逐项解析与偏差登记见 [ios-official-guides-digest.md](../../docs/references/ios-official-guides-digest.md)；拍板记录唯一登记处 [../README.md](../README.md) 关键架构决策登记表。
