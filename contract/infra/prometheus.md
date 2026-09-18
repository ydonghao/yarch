# 契约 · Prometheus（metrics 后端，v1.0 已定稿）

> **状态：已定稿**（2026-09-16 经 OB3 拍板；调研依据 [../../docs/references/observability-track-digest.md](../../docs/references/observability-track-digest.md)）。可观测轨（EP9 第二轨）metrics 信号后端——三信号中唯一拉取模型，**不进 ClickHouse**（计数器/直方图语义与拉取模型不合）。
> 等级：**【强制】**（开发行为）/ **【强制】**（部署基线，标注处）/ 【推荐】/【参考】。

## 一、定位与组合

1. 【强制】Prometheus = yarch 可观测栈 metrics 唯一默认后端（exposition 事实标准）；替代档不设（VictoriaMetrics 等仅在高基数/长保留成为真实痛点时评估）。
2. 【强制】【部署基线】观测栈为**独立部署组**（Prometheus + Grafana + OTel Collector），不按服务拆租户实例——与共享中间件（PG/Redis 按前缀隔离）不同，metrics 是全局视图；服务以**标签**区分（见二-1）。共享服务器首次启用时按 [../README.md](../README.md) 组合模型登记。
3. 【参考】traces/logs 后端 = ClickHouse（[clickhouse.md](clickhouse.md)，OB4 拍板）；LGTM 三件套（Loki/Tempo/Mimir）登记为触发档（Grafana 原生集成成为硬需求时）。

## 二、指标口径

1. 【强制】标签基线：每条业务服务指标必带 `service`（registry 服务名）/ `env`；`tenantId` 仅多租户上下文存在时打标（高基数标签禁用，对齐 [logging-trace.md](../api/logging-trace.md) v1.1 租户行）。
2. 【强制】命名：`<域>_<名>_<单位后缀>` snake_case（如 `order_created_total`、`http_request_duration_seconds`）；RED 基础指标集（rate/errors/duration）由各栈装配件内置，业务工程只补业务指标。
3. 【强制】各栈 exposition 通道：java micrometer/actuator（`/actuator/prometheus`）、golang promhttp（`/metrics`）、python prometheus-client——**统一基础指标命名由 yarch 装配件收口**，业务工程不自定义命名风格。

## 三、采集与保留

1. 【强制】（部署基线）scrape 间隔默认 30s（可用性要求高的服务 15s 由业务仓登记）；job/实例以服务名命名。
2. 【强制】（部署基线）保留默认 15 天（单机档；长保留需求走远程存储触发式评估——不为默认场景提前引入）。

## 四、可机检条文清单

| 条文 | 机检手段 | 阶段 |
|---|---|---|
| 二-1 标签基线 | 装配件单测（exposition 样本断言） | 单测 |
| 二-2 RED 基础指标集 | 四栈 conformance 同表断言（对齐 14 码表机检先例） | CI |
| 二-3 命名风格 | 装配件 lint（业务指标注册时校验 snake_case + 单位后缀） | CI |

---

## 附：来源与拍板记录

- 拍板记录（唯一登记处：[../README.md](../README.md) 决策登记表 OB 行）：2026-09-16 OB3——Prometheus + exposition 标准 + 装配件收口命名与 RED 集。
- 实现节奏：观测栈 compose 资产与四栈装配件触发式（首个消费工程或企业级试点）。
