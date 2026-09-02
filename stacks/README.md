# stacks/ · 栈模板层（预留）

各栈方言实现将按 [../docs/architecture.md](../docs/architecture.md) 的节奏、以 [../contract/](../contract/README.md) 为唯一权威来源生长，一栈一目录：

| 目录 | 规划 | 发布生态 |
|---|---|---|
| `java/` | yarch-java：parent / BOM / common / framework / archetype | Maven Central |
| `web/` | yarch-web：pnpm workspace（contract 契约包 + react/vue 适配 + UI 档模板） | npm |
| `golang/` | yarch-go：单 module 多 package（Hertz + DDD 七包模板）+ testcontainers 子 module | Go module |
| `rust/` | yarch-rust：axum + DDD | crates.io |

> 当前阶段：java ✅（reactor 16 模块全绿）；web ✅（W0-W5 冻结 + admin-demo 全链路）；golang ✅（第一批 2026-09-02：契约内核三包 + logx/middleware/web/persist/redix/httpx + testx + DDD 模板，三 module 全绿）；rust 触发式。
