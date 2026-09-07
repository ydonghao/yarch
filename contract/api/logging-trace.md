# 契约 · 日志与追踪（v1.0 已定稿）

> **状态：已定稿**（2026-09-01 评审通过；D1-D6 已拍板，口径为"业界标准优先于阿里手册"，结论见 [../../docs/references/alibaba-java-manual-digest.md](../../docs/references/alibaba-java-manual-digest.md) D 表）。

目标：跨栈排障一条链拉通——任意一行日志都能用 `traceId` 串起一次请求的全部足迹。

## 日志行协议（ndjson，一行一条）

标准输出，UTF-8，JSON Lines：

```json
{"ts":"2026-08-31T12:00:00.123Z","level":"INFO","service":"ysaas","env":"prod","traceId":"0af7651916cd43dd8448eb211c80319c","logger":"io.github.ydonghao.yarch.framework.web.TraceIdFilter","msg":"request completed","method":"GET","path":"/api/v1/users","status":200,"costMs":12}
```

| 字段 | 类型 | 必填 | 语义 |
|---|---|---|---|
| `ts` | string | 是 | RFC3339 毫秒精度 UTC，恒以 `Z` 结尾 |
| `level` | string | 是 | `TRACE` / `DEBUG` / `INFO` / `WARN` / `ERROR`，大写 |
| `service` | string | 是 | 服务名（进程级，来自配置） |
| `env` | string | 是 | `local` / `dev` / `staging` / `prod` |
| `traceId` | string | 条件 | 请求上下文内必有；后台任务/启动日志可为空 |
| `logger` | string | 是 | 记录点标识（Java 类名 / Go 包名 / Python logger 名 / TS 模块名） |
| `msg` | string | 是 | 事件描述 |
| 其余 | any | 否 | 自由键值对，camelCase |

禁止：多行堆栈直接打印（折叠为 `stack` 单字段）、本地时区、非 JSON 前缀。

## traceId 贯穿（OpenTelemetry 口径）

1. **入口**：服务端优先解析请求头 `traceparent`（W3C，`00-{traceId}-{spanId}-{flag}`，取 trace-id 段）；无则看 `X-Trace-Id`；再无则自己生成 32 位小写 hex；
2. **传播**：处理过程中该值进入各栈的上下文载体（Java MDC / Go ctx 或中间件键 / Python contextvars / TS 由服务端管理，客户端只透传）；
3. **出口**：
   - 响应头 `X-Trace-Id` 恒回显；
   - 响应体 `RestResponse.traceId` 恒等于它；
   - 调用下游（HTTP/RPC）时携带 `traceparent`，无 span 语义的客户端至少带 `X-Trace-Id`；
4. **生成**：128-bit 随机数的 32 位小写 hex；全链路不改变、不重新生成（除入口为空时）。

## 跨进程传播矩阵

traceId 跨越一切边界时的注入/继承规则（各 infra 规约引用本表为唯一口径）：

| 边界 | 注入方与位置 | 接收方行为 |
|---|---|---|
| HTTP / RPC 同步调用 | 客户端注入请求头 `traceparent`（优先）；无法构造 span 语义的客户端至少带 `X-Trace-Id` | 服务端按"入口"规则解析；响应头 `X-Trace-Id` 回显 |
| 消息队列（produce） | 生产者注入消息用户属性：`traceId`（Kafka header / RocketMQ 用户属性） | 消息体与 header 同批写入，禁分离发送 |
| 消息队列（consume） | — | **继承消息内 traceId**（同一业务链路，不新建）；消费处理日志携带它；重试与 DLQ 投递保留原 traceId |
| 定时任务（XXL-Job 等） | 执行器每轮触发生成**新** traceId（时间驱动的新链路） | 任务日志、子调用、产出消息全部携带；人工补跑同样新建并记录操作人 |
| 任务产出消息（调度→MQ） | 产出消息继承当轮任务的 traceId | 同"消息队列（consume）" |
| 网关 / 反向代理（入口） | 透传上游 `traceparent`/`X-Trace-Id`；缺省生成（Nginx `$request_id` 等） | 转发给上游；响应回显 `X-Trace-Id` |
| 网关故障响应（fallback） | 网关自身生成/透传 | **故障响应也必须携带 `X-Trace-Id`**（见 [rest-response.md](rest-response.md) 网关故障面） |
| 前端（web） | 请求带 `X-Trace-Id`；错误对象暴露响应的 `traceId` | 用户报障以 traceId 为凭证 |

铁律：traceId 只在"链路入口无值"时生成一次；消息消费、任务执行是**新入口**（上游 trace 已在消息/任务参数内，继承而非再生成）。

## 各栈实现锚点

| 栈 | 上下文载体 | 输出器 |
|---|---|---|
| java | MDC key `traceId`（`TraceIdFilter` 注入） | logstash-logback-encoder（`yarch-logging-spring-boot-starter`） |
| golang | hertz 中间件键 + slog attr | `log/slog` JSONHandler（`logx`） |
| python | FastAPI middleware 写入 contextvars | structlog ndjson 行协议（`logx`） |
| web | 不持有上下文，仅生成/透传 `X-Trace-Id` 并在错误对象暴露 `traceId` | 浏览器控制台结构化 `console` 由业务决定 |
