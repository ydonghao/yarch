# contract/ · 跨栈统一契约与规约

> 多栈脚手架的灵魂：各栈长得不一样没关系，必须说同一种接口语言。
> 本目录是全部栈实现的**唯一权威来源**，改契约必须先改这里。已落地：java / golang / web / python；clients 移动端规约已定稿（2026-09-09，实现触发式）；**规约已立待施工**：stacks/rust（axum）+ embedded/esp32（esp-idf-hal std，2026-09-14）；规划中：node（触发式）。**agent/ AI 施工规约已定稿**（2026-09-14：约束全部工程的 AI 代理上下文、生态扩展出口与自家 CLI）。**企业级三轨契约已定稿**（2026-09-16：api/realtime · telemetry · audit + infra/prometheus · grafana + logging-trace v1.1，实现触发式）。

## 规约治理（等级定义 / 豁免与变更 / 机检路线）

**条文等级**（适用于全部契约与规约）：

| 等级 | 语义 | 整改责任 |
|---|---|---|
| 【强制】（开发行为） | 违反即缺陷 | 业务工程开发者，CR/CI 把关 |
| 【强制】（部署基线） | 条文以"部署/实例/集群"为对象（节点数、水位、参数） | 平台/IaC 模板与运维，评审把关 |
| 【推荐】 | 默认遵守，评审后可豁免 | 申请豁免须记录在案 |
| 【参考】 | 正向引导 | — |

**豁免流程**：业务仓 PR 中说明豁免条文、理由与补偿措施，评审人批准后在业务仓 `docs/waivers.md` 登记（豁免有效期至该条文修订）。

**变更流程**：契约/规约变更先改本目录（含版本号）再改实现；v1 → v2 破坏性变更（字段/码位/命名口径变更）需全部已接入栈的实现方会签，旧版本并行至迁移完成。变更记录见文末「评审与变更记录」。

**机检路线**：yarch 定位是"规范的可执行化"，条文最终要落成机检（`yarch lint` / CI 门禁 / AI 审查清单）。推进方式：规约定稿一份、标注一份"可机检条文"清单（命名/数值/枚举类优先），禁止停留在文档自觉。

## 规约组合与依赖模型

**各项目按需组合，无全家桶假设**：一份 infra 规约在项目启用了对应组件时才生效；项目的技术栈组合（PG 或 MySQL、Kafka 或 RocketMQ、要不要 ES/向量/OLAP）由项目自定，组合结果登记在业务仓（服务名登记见 [registry.md](registry.md)）。

**规约间引用分三级，读取条文时按此降级理解**：

| 关系 | 语义 | 未采用被引用规约时 |
|---|---|---|
| **前置依赖（硬）** | 本规约建立在被依赖规约之上（扩展/同实例） | 本规约不适用（如不启用 PG 就谈不上 pgvector 规约） |
| **协作参考（软）** | 引用对齐口径（错误码语义、traceId、命名同构、幂等手段） | 该条按项目实际替代实现，**本规约其余条文效力不变** |
| **互斥/分工** | 多组件覆盖同类场景，按决策表选型 | 二选一/分场景，见「关键架构决策登记表」 |

> 说明：infra 条文中出现的"对齐/同构/呼应"（如限流返回错误码 1006、消息头带 traceId、前缀同 Redis key 哲学）**均为协作参考级**——项目未采用 yarch API 四件套时，这些引用降级为参考口径（错误码语义、traceId 格式自行定义，但 infra 规约的其余强制条文仍然有效）。

**依赖矩阵**（前置依赖为硬约束，其余为软参考）：

| 规约 | 前置依赖（硬） | 主要协作参考（软） |
|---|---|---|
| mysql / postgresql / mongodb / redis / nginx | — | api 四件套（错误码/幂等/traceId 口径） |
| api/realtime | — | error-codes（13 码表复用）、logging-trace（ndjson/traceId）、rest-conventions（命名/大消息引用化）、clients/client-shared（六节客户端纪律）、nginx/higress（入口透传） |
| api/telemetry | — | error-codes（13 码表回执）、logging-trace（埋点矩阵行）、kafka（摄入管道）、clickhouse（攒批写入）、clients/client-shared（七节客户端纪律） |
| api/audit | — | logging-trace（行协议同源）、clickhouse（append-only 存储）、registry（派生资源登记指引同款） |
| infra/prometheus / infra/grafana | — | api/logging-trace（service/env 标签口径）、api/audit + api/telemetry（CH 数据源共用）、agent/extensions（数据源凭证 ${ENV}） |
| timescale | postgresql | clickhouse（日志场景分流） |
| pgvector | postgresql | milvus（升级路径） |
| redisearch | redis | elasticsearch（搜索分层） |
| kafka / rocketmq | — | api（traceId/幂等）、minio-s3（大消息引用化） |
| nacos | — | registry（服务名）、higress（服务发现来源） |
| higress | — | api（信封/错误码/traceId）、nacos（服务发现，非硬前置） |
| elasticsearch | — | kafka（日志管道）、postgresql（检索分层） |
| clickhouse / starrocks | — | kafka（摄入管道）、minio-s3（冷数据） |
| minio-s3 | — | nginx/higress（上传上限）、kafka/rocketmq（大消息） |
| xxl-job | — | redis（分布式锁）、kafka/rocketmq（任务产出消息） |
| milvus | — | minio-s3、kafka（向量生成管道）、pgvector/qdrant（降级路径） |
| qdrant | — | minio-s3、kafka（向量生成管道）、pgvector（降级路径） |
| celery | redis/rabbitmq（broker，共享 Redis 必配 global_keyprefix） | api（幂等/traceId）、kafka/rocketmq、xxl-job（分工边界） |
| web/micro-frontend | — | api 四件套（信封/错误码/traceId 口径）、registry（前端应用名登记）、nginx（静态托管） |
| clients/client-shared | — | api 四件套（信封/错误码/traceId/幂等口径）、registry（App 名登记） |
| clients/android / clients/ios | clients/client-shared | api 四件套（口径协作参考）、registry（App 名与包标识登记） |
| clients/miniprogram / clients/game | — | client-shared（协作参考）、api 四件套（信封/错误码/traceId 口径）、registry（小程序与小游戏登记） |
| domains/device | — | api（logging-trace JSON 口径）、registry（设备名/产品族登记）、redis（影子缓存前缀） |
| embedded/esp32 | domains/device | api（口径协作参考）、registry（设备登记） |
| stacks/rust | api 四件套 | postgresql（sqlx）、redis（幂等/锁/限流封装） |
| agent/agents-md / agent/extensions | — | contract 全域（锚点引用对象）、cli（生成器产出三件套自检义务） |
| agent/cli | — | agents-md（生成物须含合规 AGENTS.md 三件套）、extensions（生成物扩展出口） |

## 关键架构决策登记表（唯一权威）

以下决策以本表为唯一登记处，其余文档出现处一律为引用：

| 决策 | 结论 | 状态 |
|---|---|---|
| 新项目默认数据库 | PostgreSQL；MySQL 服务存量 | 已生效 |
| 消息队列 | 双轨：Kafka（事件流/日志管道/数据集成）+ RocketMQ（在线业务消息）；同一事件默认只落一个 MQ | 已生效 |
| OLAP 引擎 | 双引擎：ClickHouse（日志/遥测）+ StarRocks（实时数仓/BI，重叠场景默认） | 已生效 |
| 向量检索 | 三档：pgvector 默认起步 → 专用库升级档默认 Qdrant（部署轻/Rust 生态/过滤强）→ Milvus 仅亿级/GPU/超大规模 | 已生效（2026-09-01 由两档升三档） |
| 网关分工 | Nginx（入口/静态/简单代理）+ Higress（API 网关与治理） | 已生效 |
| 文档库 | MongoDB 仅文档型刚需经评审引入，默认 PG | 已生效 |
| API 契约 D1-D6（2026-09-01 拍板：**业界标准优先于阿里手册**） | D1 int32 数字段位+标识符（非阿里 A/B/C 字符串）；D2 单 message 不引入 userTip；D3 kebab-case；D4 ISO-8601 UTC；D5 路径版本 `/v1/`；D6 越界返回空页（200+空 list+真实 total，参数非法仍 1001） | 已生效 |
| 微前端载器分档 | 应用级集成默认 **micro-app**（Vite 零改造/低侵入）；存量与复杂沙箱档 **qiankun**；**Module Federation** 限同仓模块共享场景；wujie 不入册（维护活跃度不足）。规约条文载器无关（[web/micro-frontend.md](web/micro-frontend.md)）；观察哨：micro-app 持续停更则默认档切 qiankun | 已生效（2026-09-07） |
| 移动端规约 M1-M7（2026-09-09 拍板） | 落位 contract/clients/ 三份：client-shared 共享契约 + android + ios 方言。融合基准 Android=**Google 官方**（风格/架构/Now in Android 范式；DI=Hilt）；iOS=**官方 API 设计指南 + Airbnb 风格**（SwiftLint+SwiftFormat 机检）；iOS 工程生成 **xcodegen+SPM**；iOS 架构 **MVVM+@Observable**。min 基线双档分文件夹：android minsdk26 默认 / 24 扩展，ios17 默认 / 16 扩展（降级 ObservableObject）；targetSdk ≥ 36 硬线（Play 2026-08-31 起） | 已生效（2026-09-09） |
| 小程序与游戏端规约 MP1-MP5/G1-G4（2026-09-10 拍板） | 落位 contract/clients/ 两份：miniprogram（**原生+TS 唯一入册栈**，Taro/uni-app 不入默认档——多端诉求升级登记，叠加不推翻；三段式结构补官方旗舰参考工程缺位）+ game（**渲染层与 web 禁共享，契约内核/领域逻辑/design token 三层共享**；Cocos Creator 默认档 / Unity·团结 3D 重度档；首包 4MB 硬线；v1.0 先窄后宽）。TS 契约内核 = **@yarch/contract 三端同源**（web/miniprogram/game，transport 可插拔注入 wx.request / 引擎 HTTP 适配器） | 已生效（2026-09-10） |
| 领域契约首发 device.md（2026-09-14 拍板 Dv1-Dv6） | 落位 contract/domains/device.md v1.0：设备标识两段 `{product}/{device}` / MQTT topic 六段定式（env 前缀单点）/ 遥测 ndjson 四字段（ts·traceId·metrics·fw，复用 logging-trace 口径）/ OTA 双分区+回滚+验签 / 影子 Redis 缓存+命令回执对齐信封哲学 / TLS 强制+一设备一证书。首个双方言验证=microduck（云端 Go + esp32 Rust） | 已生效（2026-09-14） |
| embedded/esp32 规约 E1-E8（2026-09-14 拍板） | 技术路线 **esp-idf-hal std**（WiFi/MQTT/OTA/TLS 成熟是决定性因素；no_std esp-hal 不入册）；HAL 次版本锁定（~0.46/~0.52）；首档 esp32 经典款；cargo generate + 模板目录；固件不发库模板随仓；clippy+rustfmt+host 单测 CI；与云栈 rust 零共享 crate | 已生效（2026-09-14） |
| Rust 云栈 R1-R7（2026-09-14 拍板） | **axum 0.8**（crates.io 4.67 亿下载无争议默认）+ sqlx + tokio 单体业务栈；DDD 七包（errors 目录同 python 修正口径）；workspace 两 crate（yarch-contract 零框架 + yarch-axum 装配）；**cargo-generate + 模板目录**（生态标准通道，远期统一 CLI 收编 `--lang rust`）；crates.io 发版 tag `stacks/rust/vX.Y.Z`；clippy deny warnings + rustfmt + cargo-deny + 契约断言；MSRV 1.80 | 已生效（2026-09-14） |
| AI 施工规约 A1-A6（2026-09-14 拍板） | **新设 contract/agent/ 层**：AGENTS.md 唯一事实源 + CLAUDE.md/GEMINI.md 仅一行 `@AGENTS.md` 派生（禁分叉；目录型配置不生成不维护）；四章节定式（工程地图/命令表/红线清单/契约锚点）+ 篇幅 ≤150 行（Codex 32 KiB 最严口径）；锚点双要素引用 = N2 机器可读出口首个消费场景；扩展出口首批只纳管 .mcp.json（凭证 `${ENV}` 引用）+ skills（`.agents/skills/` 跨厂商位置，`.claude/skills` 链接派生），hooks/subagents 参考级；自家 CLI 交互规约 cli.md（一行命令铁律成文 + `--yes` 非交互 + `--json` + 退出码 0/1/2 + 幂等）。目录命名避开 domains/ai.md「LLM 接入」占位。依据：[ai-cli-ecosystem-digest.md](../docs/references/ai-cli-ecosystem-digest.md) | 已生效（2026-09-14） |
| 企业级定位修订 EP1-EP12（2026-09-16 拍板） | 定位句不动，目标场景补「**企业级应用/游戏交付**」（EP1 场景定语）；三轨排序 **realtime > 可观测（含审计）> 埋点管道**（EP9）；边界细化：多租户拆两层——**上下文传播机制契约归 yarch、账号模型归业务仓**（EP2），i18n 只做**错误码稳定 key 就绪位**（EP3，随支柱 1 error-codes.json 施工）；施工项：@SignedApi/Masks 跨栈对齐 golang+python（EP5）、CI 安全扫描 Dependabot+CodeQL（EP6）；通知通道领域契约排队三轨后（EP7）、支付协议层触发式登记（EP8）、桌面端触发式登记（EP10）。依据：[enterprise-capability-gap-digest.md](../docs/references/enterprise-capability-gap-digest.md) | 已生效（2026-09-16） |
| 实时通道规约 RT1-RT9（2026-09-16 拍板） | 新立 [api/realtime.md](api/realtime.md) v1.0 与 REST 四件套并列：**WebSocket（wss）五端默认档**（KCP/UDP 重竞技与 MQTT device 域为触发档）；**首帧 AUTH**（凭证禁入 URL，30s 限时）；**应用层心跳**（30s 发 / 60s 判死，单配置点）；**重连三要素**（指数退避+抖动+封顶）；补投/离线消息触发式（seq 预留）；**独立 PushEnvelope**（type/seq/id/ts/traceId/data）错误口径复用 13 码表、消息级 traceId + 连接级 ndjson；条文覆盖全五端，内核首批 web+miniprogram+cocos。client-shared 同批增六节（长连接客户端纪律）。依据：[realtime-channel-digest.md](../docs/references/realtime-channel-digest.md) | 已生效（2026-09-16） |
| 可观测轨 OB1-OB9 + EP2-R/EP3-R（2026-09-16 拍板） | 骨架 = **OTel 三信号 + Collector**（厂商中立）；**logging-trace 升 v1.1**（spanId 入上下文与日志、租户上下文 X-Tenant-Id 进传播矩阵——机制归 yarch 隔离模型归业务、埋点上报边界进矩阵）；metrics = **Prometheus**（exposition 标准 + RED 基础集装配件收口，新 infra/prometheus.md）；logs/traces 后端 = **ClickHouse 统一**（官方 ClickStack 路径，不新立 Loki/Tempo/Mimir，LGTM 触发档；新 infra/grafana.md：Grafana 单面板两件制 + Alerting + dashboards/告警规则 provisioning 进 git）；**ndjson 行协议不搬家**（OTel SDK 只管 traces+metrics，采集走 Collector filelog）；traces 默认全量+采样单配置点；**审计 EP4 并轨**：api/audit.md v1.0（最小审计事件面 + 固定字段集 → CH append-only + 保留期 ≥180d，落 OperationLogStore SPI CH 实现件）；EP3-R：error-codes 标识列即稳定 key，error-codes.json 必含 code/key/message + REST Accept-Language 就绪位（多语言 catalog 不做）。依据：[observability-track-digest.md](../docs/references/observability-track-digest.md) | 已生效（2026-09-16） |
| 埋点管道轨 TM1-TM8（2026-09-16 拍板） | 新立 [api/telemetry.md](api/telemetry.md) v1.0：**tracking plan 登记先行**（事件注册表进业务仓 docs + CI/服务端双校验，登记哲学第三用）；上报走业务域名 `POST /api/v1/telemetry/events`（微信白名单零新增）批量双阈值 20 条/10s + onHide/pagehide 强 flush + web sendBeacon 兜底 + RestResponse 信封回执、fire-and-forget 失败静默；SDK 可靠性纪律（内存队列有界 500 + storage 双写 + 失败回滚重试带上限）；隐私红线（禁 PII/白名单通用属性/采集开关）；SDK 落 **@yarch/contract telemetry 模块**（零新包，首批 web+miniprogram+cocos 对齐 RT9）；管道 = 装配件收口转 **kafka** → 摄入 worker 攒批写 **CH**（业务禁直写）；StarRocks BI 与 CDC（debezium+PG）触发式。client-shared 同批增七节。依据：[telemetry-pipeline-digest.md](../docs/references/telemetry-pipeline-digest.md) | 已生效（2026-09-16） |
| 验证码框架 CP1-CP10（2026-09-18 拍板） | 新立 [api/captcha.md](api/captcha.md) v1.0：**Provider SPI + 框架核心**（进程内组件，非独立服务）；**首发三 Provider**（image 默认 / sms-otp / turnstile——OTP 分发通道宿主注入 SmsSender，yarch 不背通知抽象，EP7 届时对齐）；LOCAL/REMOTE 两类校验模式；**框架内置场景路由**（默认 Provider + scene→Provider 覆盖映射，租户覆盖归应用层扩展位）；**一次性原子消费**（GETDEL，java 存量 get→delete 两步视为缺陷随重构修复）；**2005 CAPTCHA_INVALID 三态合一**（error-codes v1.1 纯增量，四栈 conformance 14 码）+ 限流复用 1006；`GET /api/v1/captcha` 存量字段不动 + verify 内联业务流不设独立端点；字符集/长度入契约、渲染样式自由；java 首批 SPI 重构 + golang 对齐（五处漂移清偿）+ python 首批落地，三栈测试向量 V1-V11 同源；web/客户端零动作 | 已生效（2026-09-18） |

**租户边界标识**（服务名）登记处：[registry.md](registry.md)。

## 机器可读出口（dist/ · P1 主引擎，2026-09-17）

`contract/dist/` 是 markdown 契约的**派生机器工件**（权威仍是本目录 markdown）：`error-codes.json`（13 码全表 `{code, key, message, http, segment}` + success——key 即 i18n 稳定标识，EP3-R）与 `envelope.schema.json`（RestResponse / PageData 的 JSON Schema draft-07）。生成器 `gen-dist.mjs`（零依赖 Node）从 markdown 表格派生；**markdown 改动而 dist 未重生成 = CI 拒绝**（contract-dist workflow，同 registry-snapshot 漂移门机制）。四栈 conformance 测试读同一份 json 断言（java `GlobalErrorCodeContractTest` / golang `errcode_test` / python `test_errcode` / web `contract.test.ts`），不再各养手抄表。

## API 契约（REST 四件套 + 实时通道 + 埋点 + 审计 + 验证码）

| 契约 | 文件 | 一句话 |
|---|---|---|
| 响应形状 | [rest-response.md](api/rest-response.md) | code/message/data/traceId 四字段，0 即成功 |
| 错误码段位 | [error-codes.md](api/error-codes.md) | 一张跨语言 errno 段位表，yarch 拥有 0/1xxx/2xxx；标识列即 i18n 稳定 key（EP3-R）；v1.1 增 2005 |
| 日志与追踪 | [logging-trace.md](api/logging-trace.md) | 统一 JSON 行协议 + traceId 贯穿（W3C traceparent）；v1.1 增 spanId / 租户上下文 / 埋点矩阵行 |
| REST 约定 | [rest-conventions.md](api/rest-conventions.md) | 命名/分页/状态码/幂等，无方言；Accept-Language i18n 就绪位（EP3-R） |
| 实时通道 | [realtime.md](api/realtime.md) | WebSocket 长连接与推送：首帧鉴权 / 应用层心跳 / 退避重连 / PushEnvelope（复用 13 码表与 traceId 口径，独立于 RestResponse） |
| 埋点管道 | [telemetry.md](api/telemetry.md) | tracking plan 登记先行 / 业务域名批量上报（双阈值 + onHide·sendBeacon 兜底）/ 收口转 kafka 禁直写 CH / SDK 落契约内核首批三端 |
| 审计留存 | [audit.md](api/audit.md) | 合规通道：最小审计事件面 + ndjson 固定字段集 → CH append-only + 保留期 ≥180d，落 OperationLogStore SPI 的 CH 实现件 |
| 验证码框架 | [captcha.md](api/captcha.md) | Provider SPI（image/sms-otp/turnstile 三档）+ 框架核心（一次性原子消费 GETDEL / 场景路由 / 限流 1006 / 2005 三态合一）；verify 内联业务流不设独立端点 |

## 数据与中间件规约（infra/）

| 类别 | 规约 | 状态 | 一句话 |
|---|---|---|---|
| 关系库 | [MySQL](infra/mysql.md) | v1.0 已定稿 | 阿里黄山版数据库章条文化 + ORM 泛化；存量与既有生态 |
| 关系库 | [PostgreSQL](infra/postgresql.md) | v1.0 已定稿 | PostgresAI 风格 + GitLab 工程实践；**新项目默认库** |
| 关系库 | [TimescaleDB](infra/timescale.md) | v1.0 已定稿 | PG 体系内时序负载；继承 PG 规约，压缩/保留/连续聚合增量 |
| 文档库 | [MongoDB](infra/mongodb.md) | v1.0 已定稿 | 仅文档型刚需经评审引入；ESR 索引；默认仍是 PG |
| 缓存 | [Redis](infra/redis.md) | v1.0 已定稿 | key 前缀即租户边界 + big/hot key 口径 + 缓存一致性三防御 |
| 缓存 | [RediSearch](infra/redisearch.md) | v1.0 已定稿 | Redis 数据就地轻量查询；全文检索归 ES |
| 消息 | [Kafka](infra/kafka.md) | v1.0 已定稿 | **MQ 双轨之一：事件流/日志管道/数据集成**；幂等消费与 DLQ |
| 消息 | [RocketMQ](infra/rocketmq.md) | v1.0 已定稿 | **MQ 双轨之二：在线业务消息**（事务/延时/内建 DLQ）；订阅关系一致性 |
| 注册配置 | [Nacos](infra/nacos.md) | v1.0 已定稿 | Namespace=环境 / Group=租户边界；配置加密与灰度 |
| 网关 | [Nginx](infra/nginx.md) | v1.0 已定稿 | 入口/静态/TLS/简单代理；配置进 git；traceId 入口保证 |
| 网关 | [Higress](infra/higress.md) | v1.0 已定稿 | **API 网关**：路由编排/Consumer 鉴权/限流插件/AI 网关；声明式纳管（已对照官方文档校准） |
| 搜索 | [Elasticsearch](infra/elasticsearch.md) | v1.0 已定稿 | 全文/聚合主力；别名滚动 + ILM；禁当主存储 |
| 分析 | [ClickHouse](infra/clickhouse.md) | v1.0 已定稿 | **OLAP 之一：日志/遥测事件流分析**；攒批写入；mutation 高危 |
| 分析 | [StarRocks](infra/starrocks.md) | v1.0 已定稿 | **OLAP 之二：实时数仓与交互式 BI（重叠场景默认）** |
| 对象存储 | [MinIO/S3](infra/minio-s3.md) | v1.0 已定稿 | bucket=租户边界；key=业务分层；预签名 ≤15min；元数据在 DB |
| 向量 | [pgvector](infra/pgvector.md) | v1.0 已定稿 | **向量默认起步**：同库事务；模型隔离；HNSW/IVFFlat 参数登记 |
| 向量 | [Qdrant](infra/qdrant.md) | v1.0 已定稿 | **专用向量库升级档默认**（三档第二档）；named vectors 隔离多模型 |
| 向量 | [Milvus](infra/milvus.md) | v1.0 已定稿 | 三档第三档：亿级/GPU/超大规模保留项；依赖齐套 |
| 任务队列 | [Celery](infra/celery.md) | v1.0 已定稿 | Python 分布式任务队列；broker 前缀租户纪律；幂等/超时/重试显式 |
| 调度 | [XXL-Job](infra/xxl-job.md) | v1.0 已定稿 | Java 系时间驱动调度；幂等三级手段；调度只做时间驱动 |
| 可观测 | [Prometheus](infra/prometheus.md) | v1.0 已定稿 | metrics 唯一默认后端（exposition 标准）；标签基线 service/env；RED 基础指标集由装配件收口 |
| 可观测 | [Grafana](infra/grafana.md) | v1.0 已定稿 | 统一面板（Prometheus + CH 插件两件制）+ Grafana Alerting；dashboards/告警规则 provisioning 进 git |

## 前端规约（web/）

| 规约 | 状态 | 一句话 |
|---|---|---|
| [微前端](web/micro-frontend.md) | v1.0 已定稿 | 基座↔子应用彼此之间的契约：应用名一名三用（路由前缀/storage 前缀/事件前缀）/ 职责分界 / 通信三通道 / 登录态与 401 跳转唯一归基座 / 独立·集成双模式 / 独立发版；**条文载器无关**，载器分档见关键架构决策登记表 |

## 客户端规约（clients/ · 2026-09-09 新设层）

| 规约 | 状态 | 一句话 |
|---|---|---|
| [客户端共享契约](clients/client-shared.md) | v1.0 已定稿 | 平台无关：信封解包单点 / 错误三分类（业务·传输·取消）/ traceId 透传与报障凭证 / 网络纪律（超时单点·禁明文·取消传导）/ App 命名与登记 / 双端概念同构方言表 |
| [Android 客户端](clients/android.md) | v1.0 已定稿 | Kotlin+Compose+M3：UDF/ViewModel/Repository，Now in Android 工程范式（version catalog + convention plugins），Hilt，ktlint+detekt+Lint 三重机检，minsdk26 默认 / 24 扩展，targetSdk ≥ 36 硬线 |
| [iOS 客户端](clients/ios.md) | v1.0 已定稿 | Swift+SwiftUI+HIG：MVVM+@Observable（16 档降级 ObservableObject），xcodegen+SPM，SwiftLint（Airbnb 基准）+SwiftFormat，Swift Testing 新代码，privacy manifest 合规 |
| [小程序客户端](clients/miniprogram.md) | v1.0 已定稿 | 原生+TS 唯一入册栈（Taro/uni-app 不入默认档，多端诉求升级登记）；三段式结构（官方无旗舰参考工程，范式自选登记）/ miniprogram-ci 发布管线 / 主包 2MB·整包 30MB 预算 / wx.request 适配器注入 @yarch/contract / 体验评分 ≥90 机检 |
| [游戏客户端](clients/game.md) | v1.0 已定稿 | 分层边界（渲染与 web 禁共享，契约内核/领域逻辑/design token 三层共享）/ Cocos 默认 · Unity·团结 3D 重度双档 / 首包 4MB 硬线 / v1.0 先窄后宽（性能口径参考级，重度项目触发升格） |

## 领域契约（domains/）

新领域（机器人、AI 等）的唯一进门通道：先立领域契约评审定稿，再于至少两个语言侧实现（单一实现不成领域）。

| 契约 | 状态 | 一句话 |
|---|---|---|
| [device.md（设备接入）](domains/device.md) | v1.0 已定稿 | 设备标识两段 / MQTT topic 六段定式 / 遥测 ndjson 四字段（ts·traceId·metrics·fw）/ OTA 双分区+回滚+验签 / 影子+命令回执对齐信封 / TLS 强制一设备一证书 |
| ai.md（LLM 接入） | 规划 · 未成文 | 流式响应 / token 计费 / prompt 与 RAG 管道约定 |

> 命名消歧（2026-09-14）：`domains/ai.md` 规划位 = **LLM 接入**业务域（未成文）；`agent/`（下节）= **AI 施工代理**规约层（已定稿）——两者不同物，勿混称"ai 规约"。

> 首个合体验证候选项目：microduck（桌面机器人——云端 Go 接入侧 + esp32 Rust 固件侧，正好凑齐 device 契约的双方言验证）。

## AI 施工规约（agent/ · 2026-09-14 新设层）

约束对象不是某个栈，而是**全部工程被 AI 编码代理（Claude Code / Codex / Gemini CLI / Cursor / Copilot / ZCode 等）施工时的上下文与扩展出口**，以及 yarch 自家生成器 CLI 的交互形态。立项依据与生态矩阵：[ai-cli-ecosystem-digest.md](../docs/references/ai-cli-ecosystem-digest.md)。

| 规约 | 状态 | 一句话 |
|---|---|---|
| [agents-md.md](agent/agents-md.md) | v1.0 已定稿 | AGENTS.md 唯一事实源 + CLAUDE.md/GEMINI.md 一行派生 / 四章节定式（工程地图·命令表·红线清单·契约锚点）/ 篇幅 ≤150 行 / 锚点双要素引用 |
| [extensions.md](agent/extensions.md) | v1.0 已定稿 | 扩展出口纳管：.mcp.json 唯一入库位置 + 凭证 `${ENV}` 引用 / skills 走 `.agents/skills/` 跨厂商位置（.claude/skills 链接派生）/ hooks·subagents 参考级 |
| [cli.md](agent/cli.md) | v1.0 已定稿 | 自家 CLI 交互：一行命令铁律成文 / `--yes` 全非交互 / `--json` stdout 纯净 / 退出码 0·1·2 / 幂等与 `--force` / 成员登记表 |

## 契约版本

- API 契约：REST **四件套 v1.0 已定稿**（2026-09-01 评审通过，D1-D6 按"业界标准优先于阿里手册"拍板）；**实时通道 realtime.md v1.0 已定稿**（2026-09-16，RT1-RT9 拍板——企业级定位轨第一优先立项）；**埋点管道 telemetry.md v1.0 已定稿**（2026-09-16，TM1-TM8 拍板——第三轨）；**审计留存 audit.md v1.0 已定稿**（2026-09-16，OB7 拍板——EP4 并轨）；logging-trace **v1.1**（2026-09-16：spanId / 租户上下文 / 埋点矩阵行）
- infra 规约：**全部 22 份 v1.0 已定稿**（mysql/postgresql/redis/higress 2026-09-01 先行定稿并完成 higress 官方校准；其余 16 份同日评审通过；**prometheus + grafana 2026-09-16 增补**——OB3/OB4/OB6 拍板）
- 前端规约（web/）：**2026-09-07 新设层**，微前端 v1.0 已定稿（经 MF0-MF7 决策清单拍板）
- 客户端规约（clients/）：**2026-09-09 新设层**，client-shared + android + ios 三份 v1.0 已定稿（经 M1-M7 决策清单拍板）；2026-09-10 增 miniprogram + game 两份 v1.0（经 MP1-MP5/G1-G4 决策清单拍板），共五份
- 领域契约（domains/）：**2026-09-03 拍板设立**；device.md **v1.0 已定稿**（2026-09-14，Dv1-Dv6 拍板口径），ai.md（LLM 接入）规划中未成文
- AI 施工规约（agent/）：**2026-09-14 新设层**，agents-md + extensions + cli 三份 v1.0 已定稿（经 A1-A6 决策清单拍板）
- 各栈实现的 code/message/字段名与本目录不一致时，**以本目录为准，实现视为 bug**。

## 评审与变更记录

| 日期 | 事件 | 结论 |
|---|---|---|
| 2026-08-31 | contract 四件套 v1 草案成文 | 待 D1-D6 拍板 |
| 2026-09-01 | mysql / postgresql / redis 三份评审通过，升 v1.0 | 已定稿 |
| 2026-09-01 | 全量架构评审（22 份文档）：P0-2 跨进程传播矩阵、P0-3 网关故障面信封、P0-4 服务名登记处（registry.md）、P0-5 治理元文档、P1-1 认证鉴权、P1-2 幂等总则、P1-4 游标分页、P1-7 i18n 立场、P2 快修三项——已整改 | 本轮修订已合入 |
| 2026-09-01 | **D1-D6 拍板：业界标准优先于阿里手册**；API 四件套升 v1.0 定稿；分页越界语义按业界口径修订（空页不报错）；阿里系解析稿反推标注 | 已定稿 |
| 2026-09-01 | infra 15 份草案成文（nacos/kafka/rocketmq/es/redisearch/minio/mongodb/nginx/higress/xxl-job/ch/star/timescale/pgvector/milvus） | 待逐份评审 |
| 2026-09-01 | higress 条文对照官方文档逐条校准（Consumer 认证/限流插件实名/四级配置优先级），升 v1.0 定稿 | 已定稿 |
| 2026-09-01 | 新增「规约组合与依赖模型」：项目按需组合无全家桶假设；规约引用分三级（前置依赖硬 / 协作参考软 / 互斥分工），附依赖矩阵 | 已生效 |
| 2026-09-01 | 新增 qdrant.md（向量升三档，Qdrant 为专用档默认）与 celery.md（Python 任务队列，与 XXL-Job/MQ 分工落定）；infra 共 20 份 | 已评审 |
| 2026-09-01 | **infra 16 份草案评审通过，全部升 v1.0 定稿**——规约层 24 份全部定稿（api 四件套 + infra 20 份 + registry） | 已定稿 |
| 2026-09-03 | **全域扩展拍板**：设立 contract/domains/ 领域契约层（device / ai 首发规划）；yarch 定位升"云-边-端全域"；dotnet / php 裁撤不纳入 | 已生效 |
| 2026-09-07 | **新设 contract/web/ 前端规约层**：微前端规约 v1.0 定稿（条文载器无关；载器分档拍板 micro-app 默认 / qiankun 存量档 / Module Federation 同仓共享档 / wujie 不入册）；registry.md 新增前端应用名登记（首段 = 服务名、一名三用） | 已定稿 |
| 2026-09-09 | **新设 contract/clients/ 客户端规约层**：client-shared + android + ios 三份 v1.0 定稿（M1-M7 拍板：融合基准 Google 官方 / 官方 API 设计指南+Airbnb；xcodegen+SPM；Hilt；MVVM+@Observable；min 双基线分文件夹；targetSdk≥36）；registry.md 新增移动 App 登记（首段 = 服务名、双端包标识一致互为派生） | 已定稿 |
| 2026-09-10 | **clients 层扩两份**：miniprogram + game v1.0 定稿（MP1-MP5/G1-G4 拍板：原生+TS 默认档；渲染与 web 分开、三层共享；Cocos/Unity·团结双档；@yarch/contract 三端同源）；registry.md 新增第六节小程序与小游戏登记；client-shared 约束对象扩 game | 已定稿 |
| 2026-09-14 | **领域契约首发 + 双轨规约立项**：device.md v1.0 定稿（Dv1-Dv6：设备标识两段/topic 六段/遥测 ndjson/OTA 双分区/影子/信封回执/TLS 一设备一证书）；embedded/esp32 规约 v1.0 定稿（E1-E8：esp-idf-hal std 路线/no_std 不入册）；stacks/rust 规约 v1.0 定稿（R1-R7：axum 默认/cargo-generate 通道/crates.io 发版）——两轨零共享 crate | 已定稿 |
| 2026-09-14 | **新设 contract/agent/ AI 施工规约层**：agents-md + extensions + cli 三份 v1.0 定稿（A1-A6 拍板：AGENTS.md SSOT + 一行派生 / 四章节定式 / .mcp.json 与 skills 首批纳管 / 自家 CLI 一行命令铁律成文）；yarch 仓根 AGENTS.md 三件套带头合规；生态依据 digest 见 docs/references/ | 已定稿 |
| 2026-09-16 | **企业级定位修订 + 实时通道定稿**：EP1-EP12 拍板（目标场景补「企业级应用/游戏交付」；三轨排序 realtime > 可观测（含审计）> 埋点；多租户拆两层、i18n 仅就绪位、桌面端/支付协议层触发式登记）；[api/realtime.md](api/realtime.md) v1.0 定稿（RT1-RT9：WS 五端默认档 / 首帧 AUTH / 应用层心跳 / 退避三要素 / PushEnvelope 复用 13 码表）；client-shared 增六节（长连接与推送）；registry 三节增 wss 域名登记行。依据 digest：enterprise-capability-gap / realtime-channel | 已定稿 |
| 2026-09-16 | **企业级第二三轨定稿（可观测 + 埋点）**：OB1-OB9 + EP2-R/EP3-R、TM1-TM8 拍板——logging-trace **v1.1**（spanId / X-Tenant-Id 租户传播 / 埋点矩阵行，纯增量）；[api/telemetry.md](api/telemetry.md) v1.0（tracking plan 登记先行 / 业务域名批量上报 / 收口转 kafka 禁直写 CH）；[api/audit.md](api/audit.md) v1.0（审计最小事件面 → CH append-only ≥180d）；infra 增 [prometheus.md](infra/prometheus.md) + [grafana.md](infra/grafana.md)（共 22 份）；client-shared 增七节（埋点上报）；error-codes 实现规则 4（标识即稳定 key）+ rest-conventions Accept-Language 就绪位（EP3-R）。依据 digest：observability-track / telemetry-pipeline | 已定稿 |
| 2026-09-17 | **P1 契约机器可读出口落地**：`contract/dist/` 派生层开张（error-codes.json + envelope.schema.json，gen-dist.mjs 从 markdown 表格生成，contract-dist CI 漂移门）；四栈 conformance 改读同一份 json（java/golang/python/web 同源断言，缺文件优雅跳过）；N1 收口——java 双 archetype / golang / python 生成器模板补 AGENTS.md 三件套（java archetype 三件套走 unfiltered fileSet：Velocity 会把 markdown 的 ## 当行注释吞掉）+ web 五模板补 CLAUDE/GEMINI 一行派生 | 已生效 |
| 2026-09-18 | **验证码框架 CP1-CP10 拍板成文**：新立 [api/captcha.md](api/captcha.md) v1.0（Provider SPI 三档 + 一次性原子消费 GETDEL + 场景路由 + verify 内联业务流 + 跨栈测试向量附录）；error-codes **v1.1** 纯增量 2005 CAPTCHA_INVALID（dist 14 码，四栈 conformance 同步）；java `yarch-captcha-spring-boot-starter` 同批 SPI 重构（image/sms-otp/turnstile + GETDEL 缺陷修复），golang/python captcha 触发档对齐，web/客户端消费面零动作 | 已生效 |
| — | 遗留项：低频组件强制级占比复审；机检条文标注启动（下一步：随 stacks 重做启动 `yarch lint`/AI 审查清单） | 待办 |

## 术语对照（各栈方言）

| 概念 | java | golang | web | python |
|---|---|---|---|---|
| 响应体 | `RestResponse<T>` | `response.Response` | `RestResponse<T>` | `yarch_python.response.Response` |
| 分页负载 | `PageData<T>` | `response.PageData` | `PageData<T>` | `yarch_python.response.PageData` |
| 错误码 | `GlobalErrorCode` | `errcode.Code` | `errorCodes` 常量 | `errcode.Code` |
| 业务异常 | `BusinessException` | `xerror.BizError` | `ApiError` | `xerror.BizError` |
| 追踪 ID | `TraceIdFilter`(MDC) | `middleware.Trace()` | `apiClient` 注入/透出 | `middleware.TraceMiddleware`(contextvars) |

> **rust 两轨**（2026-09-14 立项，规约已立、工程未动工，实现落地后以实为准入表）：云栈 `stacks/rust`（axum，workspace 两 crate——`yarch_contract::response/errcode/trace` + `yarch_axum::middleware`）；固件轨 `embedded/esp32`（esp-idf-hal std，device.md 方言——非 REST 语义，信封哲学映射 MQTT resp 回执）。两轨零共享 crate。
