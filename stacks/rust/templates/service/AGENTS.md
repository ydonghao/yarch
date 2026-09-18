# AGENTS.md — {{project-name}}

> yarch rust 服务（axum + DDD 七包）。改码前先读本文件；条文冲突以 yarch 仓 contract/ 为准。

## 工程地图

| 目录 | 职责 |
|---|---|
| `src/api/` | 入口层：axum handler/DTO/校验——只做协议转换 |
| `src/application/` | 用例编排，事务边界在此 |
| `src/domain/` | 实体/仓储端口——零框架零基础设施依赖 |
| `src/crossdomain/` | 防腐层：域间端口与适配 |
| `src/infra/` | sqlx 仓储实现（UserRow 映射回 domain 实体） |
| `src/types/` | 共享类型 |
| `src/errors/` | 3xxx 业务码登记 |
| `migrations/` | sqlx 版本化迁移（禁 runtime 建表） |

## 命令表

| 验证 | 命令 |
|---|---|
| 测试 | `cargo test` |
| 机检 | `cargo clippy --all-targets -- -D warnings` + `cargo fmt --all -- --check` |
| 起跑 | `cargo run`（迁移自动执行；探活 `YARCH_SKIP_MIGRATIONS=true`） |

## 红线清单

- 响应一律 `yarch_axum::web` 信封回包（`web::ok/err`）；判错只看 body.code，HTTP 状态码不承载业务语义。
- 错误码只用 `yarch_contract::errcode` 常量与本仓 `src/errors` 登记的 3xxx；禁裸数字散落。
- traceId 不许自造：Trace 中间件已注入 extensions 与 task_local。
- `src/domain/` 禁 import axum/sqlx/tokio（零框架）；仓储端口在 domain、实现在 infra。
- 写操作幂等走 `Idempotency-Key` 头（setup 已装配中间件），handler 不自实现。
- 逻辑删除 `is_deleted TIMESTAMPTZ`（NULL=存活）；users 禁物理 DELETE。
- 依赖方向单向 api → application → domain ← infra；禁反向 import。
- 禁 `println!`（logx ndjson 已装配，tracing 宏输出）；禁 runtime 建表（迁移文件唯一入口）。

## 契约锚点

- 信封/错误码/分页：yarch 仓 contract/api/ 四件套
- 分页装配：`yarch_axum::persist::page_of`（D6：越界空页真实 total）
- yarch 规约：https://github.com/ydonghao/yarch（stacks/rust/spec.md）
