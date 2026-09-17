# AGENTS.md — {{appClassName}} 工程守则

> 本工程由 yarch yarch-init-app（android）生成，契约唯一权威 = yarch 仓 contract/（clients/android.md + client-shared.md + api 四件套）。
> 任何 AI 编码代理改码前先读本文件；条文与直觉冲突时以契约为准，红线改动直接拒绝——机检与 CR 均按此执行。

## 工程地图

| 目录 | 职责 |
|---|---|
| app/ | 壳模块：MainActivity / 导航 / DI 装配（BuildConfig 注入 API_BASE_URL） |
| core/network | 网络层：SampleApi / TokenStore——**HTTP 一律经 yarch-client-android 契约内核** |
| core/designsystem | 主题 token（禁反向依赖 feature） |
| core/common | 共享基础（AppDispatchers / PlatformFeatures） |
| feature/login · users | 功能模块：Screen + ViewModel + Repository（UDF 单向数据流） |
| build-logic/ | convention 插件（yarch.android.application/library/compose/hilt）——机检在此收口 |

## 命令表

| 场景 | 命令 |
|---|---|
| 验证（改完必跑） | ./gradlew build —— ktlint + detekt + Lint + 测试全内置 |
| 单测 | ./gradlew test |

## 红线清单

| 红线 | 规则 | 出处 |
|---|---|---|
| 契约内核单点 | 信封解包 / 错误三分类 / 401 刷新重放 / traceId 只经 yarch-client-android（Envelope.call）；**禁业务模块裸 OkHttp/Retrofit 直连** | client-shared 一/二 |
| 长连接与埋点 | 长连接 / 埋点能力由内核单点提供（realtime 六节 / telemetry 七节），业务只调 track() 与 type 路由 | client-shared 六/七 |
| 分层方向 | ViewModel 禁持 Context；UI 只读 UiState 经事件回调；feature 间禁互依 | android.md |
| 机检组合 | ktlint + detekt + Lint 三重，禁绕过（abortOnError） | android.md 九 |
| targetSdk | ≥36 硬线（Play 2026-08-31 起），convention 内置断言 | android.md 六-1 |
| 命名 | 包标识与应用名登记一致（registry 五节），改名走变更评审 | registry.md |

## 契约锚点

yarch 仓 contract/clients/android.md · client-shared.md · contract/api/——实现与本仓不一致 = 实现 bug。
