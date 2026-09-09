# Android 客户端开发规约（v1.0 已定稿）

> **状态：已定稿**（2026-09-09 经 M1-M7 决策清单拍板）。
> 等级：**【强制】**违反即缺陷；**【推荐】**默认遵守、评审后可豁免；**【参考】**正向引导。
> 前置依赖（硬）：[client-shared.md](client-shared.md)——本文件只写 Android 方言条文，概念与跨平台契约以它为准。
> 风格权威：Google 官方（[Android Kotlin style guide](https://developer.android.com/kotlin/style-guide) + [Kotlin 官方 conventions](https://kotlinlang.org/docs/coding-conventions.html)）；工程范式权威：[Now in Android](https://github.com/android/nowinandroid)（Google 旗舰参考工程）。来源解析见 [digest](../../docs/references/android-official-guides-digest.md)。
> 约束对象：yarch 体系全部 Android 原生工程（Kotlin + Compose 技术栈）。存量 View/XML 工程仅协作参考。

## 一、语言与风格

1. 【强制】Kotlin 是唯一开发语言：新文件禁 Java；存量 Java 仅允许原地维护，新增功能一律 Kotlin。
2. 【强制】代码风格以官方 Kotlin 风格指南为准（4 空格缩进、通配符 import 禁用、行宽 100），由 ktlint 落机检（九-1），**禁另行发明团队风格**。
3. 【强制】资源命名（平台层条文，源头为阿里 Android 手册择优，见 digest 附 C）：

| 资源 | 规则 | 正例 |
|---|---|---|
| drawable / mipmap | `ic_`（图标）/ `bg_`（背景）/ `img_`（位图）前缀 + 语义 | `ic_send`、`bg_login` |
| string | `{模块}_{语义}`，禁裸通用词 | `login_title_ok` ✅ `ok` ❌ |
| color / 尺寸 / 字体 | 一律走 designsystem token（四-3），散落字面量即缺陷 | `color.brand.primary` |
| 存量 XML layout | `activity_` / `fragment_` / `item_` / `dialog_` 前缀（【推荐】仅存量维护） | `activity_login.xml` |

4. 【强制】公共组件模块内的资源加模块前缀防跨模块冲突（如 `ds_` 前缀 designsystem 资源）。
5. 【参考】命名细节（lambda 参数、返回表达式简化等）遇争议以官方 IDE 默认格式化结果为准，不人工纠缠。

## 二、架构（官方 Guide to app architecture 口径）

1. 【强制】单向数据流（UDF）：状态下降（state）、事件上行（event）；**UI 不直接改状态，只发事件**——Compose 界面是状态的纯函数。
2. 【强制】每屏一个 ViewModel 作为**唯一状态容器**：`data class XxxUiState` 单一可信状态源（含 loading / error 态），配一次性事件通道（SideEffect）承载导航 / Toast；散落多个 LiveData/StateFlow 拼装界面即缺陷。
3. 【强制】ViewModel 禁引用 Android UI 框架类型（Context 例外仅 `@ApplicationContext`）；可测性优先（八）。
4. 【强制】Repository 是数据唯一来源：ViewModel 只依赖 Repository 接口，**禁直接持有 Retrofit / Room / DataStore**；单数据源多来源（远程 + 本地缓存）的合并逻辑收敛在 Repository 内。
5. 【强制】分层 `ui / data` 两层起（domain 层按需引入，简单工程不强制）：`ui`（Compose + ViewModel）→ `data`（Repository + 数据源）；依赖方向单向，禁反向。
6. 【强制】登录态持有与刷新收敛在认证模块单点（client-shared 一-6）；业务模块经注入的取凭证端口拿 token。

## 三、工程结构（Now in Android 范式）

1. 【强制】依赖版本**唯一**收敛在 version catalog（`gradle/libs.versions.toml`）：任何 `build.gradle.kts` 出现字符串版本号即缺陷。
2. 【强制】共享构建逻辑**唯一**收敛在 `build-logic/` convention plugins（`yarch-android-application` / `yarch-android-library` / `yarch-android-compose` / `yarch-android-hilt`）：模块 `build.gradle.kts` 只 apply plugin + 声明依赖，**禁复制粘贴编译配置**——机检（九-1）、minSdk、targetSdk、Java 工具链全部在 convention plugin 里统一定义。
3. 【强制】模块分组命名标准化：`:app`（组装壳，禁业务逻辑）+ `:core:{network,designsystem,common,datastore}`（横切能力）+ `:feature:{业务域}`（一域一模块）；feature 模块间禁互相依赖，公共逻辑下沉 core。
4. 【强制】工程由脚手架一行命令创建（`yarch init android <app名>`），模板分基线文件夹（六-2）；`cp -r` 既有工程当模板不可接受（对齐全局脚手架铁律）。
5. 【推荐】feature 模块对外只暴露入口路由（navigation 契约），内部实现不可见——为将来模块化装配留缝。

## 四、UI（Compose + Material 3）

1. 【强制】Jetpack Compose 是唯一 UI 框架：新页面禁 XML；存量 XML 仅原地维护，重写时迁移。
2. 【强制】Material 3 组件体系（M3，含 Expressive 演进）；禁混用 Material 2 组件。
3. 【强制】设计 token（颜色 / 排版 / 形状 / 间距）集中 `:core:designsystem`，以 M3 主题扩展暴露；**Compose 代码出现硬编码 `Color(0xFF...)` / 字号字面量即缺陷**（一-3 同源）。
4. 【强制】列表用 Lazy 系列 + `key` 参数（稳定性）；禁在 Composable 内做耗时计算（放 ViewModel / remember derivedState）。
5. 【推荐】每个复用组件配 `@Preview`；深色模式从 designsystem token 天然获得，禁按主题手写两套颜色逻辑。

## 五、依赖注入（Hilt）

1. 【强制】Hilt 是唯一 DI 框架（M5 拍板）：`@HiltViewModel` + 构造注入为准；**禁手写单例（`object` + 静态态）/ ServiceLocator / 自建容器**与 Hilt 并存。
2. 【强制】绑定声明唯一收敛在所属模块的 `@Module`；`@Inject` 构造优先，接口才走 `@Binds`。
3. 【强制】作用域纪律：默认不限定作用域；确需 `@Singleton` / `@ActivityRetainedScoped` 须能说明生命周期理由（评审项）。
4. 【参考】Koin 等其他方案不入册（选型记录见 [../README.md](../README.md) M 行）。

## 六、基线与兼容（双档）

1. 【强制】`targetSdkVersion >= 36`（Android 16）——Google Play 自 2026-08-31 起新 App 与更新**必须**满足，这是上架硬线而非风格偏好。
2. 【强制】min 基线双档，**模板按文件夹分档**、创建期选定、同一工程禁中途混档：

| 档 | minSdk | 定位 | 差异条文 |
|---|---|---|---|
| `minsdk26` | 26（默认档） | 面向当代分发，无兼容包袱 | 直接使用 notification channel 等无 guard |
| `minsdk24` | 24（扩展档） | 存量低端机覆盖 | `Build.VERSION` guard 强制用于 26+ API（lint `NewApi` 全开）；启用 core library desugaring |

3. 【强制】`compileSdk` 跟随当前 stable（由 convention plugin 统一定义，业务工程禁改）。
4. 【推荐】`minSdk` 下调（26 → 24）视为基线变更评审项：涉及 guard 面、desugaring、依赖最低 API 的全量核查，不走随手改。

## 七、协程与数据流

1. 【强制】结构化并发：协程生命周期归属于作用域（`viewModelScope` / `lifecycleScope`）；禁 `GlobalScope`、禁裸 `Thread`。
2. 【强制】`Dispatchers` 经注入使用（构造参数默认值形式），禁在业务逻辑硬编码切换——保证单测可替换（八）。
3. 【强制】UI 态用 `StateFlow`，事件用 `Channel`/`SharedFlow`（一次性语义）；禁 LiveData 新代码（历史 API，官方口径已由 StateFlow 承接）。
4. 【强制】主线程禁 IO / 网络请求（StrictMode 全开作为 CI 冒烟断言之一）。

## 八、测试

1. 【强制】测试金字塔分层：ViewModel / Repository 单测（JUnit + coroutines-test + turbine）为基础盘；Compose UI 测试（`createComposeRule`）覆盖关键交互；忽略任何一层都视为覆盖缺口。
2. 【强制】测试命名以行为描述为准（反引号自然语言或 `should_` 风格，模板统一一种）；断言失败信息可直接读出违规行为。
3. 【强制】契约内核库（`yarch-client-android`）自身测试覆盖 client-shared 全部机检条目（信封解包 / 错误三分类 / 取消传导 / traceId 注入）——它是共享契约在 Android 侧的实现凭证。
4. 【推荐】Robolectric 用于 JVM 化的 Android 依赖测试；真机 / 模拟器冒烟进 CI 每日腿而非每 PR。

## 九、机检与构建

1. 【强制】三重机检，CI 全绿是合并门槛，禁只跑其一：

| 工具 | 职责 | 配置 |
|---|---|---|
| ktlint | 格式（code style `android_studio` 档，对齐官方 IDE 默认） | convention plugin 内置 |
| detekt | 静态分析（复杂度 / 代码味，含 Compose 规则集） | config 入仓，随模板生成 |
| Android Lint | 平台正确性（NewApi / 资源 / 清单） | 全 fatal 转错误；baseline 仅一次性、复审即清 |

2. 【强制】lint baseline 禁常驻：新 baseline 必须附清理计划与期限（两个迭代内清零），防 baseline 变成豁免垃圾场。
3. 【强制】R8 默认开启（minify + shrink），混淆规则随 feature 模块自带（`consumer-rules`），禁集中一个巨型 proguard 文件随 App 膨胀。

## 十、发布与合规

1. 【强制】签名 keystore 不入仓：CI secrets 管理（对齐发版基础设施口径）；`signingConfig` 禁硬编码路径密码提交。
2. 【强制】`versionCode` 全局单调递增、`versionName` 语义化；商店渠道与构建变体（flavor）按需引入，禁为环境切换建 flavor（环境注入走三-5 client-shared 口径）。
3. 【强制】权限最小化：运行时权限须先 rationale 后申请；禁申请「以后可能用到」的权限；未使用 API 的权限声明即缺陷。
4. 【强制】Play 数据安全表与实际收集行为一致（收集项变更联动表单更新）；隐私政策链接常驻 App 内可达。
5. 【推荐】发布轨道：internal → closed → production 分阶段放量（staged rollout），崩溃率阈值自动暂停。

## 十一、可机检条文清单

> 对齐 [../README.md](../README.md)「机检路线」；落地载体为 convention plugins + 模板 CI + `yarch-client-android` 单测。

| 条文 | 机检手段 | 阶段 |
|---|---|---|
| 一-2 风格 / 通配符 import | ktlint（模板预接线） | CI |
| 一-3 资源命名前缀 | lint 自定义检查或脚本扫描资源目录 | CI |
| 二-4 ViewModel 禁持数据源 | 依赖解析（VM import Retrofit/Room 即报） | CI |
| 三-1 版本唯一 catalog | 脚本扫描 `build.gradle.kts` 字符串版本字面量 | CI |
| 三-3 模块分组命名 | settings.gradle 结构校验（`:core:*` / `:feature:*` 白名单形状） | 创建期 + CI |
| 四-3 禁硬编码 token | lint / 脚本扫描 `Color(0x` 与字号字面量 | CI |
| 五-1 禁手写单例并存 | detekt 自定义规则（`object` 含可变态告警） | CI |
| 六-1 targetSdk ≥ 36 | convention plugin 断言 | 创建期 + CI |
| 七-4 主线程禁 IO | StrictMode 全开 + 冒烟 | CI 冒烟 |
| 九-2 baseline 存在性 | CI 脚本：baseline 文件存在即过期告警 | CI |
| 十-1 签名不落盘 | 仓库扫描（keystore / 密码字面量） | CI |

---

## 附：来源与记录

- 风格 / 架构 / UI：Google 官方（Kotlin style guide、Guide to app architecture、Material 3 in Compose）；工程范式（catalog / convention plugins / 模块分组 / 三重机检 / R8）：Now in Android 参考工程。逐项解析与链接见 [android-official-guides-digest.md](../../docs/references/android-official-guides-digest.md)。
- 平台层条文（资源命名 / 权限 / 组件纪律）：《阿里巴巴Android开发手册》v1.0.1 择优吸收——其 Java 语言部分不纳入（由官方 Kotlin 规约承接），取舍明细在 digest 附 C。
- targetSdk 36 硬线：Google Play target API 政策（2026-08-31 生效）。
- 拍板记录：2026-09-09 M1-M7（Hilt / minsdk 双档分文件夹 / 融合基准 Google 官方），唯一登记处 [../README.md](../README.md) 关键架构决策登记表。
