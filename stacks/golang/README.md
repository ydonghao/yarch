# stacks/golang · yarch-go

> yarch Go 栈平台构件（单 module `github.com/yuandonghao/yarch-go`，go 1.24 基线；发 Go module proxy，业务工程 `go get` 引入）。
> 契约唯一权威来源：[../contract/](../contract/README.md)——实现与契约不一致即 bug。
> 策划与拍板记录：[PLAN.md](PLAN.md)（G1-G9 已拍板，2026-09-02）。
> 架构图：[architecture-diagram.svg](architecture-diagram.svg)（六层 + Hertz 请求生命周期）。
> 技术叙事：结构同构 coze-studio、实践反向沉淀 yagent、语义按 contract/ 重铸。

## 构件（一规约一 package，对偶 java 一规约一 starter）

```
stacks/golang/                        # module github.com/yuandonghao/yarch-go（go 1.24）
├── response/                         # 契约内核①：Response[T] / PageData[T] / PageQuery（纯 stdlib）
├── errcode/                          # 契约内核②：Code + 13 码全表 + HTTP 映射 + 业务码注册
├── xerror/                           # 契约内核③：BizError（message「默认文案：细节」规则）
├── logx/                             # slog ndjson 行协议（ts/level/service/env/traceId/logger/msg）
├── middleware/                       # Trace(traceparent 优先) / Recovery→1000 / AccessLog / Idempotency→1007 / RateLimit→1006
├── web/                              # Setup(h) 一行装配 + Bind(1001/1002) + BindPageQuery(D6) + 信封回包
├── persist/                          # GORM(pgx) + 逻辑删除 is_deleted + 审计 + PageOf 分页下推 + golang-migrate
├── redix/                            # Keys(首段=服务名) + JSON Cache + Lock + Idempotency(SET NX PX)
├── httpx/                            # 下游客户端：超时强制(≤30s) + traceparent 注入 + 信封解包 + 1008/1009
├── testx/                            # 契约断言（信封/ndjson/PageData），零三方依赖
├── testcontainers/                   # 子 module：PG/Redis 容器基座（testcontainers-go，测试态专用）
└── template/                         # yarch-go-template：DDD 七包 + users 示例（gonew 生成）
```

依赖方向：契约内核三包零框架依赖；构件间禁止反向依赖（CI golangci-lint depguard 机检模板侧同步）。

## 快速开始

```bash
# 1. 生成业务工程（服务名须过 registry.md 一-1 校验）
go run golang.org/x/tools/cmd/gonew@latest \
  github.com/yuandonghao/yarch-go/template github.com/yourname/your-svc ~/code/your-svc

# 2. 生成工程一键跑通（迁移自动执行）
cd your-svc && docker compose up -d && make run

# 3. 平台件升级 = go get 升版（发版式升级）
go get github.com/yuandonghao/yarch-go@vX.Y.Z
```

## 验收（随 CI 双矩阵 go 1.26/1.27）

- 契约断言：13 码全表（code/标识/文案/HTTP 映射）+ 信封形状逐字节 + ndjson 字段级 + message 追加规则；
- 中间件链：traceId 三级入口（traceparent→X-Trace-Id→生成）+ 回显 + panic→1000 + 幂等同键回放/异参 1007 + 限流 1006；
- web：Bind 语法错→1002 / 校验→1001、分页默认 20 上限 100、越界 D6 空页；
- 行为级（testcontainers 真 PG/Redis）：分页下推 + D6、逻辑删除不物理删、审计填充、key 首段=服务名、JSON 值、锁互斥/token 释放、幂等三段流转；
- httpx：traceparent 注入（trace-id 继承 ctx）、下游业务码透传、超时→1008、连接失败/非信封→1009、超时强制截断。

## 本机开发备注（macOS + OrbStack）

- 测试运行须 `CGO_ENABLED=0`（testcontainers 依赖链中 go-m1cpu v0.1.6 在 darwin/arm64 有 cgo init SIGSEGV，java 侧 TC 2.x 探测缺陷同源教训）；CI Linux 无此问题，但统一禁用最稳；
- 版本纪律：依赖钉 coze-studio/yagent 验证组合（hertz v0.10.2 / go-redis v9.7.0 / GORM v1.25.12 / x/time v0.11.0 / otel v1.35 线），`go mod tidy` 后若 go 指令被抬高到 1.25+，用 `go mod edit -go=1.24` 收口（G8：基线 1.24 = coze-studio 同款）。
