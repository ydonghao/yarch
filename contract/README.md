# contract/ · 跨栈统一契约与规约

> 多栈脚手架的灵魂：各栈长得不一样没关系，必须说同一种接口语言。
> 本目录是全部栈实现（java / golang / rust / web / python / node / embedded 固件 / clients 移动端契约适配）的**唯一权威来源**，改契约必须先改这里。

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

**租户边界标识**（服务名）登记处：[registry.md](registry.md)。

## API 契约四件套

| 契约 | 文件 | 一句话 |
|---|---|---|
| 响应形状 | [rest-response.md](api/rest-response.md) | code/message/data/traceId 四字段，0 即成功 |
| 错误码段位 | [error-codes.md](api/error-codes.md) | 一张跨语言 errno 段位表，yarch 拥有 0/1xxx/2xxx |
| 日志与追踪 | [logging-trace.md](api/logging-trace.md) | 统一 JSON 行协议 + traceId 贯穿（W3C traceparent） |
| REST 约定 | [rest-conventions.md](api/rest-conventions.md) | 命名/分页/状态码/幂等，无方言 |

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

## 前端规约（web/）

| 规约 | 状态 | 一句话 |
|---|---|---|
| [微前端](web/micro-frontend.md) | v1.0 已定稿 | 基座↔子应用彼此之间的契约：应用名一名三用（路由前缀/storage 前缀/事件前缀）/ 职责分界 / 通信三通道 / 登录态与 401 跳转唯一归基座 / 独立·集成双模式 / 独立发版；**条文载器无关**，载器分档见关键架构决策登记表 |

## 领域契约（domains/ · 规划层）

新领域（机器人、AI 等）的唯一进门通道：先立领域契约评审定稿，再于至少两个语言侧实现（单一实现不成领域）。首发规划（2026-09-03 拍板，均未成文，启动前按工作流先出决策清单）：

| 契约 | 状态 | 一句话 |
|---|---|---|
| device.md（设备接入） | 规划 · 未成文 | MQTT topic 约定 / 遥测 schema（复用 [api/logging-trace.md](api/logging-trace.md) JSON 口径）/ OTA 包格式；云端栈与 embedded/esp32 固件同表实现 |
| ai.md（LLM 接入） | 规划 · 未成文 | 流式响应 / token 计费 / prompt 与 RAG 管道约定 |

> 首个合体验证候选项目：microduck（桌面机器人——云端 Go 接入侧 + esp32 固件侧，正好凑齐 device 契约的双方言验证）。

## 契约版本

- API 契约：**v1.0 已定稿**（2026-09-01 评审通过，D1-D6 按"业界标准优先于阿里手册"拍板）
- infra 规约：**全部 20 份 v1.0 已定稿**（mysql/postgresql/redis/higress 2026-09-01 先行定稿并完成 higress 官方校准；其余 16 份同日评审通过）
- 前端规约（web/）：**2026-09-07 新设层**，微前端 v1.0 已定稿（经 MF0-MF7 决策清单拍板）
- 领域契约（domains/）：**2026-09-03 拍板设立**，device / ai 首发规划中，均未成文
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
| — | 遗留项：低频组件强制级占比复审；机检条文标注启动（下一步：随 stacks 重做启动 `yarch lint`/AI 审查清单） | 待办 |

## 术语对照（各栈方言）

| 概念 | java | golang | rust | web |
|---|---|---|---|---|
| 响应体 | `RestResponse<T>` | `response.Response` | `RestResponse<T>` | `RestResponse<T>` |
| 分页负载 | `PageData<T>` | `response.PageData` | `PageData<T>` | `PageData<T>` |
| 错误码 | `GlobalErrorCode` | `errcode.Code` | `ErrorCode` | `errorCodes` 常量 |
| 业务异常 | `BusinessException` | `xerror.BizError` | `BizError` | `ApiError` |
| 追踪 ID | `TraceIdFilter`(MDC) | `middleware.Trace()` | `trace_middleware` | `apiClient` 注入/透出 |
