# 契约 · 错误码全局段位表（v1.0 已定稿）

> **状态：已定稿**（2026-09-01 评审通过；D1-D6 已拍板，口径为"业界标准优先于阿里手册"，结论见 [../../docs/references/alibaba-java-manual-digest.md](../../docs/references/alibaba-java-manual-digest.md) D 表）。
>
> **D1 已拍板**：采用 `int32 数字段位 + 标识符`（与 HTTP 状态码+reason phrase、gRPC code+name 的业界形态同构）；**不采用**阿里 5 位字符串 A/B/C 方案——按"谁的错"分段偏客服排障视角，与按"谁拥有"分段的多仓治理诉求冲突，且 int32 对四栈类型系统与序列化最友好。

目标：任何栈、任何服务报出的同一个 `code`，语义唯一且同源。

## 段位分配

| 段位 | 归属 | 内容 |
|---|---|---|
| `0` | yarch | 成功 |
| `1xxx` | yarch | 通用段：任何工程都需要的框架级错误 |
| `2xxx` | yarch | 认证与权限段（IAM）：登录态/凭证/授权 |
| `3xxx` – `8xxx` | 业务工程 | **各业务仓自行注册**（在业务仓 docs 登记后方可使用），yarch 不内置、不预测 |
| `9xxx` | 预留 | 不得使用 |

> 铁律：yarch 只拥有 `0`、`1xxx`、`2xxx`。出现租户/配额/INV 等领域语义的码，一律属于业务工程（如 ysaas 在其仓内登记 `3xxx` 段）。

## 通用段 1xxx（yarch 内置，各栈必须等价实现）

| code | 标识 | message（默认文案） | HTTP |
|---|---|---|---|
| 1000 | INTERNAL_ERROR | 内部错误 | 500 |
| 1001 | INVALID_ARGUMENT | 参数校验失败 | 400 |
| 1002 | MALFORMED_BODY | 请求体格式错误 | 400 |
| 1003 | （留空） | — | — |
| 1004 | NOT_FOUND | 资源不存在 | 404 |
| 1005 | CONFLICT | 资源冲突 | 409 |
| 1006 | RATE_LIMITED | 触发限流 | 429 |
| 1007 | IDEMPOTENCY_CONFLICT | 幂等冲突：重复提交 | 409 |
| 1008 | UPSTREAM_TIMEOUT | 上游依赖超时 | 504 |
| 1009 | UNAVAILABLE | 服务暂不可用 | 503 |

> 1008 / 1009 可由**网关层**代替后端发出（后端不可达/熔断/过载场景，见 [rest-response.md](rest-response.md)「网关故障面」）；发出方必须保持 RestResponse 信封与 `X-Trace-Id` 回显。

## 认证与权限段 2xxx（yarch 内置）

| code | 标识 | message（默认文案） | HTTP |
|---|---|---|---|
| 2001 | UNAUTHORIZED | 未认证 | 401 |
| 2002 | CREDENTIALS_EXPIRED | 凭证已过期 | 401 |
| 2003 | FORBIDDEN | 权限不足 | 403 |
| 2004 | ACCOUNT_DISABLED | 账号已禁用 | 403 |

## 实现规则

1. `message` 允许在默认文案后追加冒号细节（如 `参数校验失败：pageSize 必须 ≤ 100`），默认文案部分不得改写；
2. 同一 code 的 HTTP 映射**全栈一致**，以本表为准；
3. 未注册的业务码（3xxx-8xxx 未登记即用）在 CI 阶段视为违规；
4. 各栈标识符命名对照：

| 标识 | java `GlobalErrorCode` | golang `errcode` | rust `ErrorCode` | ts `errorCodes` |
|---|---|---|---|---|
| INTERNAL_ERROR | `INTERNAL_ERROR` | `InternalError` | `InternalError` | `INTERNAL_ERROR` |
| INVALID_ARGUMENT | `INVALID_ARGUMENT` | `InvalidArgument` | `InvalidArgument` | `INVALID_ARGUMENT` |
| MALFORMED_BODY | `MALFORMED_BODY` | `MalformedBody` | `MalformedBody` | `MALFORMED_BODY` |
| NOT_FOUND | `NOT_FOUND` | `NotFound` | `NotFound` | `NOT_FOUND` |
| CONFLICT | `CONFLICT` | `Conflict` | `Conflict` | `CONFLICT` |
| RATE_LIMITED | `RATE_LIMITED` | `RateLimited` | `RateLimited` | `RATE_LIMITED` |
| IDEMPOTENCY_CONFLICT | `IDEMPOTENCY_CONFLICT` | `IdempotencyConflict` | `IdempotencyConflict` | `IDEMPOTENCY_CONFLICT` |
| UPSTREAM_TIMEOUT | `UPSTREAM_TIMEOUT` | `UpstreamTimeout` | `UpstreamTimeout` | `UPSTREAM_TIMEOUT` |
| UNAVAILABLE | `UNAVAILABLE` | `Unavailable` | `Unavailable` | `UNAVAILABLE` |
| UNAUTHORIZED | `UNAUTHORIZED` | `Unauthorized` | `Unauthorized` | `UNAUTHORIZED` |
| CREDENTIALS_EXPIRED | `CREDENTIALS_EXPIRED` | `CredentialsExpired` | `CredentialsExpired` | `CREDENTIALS_EXPIRED` |
| FORBIDDEN | `FORBIDDEN` | `Forbidden` | `Forbidden` | `FORBIDDEN` |
| ACCOUNT_DISABLED | `ACCOUNT_DISABLED` | `AccountDisabled` | `AccountDisabled` | `ACCOUNT_DISABLED` |
