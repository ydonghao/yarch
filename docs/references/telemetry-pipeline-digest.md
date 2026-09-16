# 埋点管道轨调研 digest（客户端埋点 SDK · 上报协议 · 摄入管道 · CDC，2026-09）

> **定位**：服务于埋点管道轨立项（EP9 第三轨——企业级游戏的运营命脉）的调研与审阅材料。**规范性条文一律以 [../../contract/](../../contract/README.md) 为准**。**TM1-TM8 决策清单在文末**。
> 姊妹篇：[observability-track-digest.md](observability-track-digest.md)（第二轨，同批出稿）。分界：可观测轨管**系统健康**（服务端三信号+审计）；本轨管**用户行为与业务事件**（客户端埋点→管道→OLAP）。

## 一、现状与目标

- **已有资产**：[clickhouse.md](../../contract/infra/clickhouse.md)（攒批写入、OLAP 之一：日志/遥测）、[starrocks.md](../../contract/infra/starrocks.md)（实时数仓/BI）、[kafka.md](../../contract/infra/kafka.md)（摄入管道定位）——**后端三件契约齐但空转**：没有客户端埋点 SDK、没有上报协议、没有事件 schema 口径。这是"游戏运营驱动"定位下唯一整轨缺位的管道（[enterprise-capability-gap-digest.md](enterprise-capability-gap-digest.md) 缺口二）。
- **目标**：五端 SDK 一套上报协议 + 事件注册表治理 + 摄入管道装配口径，打通「客户端事件 → CH/StarRocks → 运营报表」。

## 二、业界实践事实（2026-09 检索）

| 事实 | 出处口径 | 对 yarch 的含义 |
|---|---|---|
| 工业级埋点 SDK 通行架构（神策类）：内存队列 + 本地 storage **双写**防丢、批量触发（条数/间隔双阈值）、上报失败回滚队列重试、发送前格式校验 | [神策小程序 SDK 架构解析](https://zhuanlan.zhihu.com/p/400769563)、[小程序组件化埋点实践](https://juejin.cn/post/6937481792643399693) | SDK 可靠性纪律可直接条文化（TM4） |
| 小程序生命周期：`onHide`（切后台约 5s 执行窗口）立即 flush，防进程回收丢数据；storage 暂存 + 上报成功后删除 | [掘金 · 组件化埋点](https://juejin.cn/post/6937481792643399693) | onHide/页面隐藏强 flush 是微信端硬口径；web 对应 sendBeacon |
| web 端页面卸载上报标准 = `navigator.sendBeacon`（异步不阻塞卸载） | MDN / W3C Beacon | web SDK 卸载兜底通道 |
| 埋点三分类：代码埋点（关键业务，契约可控）/ 全埋点（自动采集，量杂）/ 可视化埋点 | [无埋点采集方案解析](https://zhuanlan.zhihu.com/p/32127243) | yarch 默认档 = 代码埋点（tracking plan 治理）；全埋点触发式 |
| CDC：Debezium（PG 官方支持、Kafka Connect 生态）为事实标准；canal 属 MySQL 生态 | Debezium 官方定位 | yarch 默认 PG → debezium；触发式登记（TM8） |

## 三、与 yarch 资产的接缝（本轨最便宜的三步）

1. **traceId 关联红利**：客户端契约内核 traceId 会话复用（client-shared 二-1）→ 事件携带 traceId 即可把「用户做了什么」与「服务端怎么处理的」串成一条链（logging-trace 跨进程矩阵补"埋点上报"行即可闭合）。
2. **域名零新增**：上报走业务 API 同域名（`POST /api/v1/telemetry/events`）——微信端 request 合法域名白名单已配，**不为埋点新增 wss/socket 域名负担**（对照 realtime 一-4 的登记成本）。
3. **管道契约已就位**：上报端点收口 → kafka（[kafka.md](../../contract/infra/kafka.md) 摄入管道 + 攒批哲学）→ 摄入 worker 攒批写 CH（[clickhouse.md](../../contract/infra/clickhouse.md) 已有条文）→ StarRocks BI 层（[starrocks.md](../../contract/infra/starrocks.md)）。本轨新立的是**客户端协议 + 事件 schema + 装配口径**三件，后端只做组合登记。

## 四、设计要点拆解（条文的目标骨架）

1. **上报语义特殊**：埋点是 fire-and-forget——服务端仍回 RestResponse 信封（契约一致性），但客户端**失败不重试业务语义**（区别于业务请求一-7 禁自动重试的幂等顾虑——埋点天然幂等，可安全重试），本地队列有界、溢出丢旧。
2. **事件信封**：`{schema, events: [{ts, type, props, traceId, sessionId?}]}`——type kebab-case 对齐 realtime 一-3 命名口径；通用属性白名单（deviceId/appVersion/platform/channel）由 SDK 自动附加。
3. **tracking plan 治理**：事件注册表（事件名/属性 schema/属主）进业务仓 docs，CI 校验未知 type——与 realtime type 注册表、错误码 3xxx 段位登记同构，yarch「登记先行」哲学的第三次复用。

## TM 决策清单（2026-09-16，待拍板）

| # | 议题 | 选项 | 建议 |
|---|---|---|---|
| TM1 | 契约归属 | (a) 新立 `contract/api/telemetry.md`（与 realtime 同级的客户端↔服务端横切协议）；(b) `domains/`；(c) `infra/` | **(a)**——跨端协议 + 摄入口径，不是领域也不是中间件 |
| TM2 | 事件模型 | (a) tracking plan 事件注册表进业务仓 docs + CI 校验未知 type + 事件名 kebab-case + 信封带 `schema` 版本字段；(b) 自由上报不设注册表 | **(a)**——无注册表的埋点半年就烂（未知事件堆积无法下线） |
| TM3 | 上报协议 | (a) `POST /api/v1/telemetry/events` 批量 ndjson，默认 20 条或 10s 双阈值 flush，onHide/页面隐藏强 flush，web 卸载 sendBeacon 兜底，RestResponse 信封回执；(b) OTLP 直发 Collector；(c) 独立采集域名 | **(a)**——域名零新增 + 信封契约一致；OTLP 浏览器/微信端支持是弱路径 |
| TM4 | SDK 可靠性纪律 | (a) 内存队列有界（默认 500 条溢出丢旧）+ storage 双写 + 失败回滚重试（带上限）+ 禁阻塞主流程 + 上报失败对业务静默；(b) 尽力而为不定条文 | **(a)**——工业级 SDK 共识直接条文化 |
| TM5 | 隐私红线 | (a) 禁 PII 明文进事件、通用属性白名单、采集开关配置点（用户可关）；(b) 不立 | **(a)**——个保法最小合规面，企业采购必问 |
| TM6 | 客户端首批与落点 | (a) `@yarch/contract` 内 telemetry 模块（复用 transport 适配器/traceId/storage backend，零新包），首批 web+miniprogram+cocos（对齐 RT9），android/ios 二批；(b) 独立 `@yarch/telemetry` 包；(c) 五端一批 | **(a)**——与 S1 三端同源架构完全同构 |
| TM7 | 服务端管道 | (a) 上报端点进各栈装配件（收口+转发 kafka，无状态），摄入 worker（消费攒批写 CH）独立触发式，StarRocks BI 层触发式；(b) 业务服务直写 CH | **(a)**——直写把 CH 可用性耦合进业务请求路径，违 kafka.md 摄入管道分工 |
| TM8 | CDC | (a) debezium+PG 触发式登记（首个数仓/报表需求触发）；(b) 本轨一并立契约；(c) canal | **(a)**——yarch 默认 PG 对应 debezium；无消费方不预写 |

## 拍板后动作

按机检路线：本 digest + TM 清单 → 拍板 → `api/telemetry.md` 条文成文（+ client-shared 增埋点纪律节 + logging-trace 传播矩阵增"埋点上报"行）→ contract/README 决策表登记 → 客户端 SDK 与管道装配触发式排队（首批三端对齐 RT9；与 realtime 内核同批施工可摊成本）。

## 来源

[神策数据 · 微信小程序 SDK 架构解析](https://zhuanlan.zhihu.com/p/400769563) · [掘金 · 微信小程序组件化埋点实践](https://juejin.cn/post/6937481792643399693)（onHide flush / storage 双写）· [知乎 · 无埋点数据采集方案](https://zhuanlan.zhihu.com/p/32127243)（三分类）· [掘金 · 数据埋点怎么做](https://juejin.cn/post/7520993539702964239)（分层/批量/治理原则）· [Debezium 官网](https://debezium.io/)。仓内锚点：clickhouse.md / starrocks.md / kafka.md / minio-s3.md（大消息引用化）/ client-shared.md 二-1 / api/realtime.md 一-3（2026-09-16 检索）
