# 小程序与游戏端调研 digest（miniprogram · 小游戏，2026-09）

> **定位**：服务于 clients 层两份新规约（`contract/clients/miniprogram.md` / `game.md`）立项的调研与审阅材料。**规范性条文一律以 [../../contract/](../../contract/README.md) 为准**，本组不承载权威条文。架构前提已拍板（2026-09-10）：**游戏与 web 分开**——渲染层两套（web = 微前端规约已有；游戏 = 引擎），契约内核 / 领域逻辑 / design token 三层共享。决策清单（MP1-MP5 / G1-G4）见文末。

## 调研范围与一句话结论

三个与 yarch 直接相关的战场，2026-09 市场状态与含义：

| 战场 | 市场现状（一句话） | 对 yarch 的含义 |
|---|---|---|
| 微信官方规约的地位 | 平台强制规范与性能/体验口径是标杆；**工程范式官方缺位**（无旗舰参考工程），实践分裂为原生 vs 跨端框架 | 不能复制 android/ios「官方风格 + 旗舰参考工程」双锚模式；规约需三段式，范式段自选登记 |
| 小程序技术栈 | 原生（单端/性能敏感）、Taro（React 多端）、uni-app（Vue 多端快速）三分，无「官方最佳」 | 与 M1-M7 native-first 哲学同构：原生 + TS 默认档，跨端按需升级（叠加不推翻） |
| 小游戏引擎与主体 | Cocos Creator 事实主流（TS、一键发小游戏 + H5）；Unity/团结走官方 Wasm 转化（3D 重度档）；**小游戏与小程序是两种独立 AppID 主体**，平台不允许合一 | 「游戏与 web 分开」有一半是平台硬约束而非偏好；引擎双档登记有既有先例（MQ 双轨 / OLAP 双引擎） |

## 一、微信官方规约的真实定位（三层拆解）

**事实**：

- **强制规范层**（不守不让上线）：包体积硬上限（主包 2MB / 总包 30MB 量级 + 分包与独立分包机制，具体数值以官方现行文档为准）、审核类目、隐私接口申明（`getUserProfile` 等逐年收窄）。平台事实标准，无从讨论「最佳」与否。
- **指导指南层**（业界标杆）：性能优化指南（`setData` 最小化、分包预下载、骨架屏）与**体验评分**体系（开发者工具内置），比支付宝/抖音小程序对应文档成熟。体验评分是官方可量化口径——yarch「条文落机检」路线在此域的天然锚点。
- **工程范式层**（官方缺位）：状态管理、组件化、测试、架构，官方只有工具（miniprogram-ci / miniprogram-automator）没有范式，更没有 Now in Android 量级的参考工程。由生态补位：Vant Weapp（组件库事实主流）、mobx-miniprogram 等。

**结论**：以微信官方文档同时充当「风格权威 + 工程范式权威」的 android/ios 式双锚，在 miniprogram 域不成立。规约结构需改为三段式（MP2），范式选择显式化为决策项（MP1）。

## 二、原生 vs Taro vs uni-app（2026 选型事实）

| 方案 | 机制 | 适合 | 代价 |
|---|---|---|---|
| 原生 + TS | 官方运行时直连 | 微信单端、性能/体积敏感、要吃新特性（Skyline 渲染引擎等总是原生先支持） | 多端诉求出现时成本翻倍 |
| Taro（京东） | 运行时方案，React DSL（支持 Vue） | React 团队、复杂业务组件化、H5/RN 扩展 | 运行时开销与包体积损耗 |
| uni-app（DCloud） | 编译时方案，Vue DSL | 多端快速发布（微信+支付宝+抖音+H5+App） | 深度平台特性须条件分支维护 |

多端案例：微信+支付宝+抖音三端，Taro 三周覆盖 vs 原生成本翻倍。

**与 yarch 的关系**：M1-M7 拍板的「native-first + 共享逻辑层」哲学可直接延伸到小程序域——原生 + TS 默认档（MP1），跨端框架不入默认档；将来多端诉求出现时按决策表升级登记，形态与 KMP 之于 android/ios 的关系同构（叠加，不推翻现有拍板）。

## 三、小游戏：主体切分与引擎格局

**事实**：

- **平台主体硬切分**：小游戏与小程序是两种独立主体——独立 AppID、API 面不同（小游戏无 WXML 页面栈，纯 Canvas + wx API 子集）、审核类目不同。「一个微信应用既是小程序又是小游戏」平台不支持。
- **4MB 首包硬约束**是所有引擎方案的第一选型维度。
- **Cocos Creator**：微信小游戏事实主流，TypeScript，构建面板一键发布小游戏 + H5——「游戏也要出 web 版」这一诉求天然覆盖。
- **Unity / 团结引擎**：微信官方 WebGL→Wasm 转化方案，保持原引擎工具链，适合 3D 重度；体积成本在 4MB 首包约束下显著（Wasm + JS 胶水层是全部原生引擎上小游戏的统一底层）。

**与 yarch 的关系**：引擎双档（Cocos 默认 / Unity·团结 3D 重度）与 MQ 双轨、OLAP 双引擎是同一登记模式；Cocos 的 TS 语言面与 web / 小程序三端统一。

## 四、分层共享边界（已拍板，G4 条文化的底稿）

| 层 | 游戏端 | web / 小程序端 |
|---|---|---|
| 契约内核 | 共享 TS 单包（MP4）——网络适配可插拔（`wx.request` / 引擎 HTTP） | 同一包（fetch / `wx.request`） |
| 领域逻辑 | 纯 TS 共享包 | 同一包 |
| design token | 共享 | 共享 |
| 渲染 / 交互 | **游戏引擎（Cocos / Unity），禁与 web 共享** | DOM / 组件树（微前端 / 小程序框架） |

> 辨析：Cocos「一套游戏代码发小游戏 + H5」是游戏自身的多端发布，与「游戏和 web 页面合一」是两回事——web 应用仍是 DOM 范式，不会跑进游戏引擎；强行全 WebGL 画 web 页面（无障碍/输入法/文本排版全塌）或 DOM 硬搓游戏（性能天花板）均为业界公认反模式。

## MP/G 决策清单（2026-09-10 全部拍板，条文已定稿）

> G 系为本 digest 局部编号（G 编号在 PG 规约 ORM 条文、gap-digest、golang PLAN 已三次撞名）；已以「小程序与游戏端规约」单行登记于 contract/README 关键架构决策登记表。

| # | 决策 | 建议 | 状态 |
|---|---|---|---|
| MP1 | 小程序技术栈默认档：**A 原生+TS** / B Taro / C uni-app | A 为默认档——与 M1-M7 native-first 同构；单端依赖最少、体验评分/包体口径官方直连。Taro / uni-app 不入默认档，多端诉求出现时按决策表升级登记 | ✅ 已拍板（A） |
| MP2 | miniprogram.md 三段式结构：通用段（client-shared 协作参考）+ 平台规范段（官方指南/包体/隐私）+ 工程范式段（自选并登记：组件库/状态管理/CI） | 采纳——官方无旗舰参考工程，「范式权威缺位」必须显式化 | ✅ 已拍板 |
| MP3 | 机检锚点：ESLint（TS）+ **官方体验评分进 CI 门禁**（miniprogram-ci 上传/预览管线） | 采纳；体验评分的导出路径（开发者工具 audits vs ci 集成）成文时以官方现行文档核实 | ✅ 已拍板 |
| MP4 | **TS 契约内核单包**：web / miniprogram / game 三端同源（网络层可插拔），发 npm @yarch scope | 建议采纳——TS 生态三端可同源（android/ios 因语言分立各自 ContractKit 是差异正当）；与 N2 契约机器可读出口联动 | ✅ 已拍板（载体 = 既有 @yarch/contract 扩展） |
| MP5 | registry 扩「端登记」：小程序 + 小游戏（名称首段 = 登记服务名，同构 App 登记口径） | 建议采纳，随定稿批落位 | ✅ 已拍板（registry 六节） |
| G1 | 引擎分档：**Cocos Creator 默认档**（轻中度 2D/休闲，TS，一键小游戏+H5）/ Unity·团结 3D 重度档（官方 Wasm 转化） | 建议采纳——双档登记先例（MQ 双轨 / OLAP 双引擎）；4MB 首包约束下入册即默认 Cocos | ✅ 已拍板 |
| G2 | game.md v1.0 范围先窄后宽：强制级只立包体硬线 / 契约接入 / 命名登记；性能口径（DrawCall/内存/加载）参考级 | 建议采纳——游戏形态差异大，先立边界后深化 | ✅ 已拍板 |
| G3 | 契约接入等级：miniprogram / game 同为 client-shared **协作参考级**（定稿批同步扩其约束对象行） | 建议采纳 | ✅ 已拍板 |
| G4 | 分层共享边界条文化为 game.md 首章架构条文：渲染层禁共享；契约内核 / 领域逻辑 / design token 三层共享 | **已拍板（2026-09-10：游戏与 web 分开 + 契约层共享）**，定稿时随批入册 | ✅ 已拍板（game.md 一章） |

## 来源

- 微信官方：[小程序开发文档](https://developers.weixin.qq.com/miniprogram/dev/framework/) · [miniprogram-ci](https://developers.weixin.qq.com/miniprogram/dev/devtools/ci.html) · [小游戏引擎适配概述](https://developers.weixin.qq.com/minigame/dev/guide/game-engine/engine-overview.html) · [Unity/团结引擎转化指南](https://developers.weixin.qq.com/minigame/dev/guide/game-engine/unity-webgl-transform.html)
- [Cocos Creator 发布到微信小游戏（官方文档）](https://docs.cocos.com/creator/4.0/manual/zh/editor/publish/publish-wechatgame.html)
- [2026 小程序跨端框架实战选型：Taro vs uni-app（腾讯云）](https://cloud.tencent.com/developer/article/2667283) · [2026 年做小程序，到底选原生还是框架？（网易）](https://www.163.com/dy/article/KSATAODT055247OE.html) · [uni-app 官方选型评估 23 问](https://uniapp.dcloud.net.cn/select.html) · [2026 跨端开发选型指南](https://www.vi23.com/list_4/334.html) · [为什么 90% 的开发者选了 Cocos（知乎）](https://zhuanlan.zhihu.com/p/2020589771861305207)
