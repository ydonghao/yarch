# 契约 · REST 约定（v1.0 已定稿）

> **状态：已定稿**（2026-09-01 评审通过；D1-D6 已拍板，口径为"业界标准优先于阿里手册"，结论见 [../../docs/references/alibaba-java-manual-digest.md](../../docs/references/alibaba-java-manual-digest.md) D 表）。

各栈 API 无方言：同一语义在任何服务里长得一样。

## 资源与命名

- 路径前缀带主版本：`/api/v1/...`；不兼容变更升 `v2`，旧版本并行至下线
- 资源名词、复数、kebab-case：`/api/v1/users`、`/api/v1/order-items`
- 层级表达从属（不超过两层）：`/api/v1/users/{userId}/orders`
- 非 CRUD 动作用动词子资源，仅限 POST：`POST /api/v1/orders/{orderId}/cancel`

## 方法与状态码

| 方法 | 语义 | 成功响应 |
|---|---|---|
| GET | 查询，无副作用 | `200` + `RestResponse` |
| POST | 创建 | `201` + `RestResponse`（含新资源） |
| PUT | 全量更新 | `200` |
| PATCH | 部分更新 | `200` |
| DELETE | 删除 | `200` + `RestResponse`（含删除结果），不留 `204` 空体特例 |

允许使用的 HTTP 状态码白名单：`200 201 400 401 403 404 409 429 500 503 504`。其余一律不出现在业务 API（网关/基础设施自定除外）。

## 查询约定

- 分页（页码通道）：`?page=1&pageSize=20`（1-based；`pageSize` 默认 20，上限 100）。**参数校验**：`page` 非正整数或 `pageSize` 超上限 → `1001`。**越界语义（D6 已拍板）**：`page` 超出总页数 → `200` + `code=0` + 空 `list` + 真实 `total`（返回边界事实，不纠错不报错——业界通行口径，GitHub/Stripe 同；阿里"收敛到末页"方案不采用）
- 分页（游标通道，可选）：`?cursor={nextCursor}&pageSize=20`——深翻页与高频增量拉取场景必须提供游标通道（与 PG/ES/Kafka 的 keyset 分页条文对齐）；响应 `data.nextCursor` 透出下一页游标（见 [rest-response.md](rest-response.md)）
- 排序：`?orderBy=createdAt&order=desc`（单字段；多字段用逗号，方向缺省 asc）
- 过滤：字段名 camelCase 精确匹配；区间用 `createdFrom` / `createdTo` 后缀
- 分页响应 `data` 固定为 `PageData{list,total,page,pageSize,nextCursor?}`

## 认证与鉴权

- 传输安全：生产一律 TLS（入口终结见 [../infra/nginx.md](../infra/nginx.md)）
- 认证协议：`Authorization: Bearer <token>`（JWT，RS/ES 非对称签名优先）；网关层持有 consumer 凭证并校验 JWT（见 [../infra/higress.md](../infra/higress.md) 三），服务间内网调用可由网络边界/mTLS 豁免透传用户身份头
- 错误联动：无凭证/无效签名 → `401` + `2001`；token 过期 → `401` + `2002`（客户端应引导重登录，禁无脑重试）；权限不足 → `403` + `2003`；账号禁用 → `403` + `2004`
- 凭证纪律：token/密钥禁入 URL 参数与日志（日志脱敏对齐各 infra 规约）；凭证轮换有流程与双跑窗口

## 幂等总则（各 infra 幂等条文的唯一上位口径）

1. **外部幂等（API 层）**：unsafe 方法可带 `Idempotency-Key`（客户端 UUID，作用域 = 单服务单资源类型）。服务端语义：首次执行后存储 `(key, 请求摘要, 响应)`，TTL ≥ 24h（存储用 Redis `SET NX PX`，见 [../infra/redis.md](../infra/redis.md)）；同键同参 → 回放原响应；同键异参 → `1007`；并发同键 → 竞争失败方等待短暂后回放或报 `1007`
2. **内部幂等（默认假设）**：消息消费（至少一次语义）、定时任务（可能重复触发）、回调重放——重复必然发生，处理逻辑必须幂等；兜底手段按序：业务状态机前置校验 → 分布式锁 → 数据库唯一约束（与 [../infra/xxl-job.md](../infra/xxl-job.md) 二-1 同源）
3. 网络层重试不得成为重复执行的来源：副作用操作以存储层唯一约束为最终防线

## 数据表示

- 时间：ISO-8601 UTC（`2026-08-31T12:00:00Z`），不用时间戳数字（**D4 已拍板：业界标准**，infra 六份规约引用一致，无翻盘风险）
- 金额：字符串或整型分值，不用浮点
- ID：不透明 string
- 枚举：小写字符串（`"pending"`），不用数字
- 空集合：`[]`，不用 `null`
