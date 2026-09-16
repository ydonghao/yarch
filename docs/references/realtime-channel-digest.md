# 实时通道调研 digest（长连接 / 推送，2026-09）

> **定位**：服务于 `contract/api/realtime.md`（暂名）立项的调研与审阅材料——企业级应用/游戏缺口第一优先轨（EP9 建议 (a)，见 [enterprise-capability-gap-digest.md](enterprise-capability-gap-digest.md)）。**规范性条文一律以 [../../contract/](../../contract/README.md) 为准**。**RT1-RT9 决策清单在文末**。
> 现状确认：全仓仅 [../../contract/infra/nginx.md](../../contract/infra/nginx.md) 一条 WS 透传条文（`Upgrade`/`Connection` 头强制），api/ 层无任何长连接契约——本轨为净新增，与现状无冲突。

## 一、平台约束事实（微信双端，2026-09 检索）

| 约束 | 事实 | 对契约的含义 |
|---|---|---|
| 并发连接数 | 基础库 1.7.0+ 小程序最多同时存在 **5 条** WebSocket 连接；更早版本仅 1 条 | 契约须立**「单服务单连接」**原则：客户端对同一后端至多一条业务长连接，多业务复用 type 路由，不开多连接 |
| 协议强制 | **线上只支持 wss**（明文 ws 仅开发工具勾选「不校验合法域名」可调）；小游戏真机同样只支持 WSS | 契约直接以 wss 为准，ws 只留本地联调口径 |
| 域名白名单 | socket 合法域名须在小程序后台登记（`wss://` 前缀）；不能用 IP / localhost（局域网 IP 除外）；可带端口 | wss 域名是**登记资源**，须进 registry 派生资源指引 |
| 连接管理 | 官方推荐 `SocketTask` 方式管理单条链路生命周期，多连接场景慎用 wx 前缀全局方法 | 小程序端实现口径：SocketTask 单例封装 |
| 心跳 | wx API 面不暴露协议层 Ping/Pong 帧，业界通行**应用层心跳消息**；间隔须 <60s（NAT/网关切空闲连接） | 心跳必须进契约（消息格式 + 默认间隔 + 判死规则），不能依赖传输层 |
| 小游戏重通道 | 基础库 3.1.1+ 提供 TCP socket；WXSocketLib（WASM 类 POSIX）补齐 TCP+UDP——KCP 可在小游戏落地 | UDP/KCP 属**重档**（WASM 成本 + 3.1.1 基线），不进默认档，登记触发条件 |
| 官方托管 | 微信云有内置「帧同步游戏服务」（房间/帧广播托管） | 竞品参照：托管方案证明需求真实；yarch 做传输契约层，不做托管竞品 |

## 二、传输选型格局

**默认档唯一合理解是 WebSocket**，硬理由是五端原生交集：

| 端 | 原生 WS 能力 |
|---|---|
| web / cocos（H5·小游戏） | 浏览器 `WebSocket` / 引擎封装 |
| 微信小程序·小游戏 | `wx.connectSocket`（SocketTask） |
| android | OkHttp `WebSocket`（已在依赖基线内） |
| ios | `URLSessionWebSocketTask`（URLSession 原生） |

生态事实（不进默认档，登记认知）：

- **Socket.IO**：web 端生态最大，但是私有协议（握手/帧格式非标准 WS），小程序/原生端要带兼容客户端——跨端统一契约不选它；
- **MQTT over WebSocket**：面向 IoT/消息总线（EMQX 明确支持微信端 wss 接入），适合「设备订阅」语义而非「应用推送」语义；device 域将来若需可作触发档；
- **KCP / QUIC**：弱网低延迟最优（激进重传换带宽），原生 App 直用 UDP，小游戏走 WXSocketLib；定位**帧同步/强实时竞技触发档**；
- **参照系 Nakama**（Go、Apache-2.0，2026 仍是开源游戏后端头部）：证明「传输层 + 社交件（匹配/排行榜/聊天）」分层市场成立——yarch 只做其中**传输契约层**，房间/匹配等属领域件走 domains/ 触发立项，不做 Nakama 竞品。

## 三、连接生命周期（业界共识拆解，契约条文的目标骨架）

1. **握手鉴权**：两种主流——URL query 带 token（实现简，但 token 入网关/服务日志，泄漏面大）vs **首帧 AUTH 消息**（连接建立后限时发 token，服务端校验通过才进业务态，超时未鉴权即断）。企业级口径倾向首帧 AUTH。
2. **心跳保活**：应用层 PING/PONG 消息；客户端每 X 秒发 PING，服务端回 PONG；双方各自判死（客户端超时未收 PONG 主动断开触发重连，服务端超时未收 PING 清理会话）。X 须 <60s，常用 15-30s。
3. **断线重连**：指数退避 + 随机抖动 + 上限封顶；重连前清理旧连接句柄（防句柄泄漏假连接）；小程序切后台冻结、回前台需主动探测恢复。
4. **会话恢复与补投**（成本最高的一段）：sessionId + 消息 seq/ack，重连后携 lastSeq 请求补投，服务端维护离线消息窗口；客户端按 messageId 幂等去重。业界（阿里云长连接实践）列为进阶项，非 v1 必须。

## 四、消息协议与 yarch 契约接缝

- **独立信封**：推送/请求-响应-over-WS 不是 REST 信封的场景，建议独立 `PushEnvelope`（type / seq / id / ts / data 形状），**不复用 RestResponse**（REST 信封语义绑定 HTTP 状态码）；错误口径**复用 error-codes.md 13 码表**（鉴权失败推 2xxx 后断开、限流 1006 等）——13 码表因此成为 REST 与 realtime 双轨共享的唯一错误真理源，支柱 1 的 error-codes.json 价值再加一票。
- **traceId**：消息级（每请求-响应消息对一条，服务端推送自发新 traceId）+ 连接级生命周期事件（connect/auth/close 记 ndjson，字段对齐 logging-trace.md）。
- **服务端实现自由**：契约只约束线上协议（握手/心跳/信封），不绑实现——java（Spring WebSocket / Netty）、golang（标准库生态 websocket 包）、python（websockets / FastAPI WS）均可达；**higress 对 WS 长连接的透传与超时配置需实施时验证**（nginx.md 已有透传条文，网关侧同语义）。
- **clients 侧落点**：client-shared.md 增「长连接与推送」节（连接状态机 + 心跳 + 重连为共享条文），五端各自映射 SocketTask / OkHttp / URLSession / 原生 WS；成文时定章节切分。

## RT 决策清单（2026-09-16，待拍板）

| # | 议题 | 选项 | 建议 |
|---|---|---|---|
| RT1 | 契约归属 | (a) 新立 `contract/api/realtime.md`，与 REST 四件套并列；(b) 扩 rest-conventions.md；(c) 放 contract/clients/ | **(a)**——与 REST 同级的服务端-客户端线上协议，非 REST 的从属 |
| RT2 | 默认传输 | (a) WebSocket（wss）全端默认档；(b) MQTT over WS；(c) Socket.IO | **(a)**——五端原生交集是硬理由；KCP/UDP（原生竞技 + 小游戏 WXSocketLib）与 MQTT（device 域）登记触发档 |
| RT3 | 握手鉴权 | (a) 首帧 AUTH（token 不入 URL/日志，限时未鉴权断开）；(b) URL query token；(c) 双模 | **(a)**——企业级日志卫生口径；(b) 仅留本地联调 |
| RT4 | 心跳口径 | (a) 应用层 PING/PONG 消息，默认 30s 发 / 60s×2 判死，间隔为客户端单配置点；(b) 协议层 ping 优先 + 应用层回退 | **(a)**——微信端无协议层 ping 事实倒逼；双轨徒增测试面 |
| RT5 | 重连策略 | (a) 指数退避 + 抖动 + 上限封顶，重连成功即重走鉴权；(b) 固定间隔重试 | **(a)**——业界共识，防雪崩 |
| RT6 | 会话恢复与补投 | (a) v1 只做重连 + 重鉴权；sessionId + seq 补投 / 离线窗口 / messageId 去重登记触发式；(b) v1 全做 | **(a)**——控制首版实现面；补投依赖服务端会话存储，成本高 |
| RT7 | 消息信封 | (a) 独立 `PushEnvelope`（type/seq/id/ts/data），错误口径复用 13 码表，不复用 RestResponse；(b) 复用 RestResponse；(c) 只定 type 不定信封 | **(a)**——REST 信封语义绑 HTTP 状态码，硬套会漂移 |
| RT8 | traceId 口径 | (a) 消息级 traceId + 连接级生命周期 ndjson（对齐 logging-trace.md）；(b) 仅连接级 | **(a)**——与 REST 排障体验同构 |
| RT9 | 首批实现面 | (a) 条文覆盖全五端；内核实现首批 web + miniprogram + cocos（游戏主战场），android/ios 二批；(b) 五端一批；(c) 仅条文不实现 | **(a)**——android/ios WS 原生便宜但测试面大，二批跟进；higress WS 透传验证列入首批实施清单 |

## 拍板后动作

按机检路线推进：本 digest + RT 清单 → 拍板 → `contract/api/realtime.md` 条文成文（含 clients 侧 client-shared.md 增节）→ contract/README 决策表与变更记录登记 → 四栈 + 三端内核实现排队（与支柱 1 error-codes.json 施工合并考虑，13 码表双轨共享）。

## 来源

[微信官方 · wx.connectSocket](https://developers.weixin.qq.com/miniprogram/dev/api/network/websocket/wx.connectSocket.html)（5 连接上限 / SocketTask）· [微信官方 · 网络能力](https://developers.weixin.qq.com/miniprogram/dev/framework/ability/network.html)（wss 强制 / 白名单）· [微信官方 · 小游戏网络](https://developers.weixin.qq.com/minigame/dev/guide/base-ability/network)（小游戏真机 WSS / TCP 3.1.1+）· [微信官方 · TCP/UDP Socket 适配（WXSocketLib）](https://developers.weixin.qq.com/minigame/dev/guide/game-engine/common-adaptation/Design/SocketAdapter.html) · [微信官方 · 帧同步游戏服务](https://developers.weixin.qq.com/minigame/dev/guide/open-ability/lock-step.html) · [EMQX · 微信小程序接入](https://docs.emqx.com/zh/emqx/latest/connect-emqx/wechat-miniprogram.html) · [掘金 · 小程序 WebSocket 心跳重连](https://juejin.cn/post/6844903647814352904)（应用层心跳实践）· [阿里云 · 长连接假死与退避重连](https://developer.aliyun.com/article/1763056)（指数退避 + 抖动 / seq 补偿去重）· [腾讯云 · 心跳机制失效排查](https://developer.cloud.tencent.com/article/2571965) · [KCP 协议解析（腾讯 WeTest）](https://wetest.qq.com/labs/391) · [Nakama（Heroic Labs）](https://heroiclabs.com/nakama/)（开源游戏后端参照）（2026-09-16 检索）
