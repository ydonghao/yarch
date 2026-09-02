# 契约 · RestResponse 形状（v1.0 已定稿）

> **状态：已定稿**（2026-09-01 评审通过；D1-D6 已拍板，口径为"业界标准优先于阿里手册"，结论见 [../../docs/references/alibaba-java-manual-digest.md](../../docs/references/alibaba-java-manual-digest.md) D 表）。

所有 HTTP API 的响应体统一为四字段。字段名 **camelCase**，任何栈不得增删改名。

```json
{
  "code": 0,
  "message": "成功",
  "data": { "any": "thing" },
  "traceId": "0af7651916cd43dd8448eb211c80319c"
}
```

| 字段 | 类型 | 必填 | 语义 |
|---|---|---|---|
| `code` | int32 | 是 | 业务码。`0` = 成功；非 0 见 [error-codes.md](error-codes.md) |
| `message` | string | 是 | 人类可读文案，可直接作为前端降级展示文案；**不得**携带堆栈/内部细节 |
| `data` | T \| null | 是 | 业务负载。`code != 0` 时必须为 `null`；成功且无负载时为 `null` |
| `traceId` | string | 是 | 本次请求的追踪 ID，恒等于响应头 `X-Trace-Id`；取不到时为空串 |

## 与 HTTP 状态码的关系

HTTP 状态码只表达**传输层/网关语义**，业务语义只看 `body.code`。两者由错误码表统一映射（每码固定），服务端不得自行发挥：

```http
HTTP/1.1 400 Bad Request
X-Trace-Id: 0af7651916cd43dd8448eb211c80319c
Content-Type: application/json

{"code":1001,"message":"参数校验失败：pageSize 必须 ≤ 100","data":null,"traceId":"0af7651916cd43dd8448eb211c80319c"}
```

- 成功：`200/201` + `code=0`
- 业务可预期失败：`4xx` + 对应业务码
- 未预期失败：`500` + `code=1000`（message 固定为「内部错误」，细节只进日志）

## 分页负载形状

`data` 为分页结果时（约定见 [rest-conventions.md](rest-conventions.md)）：

```json
{
  "code": 0,
  "message": "成功",
  "data": { "list": [ /* T */ ], "total": 123, "page": 1, "pageSize": 20 },
  "traceId": "0af7651916cd43dd8448eb211c80319c"
}
```

| 字段 | 类型 | 语义 |
|---|---|---|
| `list` | T[] | 当前页数据，可为空数组，不得为 null |
| `total` | int64 | 过滤后总数 |
| `page` | int32 | 1-based 页码 |
| `pageSize` | int32 | 每页条数 |
| `nextCursor` | string? | 可选游标（游标分页通道，见 [rest-conventions.md](rest-conventions.md)）；缺失或空串表示没有下一页 |

## 网关故障面

后端不可达、网关熔断/过载时由**网关层**发出的故障响应，同样必须是本信封：`code` 用 1008（上游超时）/ 1009（暂不可用），`traceId` 由网关生成或透传，并回显 `X-Trace-Id`——保证前端解包逻辑在故障路径上不失效。网关进程级宕机（连信封都无法产出）由前端以传输层错误兜底（ApiError code = -1）。

## 本地化立场（i18n）

v1 的 `message` 默认文案为中文；本地化（按 `Accept-Language` 服务端解析，或 code 驱动前端翻译）列为 v2 候选议题。**D2 已拍板：不引入 `userTip`**（排障信息与用户提示分离的阿里方案不采用）——单一 `message` + "默认文案：细节"规则承载，业界主流（Stripe/GitHub/Google）同为单 message 形态。信封不为此预留额外字段，机制演进不得破坏四字段形状。

## 序列化规则

- JSON 字段顺序建议 `code, message, data, traceId`（利于人读 diff，非强约束）
- 时间一律 ISO-8601 UTC：`2026-08-31T12:00:00Z`
- 金额用字符串或整型分值，**不得**用浮点
- ID 一律不透明 string（雪花/UUID 由服务端定）
