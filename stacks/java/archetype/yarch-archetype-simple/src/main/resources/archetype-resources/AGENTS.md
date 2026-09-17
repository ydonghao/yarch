# AGENTS.md — 工程守则

> 本工程由 yarch-archetype-simple 生成，契约唯一权威 = yarch 仓 contract/（全部 v1.0 定稿）。
> 任何 AI 编码代理改码前先读本文件；条文与直觉冲突时以契约为准，红线改动直接拒绝——机检与 CR 均按此执行。

## 工程地图

| 目录 | 职责 |
|---|---|
| src/main/java/**/controller | REST 适配层：参数校验进这里，禁业务逻辑 |
| src/main/java/**/service | 业务逻辑：事务边界 |
| src/main/java/**/dao | 数据访问：MyBatis-Plus mapper |
| src/main/java/**/manager | 跨域通用逻辑（可选层） |
| src/main/java/**/model / types | 数据模型 / 出入参对象 |
| src/main/resources/db/migration | Flyway 版本化迁移（禁手工改库） |

## 命令表

| 场景 | 命令 |
|---|---|
| 验证（改完必跑） | mvn verify —— Spotless + 全量测试 |
| 格式化 | mvn spotless:apply（AOSP 4 空格） |

## 红线清单

| 红线 | 规则 | 出处 |
|---|---|---|
| 分层方向 | controller → service → dao 单向，禁 dao 直入 controller | contract/api |
| 统一信封 | 响应一律 RestResponse 四字段；错误码只用 GlobalErrorCode（1xxx 通用 / 2xxx 认证），业务码 3xxx+ 先在业务仓登记 | error-codes.md |
| 幂等 | unsafe 方法支持 Idempotency-Key（同键回放 / 异参 1007） | rest-conventions.md 幂等总则 |
| traceId | ndjson 日志 + traceparent 解析；排障凭证 = traceId，报障必附 | logging-trace.md |
| 迁移 | schema 变更只走 Flyway 版本化文件，禁 DDL 直改库 | postgresql.md |

## 契约锚点

yarch 仓 contract/api/（REST 四件套）· contract/infra/postgresql.md · redis.md——实现与本仓不一致 = 实现 bug。
