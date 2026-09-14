# stacks/rust 云栈开发规约（v1.0 已定稿）

> **状态：已定稿**（2026-09-14 经 R1-R7 拍板口径成文）。
> 等级：**【强制】**违反即缺陷；**【推荐】**默认遵守、评审后可豁免；**【参考】**正向引导。
> 前置依赖（硬）：[../../contract/api/](../../contract/README.md) 四件套（信封/错误码/日志/REST 约定）——本文件只写 Rust + axum 方言条文，契约语义以四件套为准。
> 框架基线：axum 0.8 + tokio 1.x + sqlx 0.8（R1 拍板）。调研见 [rust-stack-digest.md](../../docs/references/rust-stack-digest.md)。
> 约束对象：yarch 体系全部 Rust 云端业务服务工程。与 `embedded/esp32` 固件轨零共享 crate（运行时模型不同）。

## 一、语言与工具链

1. 【强制】Rust 是唯一开发语言；MSRV 1.80+（R7，axum 0.8 要求），版本在 workspace 根 `rust-toolchain.toml` 钉死，CI 矩阵 stable + MSRV 双档。
2. 【强制】风格由 rustfmt 承接（`cargo fmt --all -- --check` 进 CI）；lint 由 clippy 承接（`cargo clippy --all-targets -- -D warnings` 进 CI）——**deny warnings 即门禁**。
3. 【强制】依赖治理由 cargo-deny 承接：许可证白名单（MIT/Apache-2.0/BSD 系）、禁重复大版本依赖、安全咨询（RUSTSEC）——`deny.toml` 入仓进 CI。

## 二、架构（DDD 七包，R2）

1. 【强制】分层定式（与 java/golang/python 逐目录同构）：

```
src/
├── api/           # 入口层：axum router/handler、DTO、请求校验——只做协议转换
├── application/   # 应用层：用例编排（一用例一函数/服务），事务边界在此
├── domain/        # 领域层：实体/值对象/领域服务/仓储端口（trait）——零框架零基础设施依赖
├── crossdomain/   # 防腐层：域间交互端口与适配
├── infra/         # 基础设施：仓储实现（sqlx）、缓存、消息、外部服务
├── types/         # 共享类型（标识/枚举/值对象）
└── errors/        # 错误类型（thiserror 派生，对齐 contract 错误码段位）
```

2. 【强制】依赖方向单向：api → application → domain ← infra（端口在 domain、实现在 infra，依赖倒置）；**禁反向依赖**（机检见九）。
3. 【强制】domain 层零框架依赖（不 import axum/sqlx/tokio）——可测性与契约内核复用前提。
4. 【强制】业务错误用 thiserror 派生枚举（按域一个枚举），透传 `yarch-contract` 错误码；**禁 anyhow 穿出 application 层**（anyhow 仅限组装根与 main）。

## 三、契约落地（四件套方言）

1. 【强制】响应一律 `RestResponse<T>` 信封（`yarch_contract::response`）：code/message/data/traceId 四字段，`code == 0` 成功；判错只看 code，HTTP 状态码不承载业务语义。
2. 【强制】错误码只用 `yarch_contract::errcode` 常量表（13 码全表断言进契约测试）；业务码 3xxx+ 段位在业务仓登记。
3. 【强制】traceId 贯穿：入口中间件（`yarch_axum::middleware::trace`）解析/生成 32 hex，注入 request extensions 与 tracing span；出口经 `yarch-axum` 的 HTTP 客户端封装注入下游。
4. 【强制】分页双通道：页码通道（D6 越界返回空页：200 + 空 list + 真实 total）+ keyset 游标通道（`nextCursor` 空串即无下一页）。
5. 【强制】幂等：`Idempotency-Key` 头 + Redis `SET NX PX`（对齐 golang/python 同语义）；写操作（POST/PUT/PATCH）默认走幂等中间件。
6. 【强制】日志 ndjson 一行一条（`ts/level/service/env/traceId/logger/msg`），tracing-subscriber JSON 格式化器承接；**禁 println!/eprintln! 进业务代码**。

## 四、工程结构与生成

1. 【强制】工程由 `cargo generate` 一行命令生成（R4；模板 `stacks/rust/templates/service/`）；`cp -r` 既有工程当模板不可接受。
2. 【强制】workspace 两 crate 分工：`yarch-contract` 零框架（信封/错误码/trace/分页语义），`yarch-axum` 装配（中间件/提取器/分页/幂等）；业务工程依赖两 crate 的版本化发布（crates.io）。
3. 【强制】配置三层：`.env`（本地）/ 环境变量（部署）/ 默认值（代码），经 config-rs 或 figment 统一加载；**禁散落 `std::env::var` 直读**。
4. 【强制】数据库迁移版本化（sqlx-cli migrate 或 refinery），**禁 runtime create_all / 自动建表**（对齐 python alembic 禁 create_all 口径）。

## 五、数据与中间件（infra 规约的 rust 方言）

1. 【强制】PG 经 sqlx（`contract/infra/postgresql.md` 生效时）：编译期 SQL 校验（query! 宏），CI 用 `.sqlx/` 离线快照；连接池单点配置（超时/上限）。
2. 【强制】逻辑删除与审计填充在 sqlx 仓储层统一（deleted_at 过滤默认开启），对齐四栈口径。
3. 【强制】Redis 经 `yarch-axum` 封装（key 前缀=服务名，对齐 redis.md）；幂等/分布式锁/限流三态复用。
4. 【推荐】Kafka/RocketMQ 接入按 infra 规约触发式引入，不预置进模板。

## 六、并发与异步

1. 【强制】异步一律 async/await + tokio；**禁在 async 上下文里做阻塞调用**（阻塞 IO 走 `tokio::task::spawn_blocking`）。
2. 【强制】中间件组合语义对齐 python 终审口径：装配顺序 AccessLog > Trace > Recovery > Idempotency > Rate（Rate 在 Idem 外层，429 不被幂等层捕获落库）。
3. 【强制】共享状态用 `Arc` + 显式锁（`tokio::sync` 系列）；**禁 `Rc`/`RefCell` 跨 await**。

## 七、测试

1. 【强制】契约断言测试：`yarch-contract` 自带 13 码全表 + 信封形状 + ndjson 字段断言（对齐 java ContractAsserts / python testx 同表）；业务工程跑同一套 conformance。
2. 【强制】domain 层单测零 IO（mock 仓储 trait）；application 层用例测试经内存仓储；api 层集成测试经 axum `oneshot`。
3. 【强制】中间件组合语义回归测试（对齐 python `test_middleware_composition.py` 四例：Rate 外层/异常释放/5xx 释放/重试回放）。
4. 【推荐】PG 集成测试用 testcontainers-rs（对齐四栈 TC 口径）。

## 八、发布与发版

1. 【强制】两 crate 发 crates.io：`yarch-contract` → `yarch-axum` 顺序发布（幂等）；tag `stacks/rust/vX.Y.Z` 走 `rust-publish.yml`（R5，对齐四栈发版链路）。
2. 【强制】crate 元数据齐全：license（Apache-2.0）/ repository / description / keywords / MSRV（`rust-version` 字段）。
3. 【推荐】API 演进守 semver；破坏性变更升 minor（0.x 期）/ major，CHANGELOG 逐版记录。

## 九、机检与构建

1. 【强制】CI 四段全绿是合并门槛：

| 段 | 命令 | 职责 |
|---|---|---|
| fmt | `cargo fmt --all -- --check` | 格式 |
| lint | `cargo clippy --all-targets -- -D warnings` | 代码味 |
| deny | `cargo deny check` | 依赖审计/许可证 |
| test | `cargo test --workspace` | 契约断言 + 业务单测 |

2. 【强制】依赖方向机检：domain 零框架依赖、api 不直引 sqlx——CI 静态扫描（`cargo tree` 断言或自定义脚本）。
3. 【强制】模板生成后冒烟（cargo generate → build → test）进 CI（对齐 web「生成后冒烟」口径）。

## 十、可机检条文清单

> 对齐 [../../contract/README.md](../../contract/README.md)「机检路线」。落地载体：CI + 契约断言测试 + 模板冒烟。

| 条文 | 机检手段 | 阶段 |
|---|---|---|
| 一-2 clippy deny warnings | CI 命令即门禁 | CI |
| 一-3 依赖审计 | cargo-deny | CI |
| 二-3 domain 零框架 | 依赖解析（cargo tree / use 扫描） | CI |
| 三-2 错误码只用常量 | 契约断言 13 码全表 | 单测 |
| 三-6 禁 println! | clippy `print_stdout` / 源码扫描 | CI |
| 四-4 禁自动建表 | 源码扫描（create_all 模式） | CI |
| 六-1 禁 async 阻塞 | clippy `await_holding_lock` 等 | CI |
| 九-1 四段门禁 | CI workflow | CI |

---

## 附：来源与拍板记录

- 框架选型 axum（R1）：crates.io 4.67 亿下载（actix-web 8 千万），tokio-rs 官方组织，tower 生态同族——与四栈「取生态事实标准」同口径。逐项论证见 [rust-stack-digest.md](../../docs/references/rust-stack-digest.md)。
- 拍板口径（R1-R7，2026-09-14 用户指令「开始动工」按建议落地）：axum+sqlx+tokio 单体 / DDD 七包 / workspace 两 crate / cargo-generate 通道 / crates.io 发版 / clippy+rustfmt+cargo-deny+契约断言 / MSRV 1.80。
- 与 `embedded/esp32` 的关系：同语言不同轨——云栈 tokio 全功能 vs 固件 esp-idf std，零共享 crate；术语表（[../../contract/README.md](../../contract/README.md) 术语对照）届时同表登记两轨方言名。
