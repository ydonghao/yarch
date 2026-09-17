# AGENTS.md — 工程守则

> 本工程由 yarch-init（python / uv）生成，契约唯一权威 = yarch 仓 contract/（全部 v1.0 定稿）。
> 任何 AI 编码代理改码前先读本文件；条文与直觉冲突时以契约为准，红线改动直接拒绝——机检与 CR 均按此执行。

## 工程地图

| 目录 | 职责 |
|---|---|
| main.py / celery_app.py | 双入口：Web / worker，启动装配各自单点 |
| api/ | REST 适配层：路由 / 依赖注入 |
| application/ | 应用服务：用例编排 |
| domain/ | 领域层：实体 / 端口（Protocol）——零框架依赖（import-linter 机检） |
| crossdomain/ | 域间防腐层：域调用必须经此 |
| infrastructure/ | 端口实现：SQLAlchemy / redis / httpx |
| errors/ / pkg/ / conf/ / types 值对象 | 出入参 / 工具 / 三环境配置 |
| tests/ | pytest（TC PG/Redis 基座） |

## 命令表

| 场景 | 命令 |
|---|---|
| 验证（改完必跑） | uv run pytest && uv run ruff check . && uv run lint-imports && uv run mypy |
| 修格式 | uv run ruff check --fix . |

## 红线清单

| 红线 | 规则 | 出处 |
|---|---|---|
| 分层方向 | api → application → domain 单向；domain 零框架依赖（import-linter 拦截）；域间只走 crossdomain | contract/api + import-linter |
| 统一信封 | 响应一律 Response 四字段；错误码只用 errcode 13 码 + 业务码 3xxx 先登记 | error-codes.md |
| 幂等 | unsafe 方法幂等中间件（同键回放 / 异参 1007） | rest-conventions.md |
| traceId | structlog ndjson + traceparent 解析；排障凭证 = traceId | logging-trace.md |
| 迁移 | Alembic 纯 SQL 版本化，禁 create_all | postgresql.md |

## 契约锚点

yarch 仓 contract/api/（REST 四件套）· contract/infra/postgresql.md · redis.md · celery.md——实现与本仓不一致 = 实现 bug。
