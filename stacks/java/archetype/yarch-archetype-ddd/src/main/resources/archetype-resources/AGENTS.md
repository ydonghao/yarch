# AGENTS.md — 工程守则

> 本工程由 yarch-archetype-ddd 生成，契约唯一权威 = yarch 仓 contract/（全部 v1.0 定稿）。
> 任何 AI 编码代理改码前先读本文件；条文与直觉冲突时以契约为准，红线改动直接拒绝——机检与 CR 均按此执行。

## 工程地图

| 目录 | 职责 |
|---|---|
| src/main/java/**/api | REST 适配层：Controller / Assembler，禁业务逻辑 |
| src/main/java/**/application | 应用服务：用例编排、事务边界 |
| src/main/java/**/domain | 领域层：实体 / 值对象 / 端口——零框架依赖（ArchUnit 机检） |
| src/main/java/**/infrastructure | 端口实现：持久化 / 缓存 / 外部客户端 |
| src/main/resources/db/migration | Flyway 版本化迁移（禁手工改库） |

## 命令表

| 场景 | 命令 |
|---|---|
| 验证（改完必跑） | mvn verify —— Spotless + ArchUnit + 全量测试 |
| 格式化 | mvn spotless:apply（AOSP 4 空格） |

## 红线清单

| 红线 | 规则 | 出处 |
|---|---|---|
| 分层方向 | api → application → domain 单向；domain 零框架依赖，禁反向（ArchUnit 拦截） | contract/api + ArchUnit |
| 统一信封 | 响应一律 RestResponse 四字段；错误码只用 GlobalErrorCode（1xxx 通用 / 2xxx 认证），业务码 3xxx+ 先在业务仓登记 | error-codes.md |
| 幂等 | unsafe 方法支持 Idempotency-Key（同键回放 / 异参 1007） | rest-conventions.md 幂等总则 |
| traceId | ndjson 日志 + traceparent 解析；排障凭证 = traceId，报障必附 | logging-trace.md |
| 迁移 | schema 变更只走 Flyway 版本化文件，禁 DDL 直改库 | postgresql.md |

## 契约锚点

yarch 仓 contract/api/（REST 四件套）· contract/infra/postgresql.md · redis.md——实现与本仓不一致 = 实现 bug。
