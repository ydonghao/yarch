# 契约 · Grafana（统一面板与告警，v1.0 已定稿）

> **状态：已定稿**（2026-09-16 经 OB4/OB6 拍板；调研依据 [../../docs/references/observability-track-digest.md](../../docs/references/observability-track-digest.md)）。可观测轨统一查询面：Prometheus（metrics）+ ClickHouse（traces/logs，插件数据源）。
> 等级：**【强制】**（开发行为）/ **【强制】**（部署基线，标注处）/ 【推荐】/【参考】。

## 一、定位

1. 【强制】Grafana = 观测栈**唯一统一面板**：数据源两件制——Prometheus（原生）+ ClickHouse（插件）；不为单信号另设查询 UI（业务自助分析走 StarRocks 档，触发式）。
2. 【强制】**dashboards as code**：面板 JSON 走 Grafana provisioning 进 git（业务仓或平台资产仓），**手工在 UI 上改的面板视为未登记资源**（同 nginx 配置进 git 哲学）。

## 二、告警

1. 【强制】告警 = **Grafana Alerting**（与面板同源，OB6 拍板）；告警规则 provisioning 进 git，通知渠道（webhook/IM）登记在业务仓 `docs/`（含接收人与升级路径）。
2. 【推荐】告警口径从 RED 基础四条起步（错误率 / P99 延迟 / 可用性 / 饱和度）；SLO 与 error budget 条文【参考】级（首个正式 SLO 运营时升格）。
3. 【强制】告警须带 runbook 链接（指向业务仓排障文档）——无 runbook 的告警 = 噪音。

## 三、账户与安全

1. 【强制】（部署基线）匿名访问禁用； viewers 只读 org 最小面；管理员账户走共享服务器凭证管理口径（不入 git）。
2. 【强制】数据源凭证 `${ENV}` 引用（对齐 [../agent/extensions.md](../agent/extensions.md) .mcp.json 凭证同款纪律）。

## 四、可机检条文清单

| 条文 | 机检手段 | 阶段 |
|---|---|---|
| 一-2 面板进 git | provisioning 目录扫描（UI 导出对账，CI 巡检） | 巡检 |
| 二-1 告警规则进 git | 同上（告警规则 provisioning 对账） | 巡检 |
| 二-3 runbook 链接 | 告警规则 lint（缺 annotation 拒绝） | CI |

---

## 附：来源与拍板记录

- 拍板记录（唯一登记处：[../README.md](../README.md) 决策登记表 OB 行）：2026-09-16 OB4/OB6——CH 统一 logs/traces 后端 + Grafana 单面板（CH 插件）；Grafana Alerting + 规则进 git。
- 实现节奏：观测栈 compose 资产（Prometheus/Grafana/Collector）触发式施工，随可观测轨首批装配。
