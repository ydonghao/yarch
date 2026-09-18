# 契约 · 实时通道（WebSocket 长连接与推送）（v1.0 已定稿）

> **状态：已定稿**（2026-09-16 经 RT1-RT9 决策清单拍板；调研依据 [../../docs/references/realtime-channel-digest.md](../../docs/references/realtime-channel-digest.md)，企业级定位轨第一优先立项 [../../docs/references/enterprise-capability-gap-digest.md](../../docs/references/enterprise-capability-gap-digest.md) EP9）。
> 等级：**【强制】**违反即缺陷；**【推荐】**默认遵守、评审后可豁免；**【参考】**正向引导。
> 分工：REST 四件套管「客户端发起的请求-响应」；本规约管「服务端可主动下推的双向通道」。两者共享同一错误码表（[error-codes.md](error-codes.md)）与追踪口径（[logging-trace.md](logging-trace.md)），信封各自独立。

## 一、适用与选型

1. 【强制】适用场景：服务端主动推送（通知、状态变更、房间广播）与高频双向交互（游戏对局）；纯请求-响应业务一律走 REST 四件套，**禁为长连而长连**。
2. 【强制】默认传输 **WebSocket（生产一律 `wss://`）**——五端原生 API 交集（浏览器 / `wx.connectSocket` / OkHttp / `URLSessionWebSocketTask` / 引擎封装），连接路径对齐 D5 版本口径：`wss://{gateway}/ws/v1`。本地联调可降级 `ws://`（豁免登记，同 [client-shared.md](../clients/client-shared.md) 三-4 流程）。
3. 【强制】**单服务单连接**：客户端对同一后端至多一条业务长连接，多业务按 `type` 路由复用（微信端 5 条并发上限 + 电量与网关压力；分连接 = 分错误语义）。
4. 【强制】wss 域名是登记资源：入口域名须在属主业务仓 `docs/` 登记（微信小程序须同值配置后台 socket 合法域名白名单，见 [../registry.md](../registry.md) 三）。
5. 【参考】触发档（触发时立增补条文，不预写）：KCP/UDP（原生重竞技弱网档；小游戏经 WXSocketLib，基础库 ≥3.1.1）；MQTT over WebSocket（device 域订阅语义，见 [../domains/device.md](../domains/device.md)）。

## 二、连接生命周期

1. 【强制】**首帧鉴权**：HTTP 升级成功后，客户端第一条业务帧必须是 `auth` 帧（`data` 携带凭证，与 REST 同一 JWT 体系）；服务端限时 **30s** 内未收到合法 `auth` 帧即断开。**凭证禁入 URL query**（网关/访问日志泄漏面）。
2. 【强制】鉴权结果以 `auth-ok` / `error` 帧回执：`error` 帧携带 [error-codes.md](error-codes.md) `2xxx`（2001 未认证 / 2002 凭证过期 / 2004 账号禁用），发完即断开；凭证过期（2002）由客户端单点刷新后重连重鉴（对齐 [client-shared.md](../clients/client-shared.md) 一-6 单点刷新哲学，重放一次）。
3. 【强制】**应用层心跳**：客户端每 30s（默认，单配置点）发 `ping` 帧，服务端必须回 `pong`；客户端连续 2 个间隔（60s）未收 `pong` 判死、主动断开进入重连；服务端 60s 未收 `ping` 判死、清理会话。禁依赖传输层协议 ping（微信端 API 面不暴露）。
4. 【强制】**重连三要素**：指数退避 + 随机抖动 + 封顶（默认：初始 1s、倍率 2、封顶 60s、抖动随机化——防断网恢复雪崩）；重连成功必须重走二-1 鉴权，禁假设复用旧会话；重连前清理旧连接句柄。
5. 【强制】主动断开先发 `close` 帧再关（客户端退出、服务端发布/缩容——服务端下线前先广播 `notice` 帧说明原因）；客户端切后台（小程序冻结）回前台后主动探测连接、失活即重连。
6. 【参考】**会话恢复与补投为触发档**：v1 不做离线消息补投；信封保留 `seq` 字段为预留（触发条件：首个依赖离线补投的业务——届时立 sessionId + seq/ack + 客户端 `id` 去重的增补条文）。

## 三、消息信封（PushEnvelope）

1. 【强制】双向统一信封，字段恒在：

```json
{"type":"order-updated","seq":42,"id":"c7f3","ts":"2026-09-16T08:00:00.123Z","traceId":"0af7651916cd43dd8448eb211c80319c","data":{...}}
```

| 字段 | 类型 | 语义 |
|---|---|---|
| `type` | string | 路由唯一键，kebab-case（D3 同源）；系统保留字：`auth` / `auth-ok` / `ping` / `pong` / `error` / `close` / `notice`，业务 type 禁占用 |
| `seq` | int64 | 连接内单调递增，v1 客户端不做补投但字段恒在（二-6 预留） |
| `id` | string | 消息标识：上行请求帧由客户端生成，服务端响应帧回带同值（请求-响应关联）；服务端推送自生成 |
| `ts` | ISO-8601 UTC | D4 同源 |
| `traceId` | string | 32 位小写 hex，见四-2 |
| `data` | object | 载荷，字段 camelCase；错误帧为 `code` / `message`（复用 14 码表，见四-1） |

2. 【强制】请求-响应 over 通道：客户端上行带 `id`，服务端回执帧（业务响应或 `error` 帧）回带同 `id`；`error` 帧形状 `{type:"error", id, code, message, traceId}`。
3. 【强制】单帧载荷 ≤ 64KB：超限内容走 REST 或对象存储引用化传递（对齐 [../infra/kafka.md](../infra/kafka.md) 大消息引用化先例）。
4. 【强制】序列化默认 JSON 文本帧；protobuf 二进制为触发档（帧体积成为主约束时）。

## 四、错误与追踪

1. 【强制】错误口径**复用 [error-codes.md](error-codes.md) 全表，不新设段位**：鉴权 `2xxx`、限流 `1006`（服务端可对单连接消息频率限流，超限发 `error` 帧后断开）、兜底 `1000`。同一 `code` 在 REST 与本通道语义恒等。
2. 【强制】**消息级 traceId**：每帧信封恒带——上行帧 = 客户端会话 traceId（复用口径，对齐 [client-shared.md](../clients/client-shared.md) 二-1）；服务端推送自发生成；同一请求-响应消息对上下行一致。
3. 【强制】连接生命周期事件记 ndjson（字段对齐 [logging-trace.md](logging-trace.md) 行协议）：`connect` / `auth-ok` / `auth-err` / `close`（含 code/reason），traceId = 当轮会话值，附 `connId` / `remote` 自由键。

## 五、服务端与网关

1. 【参考】服务端实现自由（契约只约束线上协议）：java Spring WebSocket / Netty、golang 标准生态 websocket 包、python websockets / FastAPI WS 均可达。
2. 【强制】入口透传唯一口径 = [../infra/nginx.md](../infra/nginx.md) WebSocket 条文（`Upgrade`/`Connection` 头显式透传）；经 Higress 的路由须验证长连接透传与空闲超时配置（首批实施清单项）。
3. 【推荐】v1 连接无状态（不要求粘性会话、不要求会话存储）；触发二-6 补投档时才引入 Redis 会话（key 前缀纪律见 [../infra/redis.md](../infra/redis.md)）。

## 六、可机检条文清单

> 对齐 [../README.md](../README.md)「机检路线」。落地载体为各端契约内核与栈装配（实现触发式登记，首批范围见附注）。

| 条文 | 机检手段 | 阶段 |
|---|---|---|
| 一-3 单服务单连接 | 契约内核连接管理单点（单测断言复用同连接） | 单测 |
| 一-4 域名登记 | 生成器 registry 校验（socket 域名同值核对） | 创建期 |
| 二-1 首帧鉴权 | 内核握手序列单测（未鉴权发业务帧被拒 + 30s 断开） | 单测 |
| 二-3 心跳单配置点 | 配置解析：心跳字面量只允许出现在契约内核配置 | CI |
| 二-4 退避三要素 | 内核重连序列单测（指数 + 抖动 + 封顶断言） | 单测 |
| 三-1 信封字段恒在 | 内核序列化单测（缺字段即构造失败） | 单测 |
| 三-1 系统保留 type 冲突 | 业务 type 注册表校验（CI 枚举检查） | CI |
| 四-1 错误码复用 | 契约断言（同 14 码表，随支柱 1 error-codes.json 同源） | 单测 |

---

## 附：来源与拍板记录

- 拍板记录（唯一登记处：[../README.md](../README.md) 关键架构决策登记表 RT 行，此处为引用）：2026-09-16 RT1-RT9——独立成文与 REST 四件套并列 / WebSocket 五端默认档（KCP·MQTT 触发档登记）/ 首帧 AUTH（token 禁入 URL）/ 应用层心跳 30s·判死 60s / 指数退避三要素 / 补投触发式 / 独立 PushEnvelope 复用 13 码表 / 消息级 traceId + 连接级 ndjson / 条文全五端、内核首批 web+miniprogram+cocos。
- 平台约束事实与生态选型依据：[realtime-channel-digest.md](../../docs/references/realtime-channel-digest.md)（微信双端 5 连接上限、线上强制 wss、socket 域名白名单、无协议层 ping；Nakama 参照系）。
- 实现节奏：服务端装配（java/golang/python/web 栈）与客户端内核均触发式——首个消费工程立项时施工；首批客户端范围 = web（@yarch/contract）+ miniprogram + game（cocos），android/ios 二批跟进（RT9）。
