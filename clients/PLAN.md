# clients/ · 移动端第一批施工计划

> **状态：第一批（契约内核）+ 第二批（双基线模板）施工完成（2026-09-10）**，C1-C12 已按用户批准的三步序拍板（规约层 M1-M7 见 [../contract/README.md](../contract/README.md) 决策登记表，本文件不再议规约）。
> 验收记录（2026-09-10 第一批）：**双端契约内核本地全绿——android 16 tests（EnvelopeTest 11 + TraceAndAuthTest 5）/ ios 15 tests（ClientKitTests 单序列化套件）**；覆盖第六节全部条目：信封成功/空 data/业务错四要素/网关 5xx/回显不一致取响应头/非 JSON body/传输失败/取消原生传导/分页与 nextCursor/trace 注入 32hex/不覆盖已有头/Bearer 注入/401 刷新重放一次/刷新失败终态/超时单点。
> 验收记录（2026-09-10 第二批）：**四档模板落位** `clients/create/templates/{android-minsdk26, android-minsdk24, ios17, ios16}`——android 双档 build-logic 四 convention 插件本地编译验证 ✓（全量构建走 CI：本机无 SDK）；iOS 双档 feature 包测试 10/10 绿 ✓（ios17 5 + ios16 5）+ xcodegen 生成 ✓（App target 构建走 CI：本机 Xcode 未 first launch）；CI 双 workflow 增 template-smoke job。
> 验收记录（2026-09-10 第三批·生成器，由并行会话实现 + 本会话补单测/冒烟/CI）：`clients/create/bin/yarch-init-app.mjs`（统一 `--platform android|ios|both|miniprogram|game-cocos`；both = 一次问答、双端身份同源派生 `io.github.ydonghao.<seg>` applicationId = bundle id、registry 五节一次登记）；引擎级单测 `bin/create-app.test.mjs` 4/4 绿；本地生成冒烟三口径 ✓（android/ios/both 零残留占位符）；CI 双 workflow 增 generator-smoke（生成 → 构建兜底模板正确性）。
> 输入：[../contract/clients/](../contract/README.md) 三份规约（唯一权威）· 四栈脚手架范式（java archetype / golang yarch-init / python uvx / web create-admin——同构基准）。
> 注：本文件 C 编号指移动端施工决策，与 M1-M7（规约拍板）、G/D/W/P 系（各栈）无关。
> 本机工具链事实（2026-09-09 探测）：JDK 17/25（sdkman）、**无 Android SDK**、**无 gradle**（wrapper 拉取）、Swift 6.3.3（CommandLineTools，无完整 Xcode）。

## 一、既定约束（来自规约与拍板，本计划不再议）

1. 双端契约内核是 [client-shared.md](../contract/clients/client-shared.md) 的实现凭证：信封解包单点、错误三分类（业务 `ApiError` / 传输 `NetworkError(code=-1)` / **取消原生传导**）、traceId 注入与回显校验、超时单配置点（10/30/30）、认证失效单点刷新重放。
2. 工程范式与机检（catalog / convention plugins / ktlint+detekt+Lint / SwiftLint+SwiftFormat / xcodegen+SPM）约束**生成工程**；契约内核库自身以最小依赖、本地可全绿为先。
3. 施工序（用户拍板）：**① 契约内核 + convention plugins → ② 模板（以自研 App 为底反向沉淀）→ ③ 生成器薄壳**。每步有真实消费者验收。

## 二、与四栈脚手架范式对齐

| 维度 | java | golang | python | web | **clients（本批）** |
|---|---|---|---|---|---|
| 工程形态 | Maven reactor 16 模块 | 单 module 多 package | uv workspace 双发行版 | pnpm workspace 三包 | **android：Gradle 单模块库；ios：SPM 单 package** |
| 平台库 | yarch-java（Central） | yarch-go（module proxy） | yarch-python（PyPI） | @yarch/*（npm） | **yarch-client-android（Central）/ yarch-client-ios（SPM git tag 直引）** |
| 模板资产 | archetype（Velocity） | `_template/`+archetype.json | 随 wheel `_template/` | `packages/create/templates/` | **`clients/create/templates/` 四档（第三批落）** |
| 生成器 | archetype:generate | cmd/yarch-init | yarch-init（Jinja2） | @yarch/create-admin（问答式） | **`yarch init app --platform android\|ios\|both`（第三批，复用 web 引擎范式）** |
| 发版 | tag→java-publish.yml | tag→proxy | tag→PyPI（OIDC） | tag→npm | android：tag→Central（复用 java 设施）；ios：**tag 即发版**（SPM 直引零注册） |

## 三、目标形态（全景）

```
clients/
├── PLAN.md / README.md
├── android/                          # ① yarch-client-android（本批：纯 Kotlin/JVM 库）
│   ├── settings.gradle.kts / build.gradle.kts / gradle/libs.versions.toml + wrapper
│   └── src/{main,test}/kotlin/io/github/ydonghao/yarch/client/
│       ├── model/    RestResponse<T> / Page<T>（nextCursor 空串即无下一页）
│       ├── error/    ApiError / NetworkError(-1)
│       └── http/     HttpConfig(10/30/30 单点) / TraceIdInterceptor(注入+回显校验)
│                    / TokenProvider+AuthInterceptor+RefreshAuthenticator(401 单点重放)
│                    / Envelope.call(解包) / YarchHttp(OkHttp+Retrofit 装配)
├── ios/                              # ② yarch-client-ios（本批：SPM package）
│   ├── Package.swift                 # swift-tools 6.0，Swift 6 严格并发，零第三方依赖
│   ├── Sources/ContractKit/          # RestResponse/Page、ApiError/NetworkError、HttpConfig、
│   │                                 # makeTraceId、ApiClient(get/post/call + 刷新重放一次)
│   └── Tests/ContractKitTests/       # Swift Testing + MockURLProtocol
└── create/                           # ③ 生成器（第三批）：Node 引擎（readline 问答 + {{var}} 渲染
                                      #   + registry 五节校验 + --platform both 双端包标识一致派生
                                      #   + 生成后冒烟），模板四档 android-minsdk26/24、ios17/16
```

## 四、决策 C1-C12

| # | 决策 | 结论 |
|---|---|---|
| C1 | 目录布局 | `clients/android` = Gradle 库工程根、`clients/ios` = SPM package 根、`clients/create` = 生成器（三批落）；与 stacks/「一目录一可构建单元」同构 |
| C2 | android 内核形态 | **纯 Kotlin/JVM 库（零 Android API）**——无 SDK 环境可本地构建全绿；ViewModel/Compose 扩展（`yarch-client-android-compose`）触发式第二模块 |
| C3 | 坐标与包名 | `io.github.ydonghao.yarch:yarch-client-android`（发版复用 java 栈 Central/GPG 设施，tag `clients/android/vX.Y.Z`）；包 `io.github.ydonghao.yarch.client` |
| C4 | android 依赖基线 | Kotlin 2.2.0 / Gradle 8.14（wrapper）/ toolchain JDK 17 / OkHttp 4.12 + Retrofit 2.11（方言表锁定）+ kotlinx-serialization；测试 JUnit5 + MockWebServer + coroutines-test |
| C5 | android API 面 | `RestResponse<T>`/`Page<T>`/`ApiError`/`NetworkError`/`HttpConfig`/`TraceIdInterceptor`/`AuthInterceptor`+`RefreshAuthenticator`/`Envelope.call`/`YarchHttp`（详见第五节映射） |
| C6 | 取消语义 | 双端原生异常传导：Kotlin `CancellationException` / Swift `CancellationError`（URLSession `URLError.cancelled` 归一为 `CancellationError`）；**禁捕获转译** |
| C7 | ios 形态 | swift-tools 6.0、Swift 6 严格并发、Codable + URLSession async/await、**零第三方依赖**；测试 Swift Testing（XCTest 仅 UI test 场景，规约七-1） |
| C8 | CI（本批即建） | `clients-android.yml`（ubuntu，JDK 17/21 矩阵，wrapper build+test）；`clients-ios.yml`（macos，swift test）。ktlint/detekt/SwiftLint 门禁随第二批模板批齐（与模板配置同源，避免双份漂移） |
| C9 | 第二批前置 | Android 模板验证需 Android SDK（CI ubuntu 装 cmdline-tools 即可，本地不装）；iOS 模板 `.xcodeproj` 构建需完整 Xcode（CI macos 有）——**模板正确性由 CI 兜底，本地不强求** |
| C10 | 生成器（第三批） | `clients/create`：Node ≥20 零依赖（对齐 web create-admin.mjs 引擎范式），`--platform android\|ios\|both`；both = 一次问答、一次 registry 登记、双端包标识同源派生；`{{var}}` 渲染 + 残留占位符扫描 + 生成后冒烟（双端 build/test） |
| C11 | 模板示例域 | 登录 + 用户列表（users，对齐 golang/python 模板先例），双端同域——conformance 断言跨端可比 |
| C12 | 术语表 | contract/README 方言表 android/ios 两列随第二批（模板+发版）入表；本批先在第五节登记实现锚点 |

## 五、契约 → 构件映射（client-shared 条文为左键）

| 条文 | android 实现 | ios 实现 |
|---|---|---|
| 一-2/3 信封解包 | `Envelope.call`（2xx/4xx/5xx 一律解析；errorBody 解析失败兜底传输错误） | `ApiClient.call`（同语义） |
| 一-4 错误三分类 | `ApiError` / `NetworkError(CODE=-1)` / `CancellationException` 原生 | `ApiError` / `NetworkError(code=-1)` / `CancellationError` 原生（URLError.cancelled 归一） |
| 一-5 分页 | `Page<T>`（`hasNext`：nextCursor 空串即 false） | `Page<T>.hasNext` 同 |
| 一-6 401 单点 | `RefreshAuthenticator`（priorResponse 防环，刷新失败放行 ApiError） | `onUnauthorized` 闭包重放一次 |
| 二-1/2 traceId | `TraceIdInterceptor`（请求头 32 hex；ApiError 以响应头优先） | `ApiClient` 注入 + 回显校验同源 |
| 三-1 超时单点 | `HttpConfig(10s/30s/30s)` → `OkHttpClient` 三超时 | `HttpConfig` → `timeoutIntervalForRequest`（URLSession 合并 connect+read，注释说明映射） |
| 三-3 取消传导 | suspend + Retrofit（call 随协程取消） | async/await + Task 取消 |
| 三-4 禁明文 | 属模板/清单层（第二批 build-logic） | 同左（ATS 配置） |

## 六、验收口径（第一批）

1. 双端内核本地全绿：android `./gradlew test`（无 SDK 环境可跑）；ios `swift test`。
2. 测试覆盖第五节全部映射条目：信封成功/业务错/传输错/body 非法/取消传导/分页/trace 注入/回显不一致取响应头/超时配置/401 刷新重放。
3. CI 双 workflow 建好（首跑绿以 push 后为准，本地已验证等价命令）。

## 七、已知风险

1. Gradle wrapper 首次下载 130MB + 依赖拉取走网络（公司镜像/中央仓），慢但一次。
2. Swift 6 严格并发下 URLSession/URLProtocol mock 的 Sendable 约束需要细调（MockURLProtocol 用锁盒持有 handler）。
3. 本地无 Xcode：iOS 仅库级验证（`swift test` macOS 平台），App target 编译留 CI。
4. 双端时间线风险：第二批 Android SDK 依赖只在 CI 出现，模板迭代节奏受 CI 排队影响——模板正确性测试尽量下沉到 JVM 层（convention plugins 用单元测试验证配置产物）。

## 八、实施偏差登记

### 第一批（契约内核）

- **iOS 平台基线**：Package.swift 须显式声明 `.iOS(.v15)/.macOS(.v13)`——SPM 无 platforms 声明时部署目标默认 macOS 10.13，async URLSession / CancellationError / value(forHTTPHeaderField:) 全编译不过。库自身最低面宽于消费方基线（ios16/ios17）。
- **iOS 本地测试须完整 Xcode**：CommandLineTools 无 `Testing` 模块；本机跑法 `DEVELOPER_DIR=/Applications/Xcode.app/Contents/Developer swift test`（不动全局 xcode-select；CI macos runner 自带完整 Xcode）。
- **iOS 测试桩并发模型**：MockURLProtocol handler 为全局单桩，`@Suite(.serialized)` 只串行套件内测试、**套件间仍并行**会串台——全部用例合并进单一序列化套件 `ClientKitTests`。
- **Swift 6 严格并发两处**：`Options.traceId` 闭包参数需 `@escaping`；URLProtocol 静态可变 handler 需 `nonisolated(unsafe)` + NSLock（外部同步机制显式化）。
- **`Box<String?>` 双层 Optional 陷阱**（.some(nil)==nil 判假）——测试值盒带初值非 Optional。
- **本机 Maven Central TLS 不稳**（与 java 栈当年同因）：`~/.gradle/init.d/aliyun-mirror.gradle` 追加阿里云镜像（顺序优先、原仓库保留兜底、删文件即回退）——本机全局配置，不进仓。
- **wrapper 发行版下载超时**：本地验证直接用解压版 gradle 同版本跑（`/tmp/gradle-8.14/bin/gradle test`）；`gradlew` 与官方 distributionUrl 保留给 CI（GitHub 网络正常）。
- **retrofit2.Response 没有 newBuilder()**（那是 okhttp3 的）——带响应头断言的用例走 MockWebServer 真路径。
- **JUnit5 assertThrows 的 lambda 非 suspend**——挂起路径用 try/catch + fail。

### 第二批（双基线模板）

- **Compose BOM 无法在 convention 源引入**：kotlin-dsl 的 `platform()` 扩展与 `Category.PLATFORM` 属性写法在 convention 插件源内均不可编译——BOM 依赖声明下放各模块 build.gradle.kts（`platform()` 访问器在 .kts 脚本内天然可用），convention 插件只管 `buildFeatures.compose`。依赖声明本就属模块层职责，不违三-2。
- **targetSdk 在 CommonExtension 星投影下不解析**：application 插件改用 `ApplicationExtension` 强类型配置（library 侧仍走 CommonExtension）。
- **SwiftPM path 依赖身份 = 目录名**（`ios`）而非 package name（`yarch-client-ios`）——`.product(name: "ContractKit", package: "ios")`。
- **iOS feature 包须显式双平台**：本地 swift test 在 macOS 编译，包基线 `.iOS(.v17)+.macOS(.v14)`（onChange 双参 / ContentUnavailableView 等按 macOS 14 对齐）；ios16 档的 17+ API 用单参 onChange 与同构回退布局。
- **SwiftUI Binding 写路径要求 state setter 开放**：`private(set)` 挡 `$vm.state.x` 投影——state 设公开 setter，写语义仍收敛 VM 意图方法（注释声明）。
- **非 throws 函数 do-catch 必须 exhaustive**：`catch is CancellationError` 分支显式落实「取消原生传导」+ 兜底 catch（Kotlin 无此要求，双端条文同源实现有此方言差）。
- **本机 iOS App target 构建不可跑**：Xcode 未 first launch（CoreSimulator.framework 未装，`-runFirstLaunch` 需管理员）——App 壳构建走 CI（与 Android 无 SDK 同姿态）；feature 包逻辑层本地全绿已覆盖规约条文。
- **wrapper 拷贝坑**：`cp -R clients/android/gradle .` 会连带覆盖模板的 libs.versions.toml——只拷 `gradle/wrapper/` 子目录。
- **模板身份**：registry 五节尚空，双端模板暂用中性样例 `io.github.ydonghao.sampleapp`；第三批生成器落地时改为登记 App 名派生。
