# AGENTS.md — {{appClassName}} 工程守则

> 本工程由 yarch yarch-init-app（ios）生成，契约唯一权威 = yarch 仓 contract/（clients/ios.md + client-shared.md + api 四件套）。
> 任何 AI 编码代理改码前先读本文件；条文与直觉冲突时以契约为准，红线改动直接拒绝——机检与 CR 均按此执行。

## 工程地图

| 目录 | 职责 |
|---|---|
| App/ | 壳：入口 / RootView（.xcodeproj 不入仓，`make proj` 由 xcodegen 生成） |
| Packages/Features/ | 功能模块：View + ViewModel + Repository（本地 SPM package） |
| Packages/DesignSystem | 主题 token（禁反向依赖 Feature） |
| ContractKit（includeBuild） | yarch-client-ios 契约内核——**HTTP 一律经 ApiClient** |

## 命令表

| 场景 | 命令 |
|---|---|
| 验证（改完必跑） | swift test（各 Feature 包内）+ swiftlint && swiftformat --lint . |
| 工程再生 | make proj（xcodegen；改 project.yml 后必跑） |

## 红线清单

| 红线 | 规则 | 出处 |
|---|---|---|
| 契约内核单点 | 信封解包 / 错误三分类 / 401 单点刷新 / traceId 只经 ContractKit（ApiClient.call）；**禁业务代码裸 URLSession 直连业务接口** | client-shared 一/二 |
| 取消非错误 | CancellationError 原生传导，禁捕获转译或吞掉 | client-shared 一-4 |
| 分层方向 | MVVM：@Observable ViewModel 持状态，View 只读；feature 间禁互依 | ios.md |
| 机检组合 | SwiftLint（Airbnb 基准）+ SwiftFormat；新测试一律 Swift Testing | ios.md |
| 工程生成 | project.yml 是唯一真理源（.xcodeproj 不入仓不手改） | ios.md |
| 命名 | bundle id 与应用名登记一致（registry 五节），双端互为派生 | registry.md |

## 契约锚点

yarch 仓 contract/clients/ios.md · client-shared.md · contract/api/——实现与本仓不一致 = 实现 bug。
