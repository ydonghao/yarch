# 契约 · 埋点管道（客户端埋点 · 上报协议 · 摄入，v1.0 已定稿）

> **状态：已定稿**（2026-09-16 经 TM1-TM8 决策清单拍板；调研依据 [../../docs/references/telemetry-pipeline-digest.md](../../docs/references/telemetry-pipeline-digest.md)，企业级定位轨第三优先立项（EP9））。
> 等级：**【强制】**违反即缺陷；**【推荐】**默认遵守、评审后可豁免；**【参考】**正向引导。
> 分工：本规约管**用户行为与业务事件**（客户端埋点 → 摄入管道 → OLAP）；系统健康（服务端 metrics/tracing/日志/审计）归可观测轨（[logging-trace.md](logging-trace.md) / [audit.md](audit.md)）。实时下行推送归 [realtime.md](realtime.md)。

## 一、事件模型（tracking plan）

1. 【强制】**登记先行**：埋点事件注册表（tracking plan——事件名 / 属性 schema / 属主）在业务仓 `docs/` 登记（[../registry.md](../registry.md) 三），**未登记的事件名上报即 CI/服务端拒绝**——无注册表的埋点半年就烂（未知事件堆积无法下线）。
2. 【强制】事件名 kebab-case（对齐 [realtime.md](realtime.md) 一-3 / D3 命名口径），如 `order-completed`、`level-failed`。
3. 【强制】事件信封（批量上报体）：

```json
{"schema": 1, "events": [{"ts": "2026-09-16T08:00:00.123Z", "type": "order-completed", "props": {"orderId": "o-1"}, "traceId": "0af7651916cd43dd8448eb211c80319c"}]}
```

| 字段 | 类型 | 语义 |
|---|---|---|
| `schema` | int | 信封版本（不兼容变更升版，本表 v1） |
| `events[].ts` | ISO-8601 UTC | 客户端事件时刻（D4） |
| `events[].type` | string | 注册表内事件名 |
| `events[].props` | object | 事件属性（camelCase，形状以注册表 schema 为准） |
| `events[].traceId` | string | 会话 traceId（复用 [client-shared.md](../clients/client-shared.md) 二-1；关联服务端日志，见 [logging-trace.md](logging-trace.md) 传播矩阵"埋点上报"行） |

4. 【强制】**通用属性白名单**（SDK 自动附加，业务 props 禁重复声明）：`deviceId` / `appVersion` / `platform` / `channel` / `sessionId?`——白名单外通用属性须走注册表增补。

## 二、上报协议

1. 【强制】`POST /api/v1/telemetry/events`，请求体即一节-3 信封；服务端回 `RestResponse` 信封（[rest-response.md](rest-response.md)）——**走业务 API 同域名**，微信端 request 白名单零新增（对照 realtime 一-4 登记成本）。
2. 【强制】**fire-and-forget 语义**：上报失败客户端静默（本地队列重试带上限，见 [client-shared.md](../clients/client-shared.md) 七），**不向业务层抛错、不弹提示**；埋点天然幂等，重试无双写顾虑（区别于业务请求幂等纪律）。
3. 【强制】批量双阈值默认 **20 条或 10s** 先到先发；单批上限 100 条（超限分批）。
4. 【强制】生命周期兜底：小程序 `onHide` / web `pagehide` 立即 flush（微信端切后台仅约 5s 执行窗口）；web 页面卸载用 `navigator.sendBeacon` 兜底。
5. 【参考】隐私红线：事件属性禁 PII 明文（手机号/邮箱/身份证），确需关联用户的以脱敏 `deviceId` / 注册表声明的 hashed 字段承载；采集开关为客户端单配置点（用户可关，见 [client-shared.md](../clients/client-shared.md) 七-4）。

## 三、服务端管道

1. 【强制】上报端点进各栈装配件（web middleware / starter），职责 = **收口 + 注册表校验 + 转发 kafka**（[../infra/kafka.md](../infra/kafka.md) 摄入管道；topic 前缀=服务名）——**业务服务禁直写 CH**（把 OLAP 可用性耦合进业务请求路径是反模式）。
2. 【强制】摄入 worker（独立进程，消费 kafka）攒批写 ClickHouse（[../infra/clickhouse.md](../infra/clickhouse.md) 攒批写入条文；表分区/保留策略随该规约）。
3. 【参考】StarRocks BI 层（[../infra/starrocks.md](../infra/starrocks.md)）与 CDC（debezium + PG）均为触发式登记——首个数仓/报表需求立项时成文，不预写。
4. 【参考】全埋点（自动采集）/ 可视化埋点不入默认档；触发条件登记后评估。

## 四、可机检条文清单

> 对齐 [../README.md](../README.md)「机检路线」。落地载体为契约内核 telemetry 模块（首批 web + miniprogram + cocos，对齐 RT9）与各栈装配件。

| 条文 | 机检手段 | 阶段 |
|---|---|---|
| 一-1 未登记事件名 | 服务端注册表校验（未知 type 拒绝 + 计数告警） | 运行时 |
| 一-2 事件名 kebab-case | 注册表 lint（业务仓 CI） | CI |
| 一-4 通用属性白名单 | SDK 单测（props 冲突拒绝构造） | 单测 |
| 二-2 失败静默 | 内核单测（上报失败不影响业务返回） | 单测 |
| 二-3 双阈值与单批上限 | 内核单测（flush 序列断言） | 单测 |
| 二-5 禁 PII | 注册表属性名黑名单 lint | CI |
| 三-1 禁直写 CH | 依赖解析：业务工程禁依赖 CH 客户端 | CI |

---

## 附：来源与拍板记录

- 拍板记录（唯一登记处：[../README.md](../README.md) 关键架构决策登记表 TM 行，此处为引用）：2026-09-16 TM1-TM8——落位 api/ 与 realtime 同级 / tracking plan 登记先行 / 业务域名批量上报 + 双阈值 + onHide·sendBeacon 兜底 / SDK 可靠性纪律（有界队列 + storage 双写 + 失败静默）/ 隐私红线 / SDK 落 @yarch/contract telemetry 模块首批三端 / 管道收口转 kafka 禁直写 / CDC·StarRocks 触发式。
- 工业级范式依据：[telemetry-pipeline-digest.md](../../docs/references/telemetry-pipeline-digest.md)（神策类 SDK 双写队列、onHide flush、sendBeacon、tracking plan 治理）。
- 实现节奏：客户端 SDK（@yarch/contract telemetry 模块，零新包复用 transport/traceId/storage backend）与服务端装配件均触发式——首个消费工程立项时施工，可与 realtime 内核同批摊成本。
