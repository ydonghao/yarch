# Higress 开发规约（v1.0 已定稿）

> **状态：已定稿**（2026-09-01 评审通过；条文已对照 Higress 官方文档逐条校准，见文末校准记录）。供人阅读、供 AI 作为规范上下文使用。
> 等级：**【强制】**违反即缺陷；**【推荐】**默认遵守、评审后可豁免；**【参考】**正向引导。适用 Higress 2.x（云原生网关，Envoy 内核）。分工与来源见文末附录。

## 一、定位与部署

1. 【强制】Higress 定位：南北向 API 网关——路由编排、认证鉴权、限流熔断、WASM 插件、AI 网关（LLM 代理）；静态资源与简单前置代理归 Nginx（见 [nginx.md](nginx.md) 一-1 分工）。
2. 【强制】生产以 Kubernetes（或 Higress All-in-One 标准形态）声明式部署；配置形态为 Ingress（注解）+ WasmPlugin 等 CRD + 控制台三入口（插件市场/路由策略/域名级），**声明式资源（Ingress/WasmPlugin/控制台配置导出）一律进 git**，禁只存控制台的漂移配置。
3. 【强制】网关高可用：多副本入口 + 前置 LB；控制面与数据面分离部署，数据面资源独立评估（CPU/连接数）。
4. 【强制】共享一套网关的多项目：以域名 + 路由前缀（`/{服务名}` 或独立域名）隔离，路由清单登记——机制同 Kafka topic 登记。

## 二、路由规约

1. 【强制】路由命名 `{服务名}-{业务域}`（Ingress 注解与控制台路由命名统一），与后端服务名（Nacos 注册名）一一对应，禁网关侧私造别名。
2. 【强制】路径规范对齐 [../api/rest-conventions.md](../api/rest-conventions.md)：`/api/v1/...` 起步、kebab-case；重写（rewrite）只做前缀剥离，禁在网关做深度 URI 改写。
3. 【强制】路由变更走声明式资源变更（git + CI 应用），控制台只读排障优先；变更评审等同发布。
4. 【推荐】灰度路由（按 header/百分比）用于发布验证，验证后即回收，禁长期并存双路由无下线计划。

## 三、安全与认证

1. 【强制】对外 API 默认需要认证：基于 **Consumer（调用方）机制**配置认证插件（key-auth / jwt-auth / basic-auth / hmac-auth），Consumer 凭证与路由策略绑定；匿名路由必须白名单登记（登录、健康检查、回调——jwt-auth 等支持免认证白名单）并评审。
2. 【强制】CORS 白名单显式配置（origin 精确匹配），禁 `*` 出现在带凭证的 CORS。
3. 【强制】网关层 TLS 终结（证书管理与 Nginx 同等纪律：到期监控 + ACME）；内部东西向按需 mTLS。
4. 【强制】消费者（consumer）凭证映射到服务/应用身份，凭证轮换有流程；凭证禁出现在日志。
5. 【强制】上游超时与重试在网关显式配置：超时对齐业务 P99（与 nginx.md 三-1 同源）；重试仅幂等方法（GET/带幂等键），禁默认无限重试。

## 四、流量治理

1. 【强制】核心路由必须配置限流（QPS/并发，按 consumer、IP 或自定义 key 维度）：单机用 `key-rate-limit`、集群统一口径用 `cluster-key-rate-limit`（基于 Redis）；阈值登记进业务仓——达到限流返回 429 + 错误码 1006 语义（对齐 [../api/error-codes.md](../api/error-codes.md)）。
2. 【强制】熔断与降级策略对弱依赖开启；降级响应保持 RestResponse 信封（网关自身错误页也用统一 JSON 信封）。
3. 【强制】**网关故障面信封**：后端不可达、熔断、过载时网关发出的故障响应（504/503）必须是 RestResponse 信封——`code` 用 1008（上游超时）/ 1009（暂不可用），`traceId` 生成或透传并回显 `X-Trace-Id`（口径见 [../api/rest-response.md](../api/rest-response.md)「网关故障面」与 [../api/logging-trace.md](../api/logging-trace.md) 传播矩阵）。网关进程级宕机不由网关保证，前端以传输层错误兜底。
4. 【推荐】按路由分组治理（鉴权/限流/缓存策略复用），禁逐路由复制配置。
5. 【参考】AI 网关场景（LLM 代理）：ai-proxy 等 AI 插件 + `ai-token-ratelimit` 按模型/租户 token 配额限流，API key 脱敏入日志。

## 五、插件与扩展

1. 【强制】WASM 插件版本化管理，插件配置变更走评审；禁在生产路由热挂未验证插件。插件配置四级生效（**路由级 > 域名级 > 服务级 > 全局**），策略归属层级必须在登记时写明，防全局默认被静默覆盖。
2. 【强制】插件与日志不得记录请求体全文与凭证；记录字段对齐日志契约（path/status/costMs/consumer/traceId）。
3. 【强制】traceId 全链路透传：网关入口透传/生成 W3C traceparent（对齐 [../api/logging-trace.md](../api/logging-trace.md)），响应回显 X-Trace-Id。
4. 【推荐】优先使用官方插件满足需求，自研插件（WASM/Rust）代码进仓受评审。

## 六、运维基线

1. 【强制】监控告警：路由 5xx 比例、P99 延迟、429/401 计数、上游健康状态、连接与 CPU 水位。
2. 【强制】配置与路由清单定期导出备份（声明式仓即备份）；网关版本升级走灰度数据面节点。
3. 【推荐】控制台只读权限分级；变更审计开启。

---

## 附：分工与来源

- **分工决策（可推翻）**：Higress=API 网关与治理（含 AI 网关），Nginx=入口/静态/简单代理；重叠场景默认见各自一-1。
- 官方依据：[插件使用指南（三入口与层级）](https://higress.ai/docs/latest/user/plugins/intro/)、[key-auth](https://higress.ai/docs/latest/plugins/authentication/key-auth/) / [jwt-auth](https://higress.ai/docs/latest/plugins/authentication/jwt-auth/)（Consumer 机制）、[key-rate-limit](https://higress.ai/docs/latest/user/plugins/traffic/key-rate-limit/) / [cluster-key-rate-limit](https://higress.ai/docs/latest/user/plugins/traffic/cluster-key-rate-limit/)、[WasmPlugin 配置优先级](https://higress.ai/docs/latest/user/plugins/wasm-dev/wasm14/)。
- 其余条文（路由命名、故障面信封、traceId 透传、声明式纳管）与 yarch 既有契约（error-codes 1006、logging-trace 传播矩阵、rest-conventions）对齐推导。
- 校准记录（2026-09-01）：Consumer 认证模型与四认证插件、`key-rate-limit`（单机）/`cluster-key-rate-limit`（集群，基于 Redis）、`ai-token-ratelimit`、插件配置三入口与四级优先级（路由 > 域名 > 服务 > 全局）已逐条对照官网 higress.ai/docs 核实；本文件为架构评审 P2 遗留项的收尾。
