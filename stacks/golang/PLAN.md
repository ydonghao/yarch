# stacks/golang · yarch-go 脚手架策划案

> **状态：第一批施工完成（2026-09-02），G1-G9 已拍板**（用户审阅架构图后开工，按推荐值落地）。
> **验收记录（2026-09-02，本机 go 1.26.7 + OrbStack）**：三 module 全绿（主 8 包 + testcontainers 子 module PG/Redis 行为级 + template 冒烟）——13 码全表 / 信封逐字节 / ndjson 字段级 / traceId 三级入口与回显 / panic→1000 / 幂等回放与 1007 / 限流 1006 / Bind 1001·1002 / 分页 D6（真 PG）/ 逻辑删除 / 锁 token 语义 / httpx 出口传播与 1008·1009。
> 架构图：[architecture-diagram.svg](architecture-diagram.svg)（六层 + Hertz 请求生命周期）。
> 输入：[contract/](../../contract/README.md) 24 份定稿规约（唯一权威）· [docs/architecture.md](../../docs/architecture.md)（Hertz+DDD 既定）· coze-studio 调研（2026-09-02，main 分支核实）· yagent 既有实践（反向沉淀源）。
> 注：本文件 G1-G9 指 golang 栈决策编号；与 postgresql.md 的 ORM 条文 G1-G10、ruoyi-yudao-coli-gap-digest.md 的对标缺口 G1-G10 无关。
>
> **实施偏差登记**：容器基座拆为子 module `testcontainers/`（主 module 保持 go 1.24 轻依赖——testcontainers 的 otel v1.46 依赖链强制 go 1.25，拆分后双 module 均收口 1.24）；测试运行须 CGO_ENABLED=0（go-m1cpu v0.1.6 darwin/arm64 cgo 崩溃）。

## 一、定位与既定约束（来自 architecture.md 与 contract，本策划不再议）

1. yarch-go = Go 栈平台构件（单 module 多 package），经 Go module proxy 分发；业务工程 `go get` 引入，版本步进自动跟进。
2. module path：`github.com/yuandonghao/yarch-go`（开发期在本仓 stacks/golang/ 下，go.mod `go 1.24` 基线；发布时独立仓库或本仓子目录 tag `/stacks/golang/vX.Y.Z` 均可）。
3. 铁律：不含任何业务语义；实现与 contract/ 不一致即 bug。
4. 术语对照（契约已锁定，包名必须命中）：`response.Response` / `response.PageData` / `errcode.Code` / `xerror.BizError` / `middleware.Trace()` / `logx`（`log/slog` JSONHandler）。
5. 分层：coze-studio 同构 DDD 七包（java J1 已预留「golang 栈直接同构对照」）；`infrastructure(contract/impl)` port/adapter 分离、`crossdomain` 域间防腐为 coze-studio 吸收项。

## 二、与 coze-studio 的对标结论（2026-09-02 调研）

| 处置 | 内容 | 说明 |
|---|---|---|
| **直接吸收** | DDD 七包：api / application / domain / crossdomain / infrastructure / types（+cmd）；域间禁止直接依赖，跨域走 crossdomain | yagent 已同构在用；java J1-C 同源 |
| | `infrastructure(contract/impl)` port/adapter：外部依赖接口契约与实现分离 | 多实现可插拔（coze 的 OSS 三适配/eventbus 五适配得益于此） |
| | 手写组装根 `application.Init` 分阶段（basic→primary→complex） | 显式可读；coze 公认短板是组装根膨胀，以「阶段化+每阶段小函数」缓解 |
| | 单 module、modular monolith 部署形态 | 与 G1/G5 一致 |
| | Hertz + GORM + godotenv 技术底座 | yagent 同款，反向沉淀零重写成本 |
| **明确不采用** | hz + Thrift IDL-first 代码生成 | G9：第一批手写 handler；REST 契约以 OpenAPI 为出口；触发式再议 |
| | atlas/HCL 声明式迁移 | G3：用 golang-migrate 纯 SQL 版本化（PG 规约六-1 + Flyway 对偶） |
| | 9 位分段错误码 + `WithAffectStability` | 契约已定 int32 四位段（0/1xxx/2xxx），实现不得分叉 |
| | `SetDefaultSVC` 包级全局单例 + mockey/patch 打桩 | 模板改为显式注入；测试隔离不依赖打桩 |
| | OTel 依赖进而不接线、排障靠 x-log-id grep | yarch 契约 traceparent 全链路 + ndjson 字段级断言，正好补此坑 |
| **yarch 增量** | 统一信封 / 错误码 13 码全表 / traceId 贯穿 / 幂等 / 限流——契约可执行化 | coze-studio 无跨栈统一契约层 |

## 三、目标形态（module 树）

```
stacks/golang/                      # module github.com/yuandonghao/yarch-go（go 1.24）
├── response/                       # 契约内核①：Response[T] / PageData[T] / 构造与序列化（纯 stdlib）
├── errcode/                        # 契约内核②：Code 类型 + 13 码全表 + HTTP 映射 + 业务码注册
├── xerror/                         # 契约内核③：BizError（code + message[+细节]）
├── logx/                           # slog ndjson 行协议（ts/level/service/env/traceId/logger/msg）+ Ctx 取值
├── middleware/                     # Trace（traceparent 优先）/ Recovery→1000 / AccessLog / Idempotency→1007
├── web/                            # Setup(h) 装配 + 信封回包 + BindError→1001/1002 + 分页绑定（默认 20 上限 100）
├── persist/                        # GORM(pgx)：逻辑删除 is_deleted / 审计填充 / 分页桥（D6 空页天然成立）/ migrate 辅助
├── redix/                          # Keys（首段=服务名强制）/ JSON codec / 分布式锁 / 幂等存储（SET NX PX ≥24h）
├── httpx/                          # 下游客户端：超时强制 / traceparent 注入 / 信封解包 / 1008·1009 转译
├── testx/                          # 契约断言（13 码表 / 信封形状 / ndjson 字段级）+ testcontainers PG·Redis 基座
└── template/                       # yarch-go-template（DDD 七包 + users 示例 CRUD + migrate + compose + Makefile）
```

依赖方向：`response/errcode/xerror` 零框架依赖；`middleware`→`logx/redix/xerror`；`web`→`response/errcode/xerror`；`persist`→`response`；`redix` 独立；`httpx`→`response/errcode/xerror`；`testx`→全部（仅测试态）。构件间禁止反向依赖。

## 四、契约 → 构件映射

| 契约条文（出处） | 落点 | 机检 / 验收 |
|---|---|---|
| RestResponse 四字段 camelCase、code!=0 data=null、traceId=X-Trace-Id（rest-response.md） | response | JSON 序列化断言（字段名/必填/形状） |
| 13 码全表 + 默认文案 + HTTP 映射 + 标识符驼峰（error-codes.md） | errcode | 全量断言（code/标识/message/HTTP），防漂移 |
| message「默认文案：细节」追加规则（error-codes.md 实现规则-1） | xerror | 断言 |
| ndjson 行协议字段（logging-trace.md） | logx | 捕获输出逐行断言 |
| traceId 入口（traceparent→X-Trace-Id→生成）/ 回显 / ctx 贯穿 | middleware.Trace | httptest 集成断言 |
| 校验失败→1001/400、请求体格式→1002/400（error-codes + rest-conventions） | web | 集成测试 |
| 分页默认 20 上限 100、越界空页 D6、PageData{list,total,page,pageSize,nextCursor?}（rest-conventions） | web + persist | PG 集成：越界返回空 list + 真实 total |
| 幂等 Idempotency-Key：同键回放/异参 1007/TTL≥24h（rest-conventions 幂等总则-1） | middleware.Idempotency + redix | Redis 集成测试 |
| PG 逻辑删除 is_deleted / 审计填充 / 迁移版本化（postgresql.md 五·六） | persist + template db/migration | Testcontainers PG 行为级测试 |
| Redis key 首段=服务名 / JSON 值（redis.md） | redix.Keys | 断言 key 形状 |
| 出口传播：traceparent 注入 + 超时强制 + 信封解包 + 1008/1009（logging-trace 传播矩阵 + A7） | httpx | httptest 下游桩集成 |
| 状态码白名单 200/201/400/401/403/404/409/429/500/503/504（rest-conventions） | web | 断言 |

## 五、决策清单（已拍板记录）

| # | 决策 | 拍板结论（2026-09-02，按推荐） |
|---|---|---|
| G1 | 工程形态 | **单 module 多 package**（coze-studio 同款；Go 细粒度在 import path，未 import 的包不进构建图；版本统一步进） |
| G2 | 构件切分 | **一规约一 package**：response/errcode/xerror（契约内核组，即架构图「contract」卡）/ logx / middleware / web / persist / redix / httpx / testx |
| G3 | ORM 与迁移 | **GORM v1.25+ + pgx 驱动**；迁移 **golang-migrate**（纯 SQL 版本化进仓）；不用 GORM AutoMigrate（违反 PG 规约六-1），不采用 coze 的 atlas/HCL |
| G4 | 配置管理 | **godotenv + .env 三环境**（local/debug/release，coze-studio 与 yagent 双同款）；koanf/viper 触发式 |
| G5 | 依赖组装 | **手写组装根** application.Init 分阶段（显式可读）；wire 编译期校验为触发式增强；不用 fx |
| G6 | 模板分发 | **单 ddd 档起步**（simple 档按触发）；template/ 模板仓 + `gonew`/`cp` 指引，`yarch init` CLI 后续包一层 |
| G7 | 机检第一批 | 契约断言 go test（13 码全表/信封/ndjson 字段级）+ golangci-lint（depguard 依赖方向：domain 零框架依赖、api 不直依赖 infra）+ testcontainers-go + GitHub Actions |
| G8 | 版本基线 | **go.mod `go 1.24`**（coze-studio 同款、yagent 兼容、slog 足用）+ 工具链/CI **1.26/1.27 双矩阵**（对偶 java 21/25：基线取采用面最大，工具链取最新） |
| G9 | IDL/代码生成 | **不引入** hz+Thrift IDL-first；手写 handler；触发式再议 |

## 六、分批施工清单

**第一批（2026-09-02 完成，commit d92dd72）**：module 骨架 + 契约内核三包 + logx + middleware + web + persist + redix + httpx + testx + template（DDD 七包 users 示例）+ README + CI。

**第二批 = 精细打磨批（2026-09-02 完成，对偶 java 路线图 2.0）**：
- `auth`：JWT 机制件（HS256 默认，密钥 ≥32B；RS/ES 扩展位）+ RequireAuth/RequireRoles 中间件 + 2xxx 全矩阵（无凭证/坏签名→2001、过期→2002、权限不足→2003；2004 账号禁用归业务钩子）；
- `captcha`：图形验证码（basicfont 渲染 PNG + 去混淆字符集 + dataURL）+ Redis 一次性 token（GETDEL 原子消费，失败同样消费防重放）+ `GET /api/v1/captcha` 端点件；
- 分布式限流档：`redix.RateLimiter`（固定窗口 Lua 原子计数）+ `middleware.RateLimitRedis`（跨实例口径，进程内档保留）；
- `middleware.OperationLog`：操作日志（ndjson 行 + 存储 SPI 异步投递，失败不阻断）。

**触发式增长（一规约一 package，不做配置档）**：mysql 档（`persist/mysql`）、kafka、rocketmq、xxl-job、nacos、minio、es、qdrant、wire 组装校验、`yarch init`。

## 七、开放问题与风险

1. Hertz 中间件修改请求 ctx：用 `c.SetCtx` 注入 context value，同时 `c.Set` 双写（handler 两种取法都命中）——已验证 API 存在于 v0.10.x。
2. testcontainers-go 版本锁定 0.3x（macOS OrbStack 探测与 java 侧 TC 1.21 同源经验）；本机 Docker Desktop socket 软链已就绪。
3. 发布：0.x 不承诺兼容；正式发 module tag 待第一批全绿后。
