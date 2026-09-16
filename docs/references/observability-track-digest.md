# 可观测轨调研 digest（metrics · tracing · 日志采集 · 告警 · 审计留存，2026-09）

> **定位**：服务于可观测轨立项（EP9 第二轨，**含审计留存 EP4 并轨**）的调研与审阅材料；随批附 **EP2 租户上下文传播、EP3 错误码 i18n key** 两项增量决策。**规范性条文一律以 [../../contract/](../../contract/README.md) 为准**。**OB1-OB9 决策清单在文末**。
> 姊妹篇：[realtime-channel-digest.md](realtime-channel-digest.md)（第一轨，已定稿）；[telemetry-pipeline-digest.md](telemetry-pipeline-digest.md)（第三轨，同批出稿）。分界：**本轨管系统健康**（服务端指标/追踪/日志/审计）；埋点管用户行为与业务事件（第三轨）。

## 一、现状与目标

- **已有**：[logging-trace.md](../../contract/api/logging-trace.md) ndjson 行协议 + traceId 三级入口/跨进程传播矩阵 + 响应头回显；java actuator 存活检查。企业级三问里只答了「出事后的排障凭证」。
- **缺**：metrics（怎么知道坏了）、tracing（坏在哪一跳）、告警（谁来叫我）、审计留存（谁动过什么——合规问询口径）。B 端交付与 SLO 运营的第一道门槛（[enterprise-capability-gap-digest.md](enterprise-capability-gap-digest.md) 缺口一）。

## 二、技术骨架事实（2026-09 检索）

| 事实 | 出处口径 | 对 yarch 的含义 |
|---|---|---|
| OTel 三信号（traces/metrics/logs）在主流语言 SDK **全部 stable**；profiling 为第四信号（alpha） | [Apica OTel Best Practices 2026](https://www.apica.io/blog/opentelemetry-best-practices-for-improving-your-monitoring-and-observability/) | 骨架无争议：OTel 是唯一口径，不自拼 |
| OTel Collector 厂商中立采集管道已成事实标准；生产采用率 metrics（83%）> logs（61%）> traces（25%） | [OTel Collector 文档](https://opentelemetry.io/docs/collector/)、[官方调研](https://opentelemetry.io/blog/2026/otel-collector-follow-up-survey-analysis/) | 管道形态：SDK → Collector → 后端；metrics 是第一优先信号 |
| ClickHouse 官方 **ClickStack** 全栈观测（OTLP 收三信号存 CH）+ 官方 [clickhouse-otel-collector](https://clickhouse.com/docs/clickstack/ingesting-data/collector) + [contrib exporter](https://github.com/open-telemetry/opentelemetry-collector-contrib/blob/main/exporter/clickhouseexporter/README.md) | [ClickHouse 官方](https://clickhouse.com/blog/clickhouse-and-opentelemetry)、[存储 traces/spans 实战](https://clickhouse.com/blog/storing-traces-and-spans-open-telemetry-in-clickhouse) | CH 做 logs/traces 后端是官方支持路径而非野路子 |
| 实践者从 Loki 迁 CH 后「栈终于对了」；CH 查一天 3 亿行日志秒级；代价 = Grafana 里 CH 是**插件数据源**非原生后端 | [HN 讨论](https://news.ycombinator.com/item?id=48596743)、[Grafana 社区](https://community.grafana.com/t/loki-and-clickhouse-performance/150023)、[ClickHouse 官方对比](https://clickhouse.com/resources/engineering/best-open-source-observability-solutions) | 统一 CH 后端（OLAP 分析强）vs LGTM 三件套（面板集成深）是真实权衡 |
| Prometheus 拉取模型 + exposition 格式（`/metrics`）是 metrics 事实标准 | 业界共识 | metrics 不进 CH（计数器/直方图语义与拉取模型不合） |

## 三、选型分析（与 yarch 资产的接缝）

1. **契约兼容红利（本轨最便宜的一步）**：`traceparent` 已是 W3C 格式（trace-id + span-id 段都在头里）、traceId 32 位小写 hex 与 OTel trace-id 同形状、ndjson 字段（ts/level/service/traceId）与 OTel log record 同构——**升 span 语义是增量修订，不是重写**。
2. **后端组合判断**：yarch 已有 [clickhouse.md](../../contract/infra/clickhouse.md)（契约定位就是"日志/遥测事件流分析"）+ 共享一台服务器不重复部署中间件的铁律 → **logs/traces 后端 = CH 统一**，不新立 Loki/Tempo/Mimir 三件套；Grafana 单面板统一（CH 插件 + Prometheus 数据源）。LGTM 留触发档（Grafana 原生集成成为硬需求时）。
3. **日志通道不搬家**：ndjson stdout 是 [logging-trace.md](../../contract/api/logging-trace.md) 契约核心（四栈已实现+机检）——采集改为 Collector filelog receiver / 容器日志驱动 → CH，**OTel SDK 只承担 traces + metrics 两信号**，日志行协议一字不动（实现与契约不炸）。
4. **审计（EP4 并轨）**：审计事件 = 特殊 ndjson 通道（固定字段：principal/action/target/result/traceId/ts），CH append-only 表天然防篡改（无 UPDATE/DELETE）+ 保留期分级；与 java `OperationLogStore` / golang `middleware.OperationLog` 存储 SPI 的关系 = SPI 的 CH 实现件归本轨装配。
5. **deploy 边界**：本轨交付观测栈 compose 资产 + 告警规则进 git（同 nginx 配置进 git 哲学）；k8s/helm 维持触发式（G10 口径不变）。

## OB 决策清单（2026-09-16，待拍板）

| # | 议题 | 选项 | 建议 |
|---|---|---|---|
| OB1 | 骨架口径 | (a) OTel 三信号 + Collector 管道（SDK→Collector→后端）；(b) 无 Collector 直连后端；(c) 只加 metrics 不上 OTel | **(a)**——Collector 厂商中立是换后端的保险层 |
| OB2 | span 语义升级 | (a) logging-trace.md v1.1：入口解析 traceparent 时提取 spanId 进上下文与 ndjson 字段，响应头仍只回显 traceId；(b) 维持 traceId-only；(c) 全量 OTel 语义重写日志字段 | **(a)**——契约红利增量修订，排障粒度上一跳 |
| OB3 | metrics 后端与装配件 | (a) Prometheus + 各栈 exposition 标准（java micrometer/actuator、golang promhttp、python prometheus-client），yarch 装配件只做统一命名与基础指标集；(b) VictoriaMetrics；(c) 自建 | **(a)** |
| OB4 | logs/traces 后端 | (a) ClickHouse 统一（官方 ClickStack/exporter 路径）+ Grafana 面板；(b) LGTM 三件套；(c) ES 存 trace | **(a)**——复用契约与共享服务器；LGTM 登记触发档 |
| OB5 | traces 采样 | (a) 默认全量 + 采样率单配置点（parentbased，留档不强制）；(b) 默认采样 10% | **(a)**——个人/小规模全量最省心，规模上来再降 |
| OB6 | 告警 | (a) Grafana Alerting（与面板同源）+ 告警规则进 git；(b) Alertmanager 独立；(c) 不立契约 | **(a)**；SLO/error budget 条文【参考】级 |
| OB7 | 审计留存（EP4） | (a) 审计通道契约：固定 ndjson 字段 → CH append-only 表 + 保留期分级（默认 ≥180 天，登记可调）+ 禁改写；落 SPI 的 CH 实现件；(b) 触发式；(c) 不做 | **(a)**——合规问询的最小可交付物 |
| OB8 | 日志采集路线 | (a) ndjson stdout 契约不动，Collector filelog/容器驱动采集入 CH；OTel SDK 只管 traces+metrics；(b) 日志改走 OTel SDK 输出 | **(a)**——四栈已实现行协议不动，采集在侧车层 |
| OB9 | deploy 边界 | (a) 观测栈 compose 资产（Prometheus/Grafana/Collector；CH 共享服务器首次启用时登记 infra 组合）+ 规则进 git；k8s/helm 触发式；(b) 直接上 k8s 模板 | **(a)**——与 G10「运维域将来承接」口径衔接 |

## 附：随批增量决策（EP2 / EP3，2026-09-16 EP 拍板的落地方案，待拍板）

| # | 议题 | 选项 | 建议 |
|---|---|---|---|
| EP2-R | 租户上下文传播落点 | (a) 最小传播契约：logging-trace.md 传播矩阵增 `X-Tenant-Id` 行（入口解析 → 上下文 → 日志字段 → 下游透传 → MQ 消息属性），隔离模型（schema/库/行级三档）登记决策表但归业务仓执行；(b) 连隔离实现一起立；(c) 不落条文 | **(a)**——机制归 yarch、模型归业务的 EP2 拍板口径直接条文化 |
| EP3-R | 错误码 i18n key 落点 | (a) 支柱 1 的 error-codes.json 必含 `{code, key, message}` 三元组（key 即现有「标识」列，客户端按 key 渲染本地文案的 catalog 基础）+ REST 增 `Accept-Language` 透传行；多语言文案 catalog 不做；(b) 完整 i18n 契约；(c) 不落 | **(a)**——近零成本就绪位，P1 施工时一并落 |

## 拍板后动作

按机检路线：本 digest + OB/EP2-R/EP3-R 清单 → 拍板 → 条文成文（logging-trace.md v1.1 增 spanId 与租户行、审计通道新立 `api/` 或 `infra/` 视 OB7 拍板、`infra/` 增 prometheus/grafana 视 OB3/OB4）→ contract/README 决策表登记 → 四栈装配件触发式排队（首个消费工程或企业级试点）。

## 来源

[OpenTelemetry Collector](https://opentelemetry.io/docs/collector/) · [OTel 2026 博客](https://opentelemetry.io/blog/2026/) · [Collector 采用率调研](https://opentelemetry.io/blog/2026/otel-collector-follow-up-survey-analysis/) · [Apica · OTel Best Practices 2026](https://www.apica.io/blog/opentelemetry-best-practices-for-improving-your-monitoring-and-observability/) · [ClickStack · OTel Collector 接入](https://clickhouse.com/docs/clickstack/ingesting-data/collector) · [ClickHouse 与 OpenTelemetry](https://clickhouse.com/blog/clickhouse-and-opentelemetry) · [CH 存储 traces/spans 实战](https://clickhouse.com/blog/storing-traces-and-spans-open-telemetry-in-clickhouse) · [contrib clickhouse exporter](https://github.com/open-telemetry/opentelemetry-collector-contrib/blob/main/exporter/clickhouseexporter/README.md) · [CH 官方观测方案对比](https://clickhouse.com/resources/engineering/best-open-source-observability-solutions) · [HN · Loki→CH 迁移讨论](https://news.ycombinator.com/item?id=48596743) · [Grafana 社区 · Loki vs CH](https://community.grafana.com/t/loki-and-clickhouse-performance/150023)。仓内锚点：logging-trace.md / clickhouse.md / elasticsearch.md / nginx.md / development-roadmap.md G10（2026-09-16 检索）
