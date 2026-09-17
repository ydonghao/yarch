# AGENTS.md — 工程守则

> 本工程由 yarch yarch-init（golang）生成，契约唯一权威 = yarch 仓 contract/（全部 v1.0 定稿）。
> 任何 AI 编码代理改码前先读本文件；条文与直觉冲突时以契约为准，红线改动直接拒绝——机检与 CR 均按此执行。

## 工程地图

| 目录 | 职责 |
|---|---|
| main.go | 单入口：装配根（EnsureDatabase → Migrate → Open），启动顺序勿乱 |
| api/ | REST 适配层：handler / 绑定（web.Bind 1001/1002 分型） |
| application/ | 应用服务：用例编排 |
| domain/ | 领域层：零框架依赖（golangci-lint depguard 机检） |
| crossdomain/ | 域间防腐层：域调用必须经此，禁直连 |
| infra/ | 端口实现：持久化 / 缓存 / 外部客户端 |
| pkg/ / types/ / conf/ | 共享工具 / 出入参 / 配置 |

## 命令表

| 场景 | 命令 |
|---|---|
| 验证（改完必跑） | go test ./... && golangci-lint run |
| 单测 | go test ./domain/... -race |

## 红线清单

| 红线 | 规则 | 出处 |
|---|---|---|
| 分层方向 | api → application → domain 单向；domain 零框架依赖（depguard 拦截）；域间只走 crossdomain | contract/api + depguard |
| 统一信封 | 响应一律 response.Response 四字段；错误码只用 errcode 13 码 + 业务码 3xxx 先登记 | error-codes.md |
| 幂等 | unsafe 方法 Idempotency-Key 中间件（同键回放 / 异参 1007） | rest-conventions.md |
| traceId | logx slog ndjson + traceparent 解析；排障凭证 = traceId | logging-trace.md |
| 命名 | 服务名即租户边界（Redis 前缀 / 幂等键首段），改名 = registry 变更评审 | registry.md |

## 契约锚点

yarch 仓 contract/api/（REST 四件套）· contract/infra/postgresql.md · redis.md——实现与本仓不一致 = 实现 bug。
