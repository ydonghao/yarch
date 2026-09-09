# 客户端共享契约（v1.0 已定稿）

> **状态：已定稿**（2026-09-09 经 M1-M7 决策清单拍板，`contract/clients/` 规约组首份）。
> 等级：**【强制】**违反即缺陷；**【推荐】**默认遵守、评审后可豁免；**【参考】**正向引导。
> 约束对象：消费 yarch 体系 API 的**原生移动客户端**（[android.md](android.md) / [ios.md](ios.md) 两份方言规约的前置）。desktop / miniprogram 消费同一 API 时按本规约**协作参考级**对齐。
> 本规约平台无关；与后端的信封 / 错误码 / traceId 口径为协作参考级——项目未采用 api 四件套时按项目实际替代（降级语义同 [../README.md](../README.md)），其余条文效力不变。

## 一、信封解包与错误模型

1. 【强制】信封解包职责**唯一收敛在契约内核库**（android 侧 `yarch-client-android`、iOS 侧 `yarch-client-ios`）：业务代码只见强类型结果 / 异常，**禁在任何业务模块手解 `code/message/data` JSON**——两处解包就是两套错误语义。
2. 【强制】HTTP 状态码只是传输层信号：`2xx/4xx/5xx` **一律解析 body**（信封恒在，网关故障面也保证，见 [../api/rest-response.md](../api/rest-response.md)）；body 非法 / 不可解析（含连接被 reset）→ 传输层错误。
3. 【强制】解包规则：`code == 0` 返回 `data`（反序列化为 `T | null`）；`code != 0` 抛 `ApiError`，携带 `code / message / traceId / httpStatus` 四要素，**缺 traceId 的 ApiError 是缺陷**（排障凭证丢失）。
4. 【强制】错误三分类，语义不得混淆：
   - **业务错误 `ApiError`**：服务端可预期拒绝（错误码表 [../api/error-codes.md](../api/error-codes.md)），可映射为用户可读提示；
   - **传输错误**：超时 / DNS / 断网 / TLS 失败 / body 不可解析，`code = -1`（本地保留码，不出网），提示口径统一为网络类文案；
   - **取消（Cancellation）**：**不是错误**——禁捕获后转译为 ApiError、禁吞掉不向上抛，必须原样传导让调用方作用域正常收尾。
5. 【强制】分页负载解包为 `Page<T>`，形状对齐 [../api/rest-response.md](../api/rest-response.md)：`list`（可空数组禁 null）、`total`、`page`（1-based）、`pageSize`、`nextCursor`（空串即无下一页）；分页参数越界返回空页而非报错（D6）。
6. 【强制】认证失效（`2001/2002`）由认证拦截层**单点**处理：刷新凭证 → 重放原请求；刷新失败 → 清态并路由到登录。业务层收到的是重试后的结果或终态 `ApiError`，**禁在业务层各自捕获 401 跳登录**（对齐微前端基座单点纪律 [../web/micro-frontend.md](../web/micro-frontend.md) 十-2 的同源逻辑：跳转权唯一）。
7. 【推荐】写操作自动重试**一律禁止**（防双写）；人工触发的显式重试必须携带幂等键（幂等口径对齐 [../api/rest-conventions.md](../api/rest-conventions.md)，服务端 `1007` 幂等冲突按可提示错误处理）。

## 二、traceId 与可观测

1. 【强制】每个出站请求注入 `X-Trace-Id` 请求头（32 位小写 hex，本地生成一次、会话内复用，见 [../api/logging-trace.md](../api/logging-trace.md) 跨进程传播矩阵"前端"行：无 span 语义的客户端至少带 `X-Trace-Id`）。
2. 【强制】响应头 `X-Trace-Id` 与信封 `traceId` 恒等；两者不一致时以响应头为准记入错误对象（服务端 bug 信号）。
3. 【强制】错误上报 / 崩溃报告 / 用户报障单**必须携带 traceId**，并附 App 版本、平台、构建号——traceId 是客服与后端排障的对接凭证。
4. 【强制】客户端日志禁输出敏感明文：token、密码、手机号明文一律脱敏；debug 级日志不进生产构建。
5. 【推荐】本地日志结构化（时间 / 级别 / 模块 / msg / traceId），为接入远端日志通道预留字段形状（对齐 ndjson 字段口径 [../api/logging-trace.md](../api/logging-trace.md)）。

## 三、网络纪律

1. 【强制】超时配置全 App **一个配置点**（契约内核库持有）：默认连接 10s / 读 30s / 写 30s，业务模块禁私自改小改大；确需例外的长请求（上传）走显式 override 并登记。
2. 【强制】自动重试仅限幂等读（GET），最多 1 次、指数退避；其余见一-7。
3. 【强制】请求生命周期跟随调用方作用域：页面离开 / 协程取消 / Task 取消必须传导到网络层（取消传导是 UI 层硬义务，见方言规约）。
4. 【强制】禁明文：Android `usesCleartextTraffic=false` 口径、iOS ATS 默认口径，双端生产构建一律禁 http 明文与自签绕过；**证书校验禁关闭**——debug 构建临时放行须在业务仓 `docs/waivers.md` 登记（对齐豁免流程 [../README.md](../README.md)）。
5. 【强制】环境（base URL / 环境标识）由构建配置注入，禁硬编码、禁生产包含隐藏环境切换入口；环境清单与口径对齐 registry 所属项目声明。

## 四、App 命名与登记

1. 【强制】App 名格式同服务名（`^[a-z][a-z0-9-]{1,31}$`），由「**已登记服务名-用途**」两段构成（如 `ysaas-companion`）；首段必须能在 [../registry.md](../registry.md) 二节查到属主。App 是服务在终端空间的延伸，与服务名空间同构。
2. 【强制】同一 App 双端包标识**必须一致且互为派生**：`applicationId`（Android）与 bundle id（iOS）从 App 名按各平台惯例派生（方言规约定式），双端不一致即登记缺陷（推送 / 分享 / 深链统一受影响）。
3. 【强制】App 登记于 [../registry.md](../registry.md) 五节；一经登记并上架（产生商店资源、推送证书）即冻结，改名走变更评审。

## 五、双端概念同构（方言映射表）

> 本表是两份方言规约的**概念对齐基线**：同一概念、各平台惯用实现；条文写概念，方言写实现。双方言条文不得发明本表之外的概念分叉。

| 概念（本规约术语） | android 方言（[android.md](android.md)） | ios 方言（[ios.md](ios.md)） |
|---|---|---|
| 契约内核 | `yarch-client-android`（Kotlin 库，Maven Central） | `yarch-client-ios`（SPM package，git 直引） |
| UI 框架（声明式唯一） | Jetpack Compose + Material 3 | SwiftUI + HIG |
| 状态容器（UDF） | ViewModel + StateFlow `UiState` | @Observable ViewModel（16 档 ObservableObject） |
| 数据层 | Repository（suspend / Flow） | Repository（async / await） |
| 依赖注入 | Hilt（构造注入） | 构造注入（原生，无框架） |
| 序列化 | kotlinx.serialization | Codable |
| 网络栈 | Retrofit / OkHttp | URLSession |
| 机检组合 | ktlint + detekt + Android Lint | SwiftLint + SwiftFormat |
| 测试 | JUnit（+ Robolectric / Compose test） | Swift Testing（+ XCTest UI test） |
| 工程生成 | Gradle version catalog + convention plugins | xcodegen + SPM |

## 六、可机检条文清单

> 对齐 [../README.md](../README.md)「机检路线」。落地载体为契约内核库与两栈模板 CI（实现节奏见 clients/android、clients/ios 各自 README，触发式登记）。

| 条文 | 机检手段 | 阶段 |
|---|---|---|
| 一-1 解包单点 | 依赖解析：业务模块禁直接依赖裸 HTTP 客户端（android: Retrofit 直引；ios: URLSession 直引） | CI |
| 一-4 取消不吞 | 契约内核单测（取消传导用例）+ lint 捕获取消异常的模式告警 | CI |
| 一-6 401 单点 | 方言规约依赖解析：登录路由禁散落（android: 非 auth 模块引登录 Activity；ios: 非 Auth feature 引 LoginView） | CI |
| 二-1 X-Trace-Id 注入 | 契约内核内置（单测断言请求头） | 单测 |
| 三-1 超时单配置点 | 配置文件解析：超时字面量只允许出现在契约内核配置 | CI |
| 三-4 禁明文 | 平台配置检查（`usesCleartextTraffic` / ATS exception） | CI |
| 三-5 环境注入 | 字面量扫描：业务代码禁出现 `http(s)://` 硬编码（白名单：文档链接展示） | CI |
| 四-1 App 名首段 = 登记服务名 | 生成器校验（registry 表核对，同 create-admin 先例） | 创建期 |

---

## 附：来源与拍板记录

- 市面规约调研结论（2026-09-09）：Android 权威收敛于 Google 官方三份 + Now in Android 参考工程；iOS 权威 = 官方 API 设计指南 + 社区 Airbnb 风格（配置开源可机检）；国内阿里 Android 手册为 Java 时代产物仅平台层条文择优。解析见 [../../docs/references/android-official-guides-digest.md](../../docs/references/android-official-guides-digest.md)、[../../docs/references/ios-official-guides-digest.md](../../docs/references/ios-official-guides-digest.md)。
- 拍板记录（唯一登记处：[../README.md](../README.md) 关键架构决策登记表 M 行，此处为引用）：2026-09-09 M1-M7——落位三份 / iOS=Airbnb+SwiftLint+SwiftFormat / iOS 工程生成=xcodegen / min 双基线分文件夹（android minsdk26 默认+24 扩展、ios 17 默认+16 扩展）/ Android DI=Hilt / iOS 架构=MVVM+@Observable。
- 401 单点与取消非错误：分别同源自微前端基座单点纪律（十-2）与各栈结构化并发通行实践；`code=-1` 本地保留码沿用 web 栈 http 封装先例（传输层错误兜底）。
