# 小程序客户端开发规约（v1.0 已定稿）

> **状态：已定稿**（2026-09-10 经 MP1-MP5 决策清单拍板）。
> 等级：**【强制】**违反即缺陷；**【推荐】**默认遵守、评审后可豁免；**【参考】**正向引导。
> 前置依赖（协作参考级）：[client-shared.md](client-shared.md)——本域按其口径对齐（信封解包 / 错误三分类 / traceId / 超时单点 / 认证失效单点）；client-shared 的硬前置是 android / ios 方言规约，对本域为协作参考（其条文一至三的语义照搬，载体换成 TS 契约内核）。
> 风格权威：微信官方开发文档（框架 / 性能优化 / 体验评分）；语言权威：TypeScript 官方。**工程范式无官方旗舰参考工程**（与 android/ios「官方风格 + 旗舰参考工程」双锚模式的差异，MP2 显式化）：范式条文为本规约自设，组件库 / 状态管理在工程 README 登记自选，登记即审计线索。
> 约束对象：yarch 体系全部微信小程序原生工程（原生框架 + TypeScript）。Taro / uni-app 跨端工程协作参考（一-3 升级口径）。
> 来源解析见 [miniprogram-game-digest.md](../../docs/references/miniprogram-game-digest.md)。

## 一、技术栈与工程范式（三段式之范式段）

1. 【强制】**原生 + TypeScript 是唯一入册技术栈**（MP1）：页面 / 组件为 WXML / WXSS + TS，新工程禁 JavaScript 裸写；`tsconfig` `strict: true` 硬开，`any` 显式豁免须注释理由。
2. 【强制】构建与发布管线唯一走 **miniprogram-ci**：版本号由 CI 语义生成（git tag / 流水线号派生），CI 预览 → 上传；生产版本禁开发者工具手工上传——上传记录不可追溯即缺陷。
3. 【强制】**跨端框架（Taro / uni-app）不入默认档**：出现微信之外平台诉求（支付宝 / 抖音 / H5 多端）时，先在 [../README.md](../README.md) 关键架构决策登记表升级登记再引入——升级是叠加不推翻，本规约三、四章条文对生成产物同样适用（信封解包 / traceId / 命名登记）。
4. 【推荐】工程范式自选登记（MP2）：组件库默认 **Vant Weapp**（禁自研通用 UI 层）；状态管理默认页面内 `setData` 最小化，跨页面共享态引入 mobx-miniprogram 或等价轻量方案——**选型与理由登记在工程 README**。
5. 【参考】三方依赖走官方构建 npm（miniprogram-ci `packNpm`）；禁 vendor 拷贝源码进工程（升级面失控）。

## 二、平台规范段（包体 / 隐私 / 环境）

1. 【强制】包体预算：**主包 ≤ 2MB、整包 ≤ 30MB**（以微信现行口径为准）；按业务域分包（`subpackages`），启动非必需页面全部下沉分包并配置分包预下载。
2. 【强制】隐私合规：涉隐私接口（`getUserProfile` / `chooseAddress` 等）须 app.json 声明并对齐官方现行隐私检查要求；实际采集项与隐私政策一致（同源纪律对齐 [android.md](android.md) 十-4）。
3. 【强制】环境注入（对齐 client-shared 三-5）：base URL / 环境标识由构建配置注入，业务代码禁 `http(s)://` 硬编码（白名单：纯展示文案链接）；request 合法域名清单随工程 README 登记，与环境一一对应。
4. 【推荐】**体验分门禁**：CI 体验评分 ≥ 90 分（官方体验评分口径），低于线即红（MP3；导出路径以官方现行文档核实）。

## 三、契约接入段（client-shared 协作参考落地）

1. 【强制】信封解包**唯一收敛在 TS 契约内核**（`@yarch/contract` 三端同源，MP4）：业务代码只见强类型结果 / `ApiError`，**禁在任何页面手解 `code/message/data`**。
2. 【强制】网络层可插拔：内核 transport 默认 fetch **不适用小程序域**——工程装配时注入 **wx.request 适配器**，超时（连接 10s / 读写 30s）由适配器统一持有（client-shared 三-1 单配置点），业务模块禁私自传 `timeout`。
3. 【强制】错误三分类语义照搬 client-shared 一-4：业务错误 `ApiError`（`code / message / traceId / httpStatus` 四要素，缺 traceId 即缺陷）；传输错误本地保留码 `-1`（断网 / 超时 / body 不可解析，提示口径统一网络类文案）；**取消（RequestTask abort）不是错误**——原样传导禁转译禁吞。
4. 【强制】`X-Trace-Id` 注入由适配器单点完成（32 位小写 hex，本地生成一次会话内复用，client-shared 二-1）；错误上报 / 用户报障必带 traceId + 版本号。
5. 【强制】认证失效（`2001/2002`）单点：适配器拦截层刷新凭证 → 重放原请求一次；刷新失败清态并路由登录。**禁各页面各自捕获 401 跳登录**（client-shared 一-6）。
6. 【强制】凭证存储走 wx storage 且 key 以「已登记服务名」前缀隔离（防宿主生态串号；前缀口径对齐 [../registry.md](../registry.md) 一名三用哲学），token 明文禁入日志（client-shared 二-4）。

## 四、命名与登记（MP5）

1. 【强制】小程序名格式同服务名（`^[a-z][a-z0-9-]{1,31}$`），由「**已登记服务名-用途**」两段构成（如 `ysaas-companion`）；首段必须能在 [../registry.md](../registry.md) 一、二节查到属主；登记于 [../registry.md](../registry.md) 六节，AppID 属主同表。
2. 【强制】小程序名一经登记并上架（AppID 产生审核记录 / 类目 / 版本历史）即冻结；改名走一-3 同款变更评审（对齐五节 App 口径）。
3. 【强制】小程序与小游戏是**两种独立主体**（独立 AppID / API 面 / 审核类目）：一个工程一个形态，跨形态复用只走共享层（见 [game.md](game.md) 一-3）。

## 五、可机检条文清单

> 对齐 [../README.md](../README.md)「机检路线」。落地载体为工程 CI（触发式：首个小程序工程创建时接线）。

| 条文 | 机检手段 | 阶段 |
|---|---|---|
| 一-1 TS strict | tsconfig 解析（`strict` 缺失即红） | CI |
| 二-1 包体预算 | 构建产物体积断言（主包 / 整包双线） | CI |
| 二-3 环境注入 | 字面量扫描：业务代码禁 `http(s)://` 硬编码 | CI |
| 二-4 体验分门禁 | 体验评分导出 ≥ 90 | CI |
| 三-1 解包单点 | 依赖解析：业务代码禁直接调用 `wx.request`（白名单：契约内核适配器） | CI |
| 三-2 超时单点 | `timeout` 字面量只允许出现在适配器 | CI |
| 三-4 traceId 注入 | 契约内核单测（适配器请求头断言） | 单测 |
| 四-1 命名与属主 | 生成器校验（registry 六节核对，同 create-admin 先例） | 创建期 |

---

## 附：来源与拍板记录

- 官方规约三层拆解（2026-09-10）：强制规范层与性能 / 体验口径可锚定为权威；工程范式层官方缺位（无 Now in Android 量级参考工程），故本规约采三段式结构、范式段自选登记。解析见 [miniprogram-game-digest.md](../../docs/references/miniprogram-game-digest.md)。
- 拍板记录（唯一登记处：[../README.md](../README.md) 关键架构决策登记表 MP/G 行，此处为引用）：2026-09-10 MP1-MP5——原生+TS 默认档 / 三段式 / 体验评分机检 / `@yarch/contract` 三端同源 / registry 端登记扩节。
- `code = -1` 本地保留码沿用 web 栈 http 封装先例（client-shared 同款）；取消非错误语义同 client-shared 一-4。
