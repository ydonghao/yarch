# Android 官方规约解析（google 官方三份 + Now in Android + 阿里手册择优）

> **定位**：服务于 [contract/clients/android.md](../../contract/clients/android.md) 评审的解析材料（来源逐项展开、取舍理由、正反例）。**规范性条文一律以 [../../contract/](../../contract/README.md) 为准**，本文件不承载权威条文。
> 调研与拍板：2026-09-09（M1-M7 决策清单，唯一登记处 contract/README.md 关键架构决策登记表）。

## A. 官方风格：Android Kotlin style guide + Kotlin coding conventions

- 出处：[developer.android.com/kotlin/style-guide](https://developer.android.com/kotlin/style-guide)（Google 的 Android Kotlin 编码标准）与 [kotlinlang.org coding-conventions](https://kotlinlang.org/docs/coding-conventions.html)（JetBrains 语言级约定）。Android 版 = JetBrains 基础 + Android 特有差异，两份配合读。
- 与 yarch 的关系：**引用级条文**（android.md 一-2）——yarch 不复制、不改写其内容，机检以 ktlint `android_studio` code style 对齐官方 IDE 默认格式化结果。禁止团队另行发明风格（分歧以 IDE 格式化结果为仲裁，android.md 一-5）。
- 要点（均由 ktlint 承接为机检）：4 空格缩进；行宽 100；通配符 import 禁用；KDoc 规范。

## B. 官方架构：Guide to app architecture

- 出处：[developer.android.com/topic/architecture](https://developer.android.com/topic/architecture)、[ViewModel overview](https://developer.android.com/topic/libraries/architecture/viewmodel)、参考工程 [android/architecture-samples](https://github.com/android/architecture-samples)。
- 核心主张（已条文化为 android.md 二）：
  - **UDF 单向数据流**：状态下降、事件上行；UI 是状态的函数；
  - **ViewModel 是屏幕级状态容器**：暴露单一 `UiState`、封装业务逻辑、configuration change（旋转）下存活；
  - **分层 ui / domain / data**：domain 按需（官方也明确"简单工程可不加 domain 层"→ yarch 取 ui/data 起步、domain 可选，android.md 二-5）；
  - **Repository 是数据唯一来源**：多来源（远程+缓存）合并收敛其内；
  - 状态暴露的现代口径：StateFlow（LiveData 是历史承接，新代码不用 → android.md 七-3）。
- yarch 的增量：官方不约束"谁持有登录态刷新"，yarch 按基座单点同源逻辑收进认证模块（client-shared 一-6）。

## C. 阿里巴巴Android开发手册（v1.0.1）逐块取舍

- 出处：[阿里云课程页](https://edu.aliyun.com/course/813)、[电子版](https://developer.aliyun.com/article/1151408)；配套 Java 手册（yarch 已消化，[alibaba-java-manual-digest.md](alibaba-java-manual-digest.md)）。
- 总判断：**Java/View 时代的产物**——Kotlin、Compose、协程零覆盖；平台层原则（与语言无关者）仍有效。定位与 MySQL 章相同：digest 择优，不作主融合对象（主融合对象 = Google 官方，M 系拍板）。

| 手册部分 | 取舍 | 去向 |
|---|---|---|
| Java 语言规范 | **不纳入** | 由官方 Kotlin 规约承接（android.md 一）；Java 手册本身 yarch 已消化 |
| 资源文件命名与使用 | **纳入**（本 digest 唯一整块吸收） | android.md 一-3：drawable 前缀 `ic_/bg_/img_`、string `{模块}_{语义}` 禁裸通用词、公共组件资源模块前缀 |
| Android 基本组件（生命周期/启动模式/广播） | **原则吸收，不复制条文** | 生命周期纪律经官方架构指南（ViewModel 职责）承接；启动模式/广播细则以官方文档为准，不入 yarch 条文 |
| UI 与布局 | **基本过时**（XML/View 时代） | Compose + M3 承接（android.md 四）；存量 XML 命名前缀降为【推荐】仅维护 |
| 进程、线程与消息通信 | **原则吸收** | "主线程禁 IO"入条文（android.md 七-4，StrictMode 机检）；Handler/Looper 细则协程时代不单列 |
| 文件与数据库 | **原则吸收** | 存储选型走官方 storage 文档与 yarch infra 规约（客户端本地库未立规约，触发式） |
| 动画/图片/安全等其余 | 部分原则有效 | 权限最小化已由 Play 政策 + HIG 级条文承接（android.md 十-3）；其余不入 |

## D. Now in Android 工程范式逐项

- 出处：[github.com/android/nowinandroid](https://github.com/android/nowinandroid)（Google 旗舰参考 App，仍活跃维护；2026-09 核实未被归档）。它是「现代 Android 开发」文档的可运行印证，yarch 工程结构条文（android.md 三）的范式来源。
- 逐项吸收表：

| NIA 实践 | yarch 取舍 | 条文 |
|---|---|---|
| version catalog（`gradle/libs.versions.toml`）集中全部版本 | **吸收**：字符串版本号出现即缺陷 | 三-1 |
| `build-logic/` convention plugins（Application/Library/Compose/Hilt…） | **吸收并 yarch 化**：`yarch-android-*` 四件，机检/minSdk/工具链统一定义于内 | 三-2 |
| 模块分组 `:app` + `:core:*` + `:feature:*` | **吸收**：一域一模块、feature 间禁互依 | 三-3 |
| ktlint + detekt + Android Lint 三重全绿门槛 | **吸收**：CI 门禁，禁只跑其一 | 九-1 |
| Robolectric JVM 化测试 | **吸收为【推荐】**：真机冒烟放每日腿 | 八-4 |
| R8 默认开启、混淆规则随模块 `consumer-rules` | **吸收**：禁巨型集中 proguard | 九-3 |
| Baseline profile / macrobenchmark | **暂不吸收**：性能优化项，不进 v1 规约（观察项） | — |
| Firebase / GTM / Play 广告位 | **不吸收**：业务耦合与依赖注入非规约范畴 | — |

## E. 发布政策时间线（targetSdk 硬线依据）

- 出处：[Play target API 政策](https://support.google.com/googleplay/android-developer/answer/11926878)。
- 时间线：2025 年（更新须 target API 35+）→ **2026-08-31 起：新 App 与更新必须 target Android 16（API 36）+**（手机/平板/Auto；Wear 另有节奏）。
- 对 yarch 的含义：规约落稿日（2026-09-09）已在此政策生效之后，`targetSdk >= 36` 是上架硬线而非目标（android.md 六-1），由 convention plugin 断言（创建期 + CI 双卡）。

## F. 机检映射（对齐 contract/README.md 机检路线）

| 工具 | 承接条文 | 备注 |
|---|---|---|
| ktlint（`android_studio` code style） | 一-2 风格全文 | 官方 IDE 默认即仲裁，零风格争论 |
| detekt（+ Compose 规则集插件） | 二-4 / 五-1（依赖与单例纪律的补充） | config 随模板入仓 |
| Android Lint（fatal 全转错误） | 一-3 资源命名、六-2（24 档 `NewApi` guard）、十-3 权限 | baseline 一次性、两迭代清零（九-2） |
| 依赖/结构扫描脚本 | 三-1 catalog 唯一、三-3 模块形状、四-3 硬编码 token、十-1 签名不落盘 | 模板 CI 内置 |

## G. 结论

Google 官方三份 + NIA 构成「风格—架构—工程范式」完整闭环且全部可机检，是 Android 侧唯一主融合对象；阿里手册仅资源命名整块吸收、其余原则级消化。解析与拍板已闭环，本 digest 转为条文溯源材料（改动 android.md 时先读本文件对齐来源）。
