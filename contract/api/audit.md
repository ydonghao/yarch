# 契约 · 审计留存（合规通道，v1.0 已定稿）

> **状态：已定稿**（2026-09-16 经 OB7（含 EP4）拍板；调研依据 [../../docs/references/observability-track-digest.md](../../docs/references/observability-track-digest.md) 附录）。企业采购合规问询的最小可交付物。
> 等级：**【强制】**违反即缺陷；**【推荐】**默认遵守、评审后可豁免；**【参考】**正向引导。
> 分工：`@OperationLog`（java）/ `middleware.OperationLog`（golang）是**通用操作日志**（ndjson + 存储 SPI）；本规约定义其**合规子集**——必须审计什么、字段固定形状、不可篡改留存、保留期口径。

## 一、审计范围（最小事件面）

1. 【强制】以下操作必须产生审计事件（业务工程可增不可减）：**特权操作**（管理端越权敏感的写操作）、**认证与授权变更**（登录/登出/权限分配/密钥轮换）、**数据导出与批量变更**（导出/删除/恢复）、**配置与发布动作**（配置变更/功能开关）。
2. 【强制】审计事件是**旁路**：审计管道故障不得阻断业务（先落本地 ndjson，补传机制由存储实现件承担）——与操作日志同哲学。

## 二、事件协议（ndjson 固定字段）

行协议对齐 [logging-trace.md](logging-trace.md)（ts/level/service/env/logger 语义同源），审计通道**固定字段集**：

| 字段 | 类型 | 必填 | 语义 |
|---|---|---|---|
| `ts` | ISO-8601 UTC | 是 | 事件时刻（D4） |
| `service` / `env` | string | 是 | 服务名 / 环境 |
| `traceId` | string | 是 | 排障关联（入口 traceId） |
| `principal` | string | 是 | 操作主体（用户 ID / 服务账号 / `anonymous`） |
| `action` | string | 是 | 动作名 kebab-case（如 `user.export`、`role.assign`——域.动两级） |
| `target` | string | 是 | 操作对象（资源 ID 或类目） |
| `result` | string | 是 | `success` / `denied` / `failed`（被拒绝的越权尝试正是审计价值所在） |
| `detail` | object | 否 | 自由键值补充（camelCase，禁 PII 明文） |

## 三、存储与留存

1. 【强制】存储 = ClickHouse **append-only 表**（天然无 UPDATE/DELETE，即不可篡改基线；建表/分区/保留条文随 [../infra/clickhouse.md](../infra/clickhouse.md)）；落通道 = 既有 `OperationLogStore` SPI（java/golang）的 CH 实现件——不发明第二套审计 SDK。
2. 【强制】保留期默认 **≥180 天**，分级登记（业务仓 `docs/` 可上调不可下调；涉个保法数据的保留期与业务仓合规口径对齐）。
3. 【强制】备份口径：审计表随 CH 例行备份；导出查询须记录导出者与范围（导出本身也是一节-1 的审计事件）。

## 四、可机检条文清单

| 条文 | 机检手段 | 阶段 |
|---|---|---|
| 一-1 审计面覆盖 | 装配件路由清单核对（特权/认证/导出/配置四类路由必有审计注解或声明） | CI |
| 二 字段固定集 | 存储实现件 schema 校验（缺必填字段拒绝写入并告警） | 运行时 |
| 三-2 保留期下限 | CH 表 DDL 巡检（TTL ≥180d） | 巡检 |

---

## 附：来源与拍板记录

- 拍板记录（唯一登记处：[../README.md](../README.md) 关键架构决策登记表 OB 行，此处为引用）：2026-09-16 OB7——固定 ndjson 字段 → CH append-only + 保留期分级 ≥180d + 禁改写；落 OperationLogStore SPI 的 CH 实现件。
- 依据：[observability-track-digest.md](../../docs/references/observability-track-digest.md) 三-4（CH append-only 天然防篡改判断）。
- 实现节奏：CH 实现件与观测栈 compose 资产同批触发式施工（首个消费工程或企业级试点）。
