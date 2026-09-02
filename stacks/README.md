# stacks/ · 栈模板层（预留）

各栈方言实现将按 [../docs/architecture.md](../docs/architecture.md) 的节奏、以 [../contract/](../contract/README.md) 为唯一权威来源生长，一栈一目录：

| 目录 | 规划 | 发布生态 |
|---|---|---|
| `java/` | yarch-java：parent / BOM / common / framework / archetype | Maven Central |
| `golang/` | yarch-golang：Hertz + DDD | Go module |
| `rust/` | yarch-rust：axum + DDD | crates.io |
| `web-react/` | yarch-react | npm |
| `web-vue/` | 按需 | npm |

> 当前阶段：**规约层先行**——先在 `contract/` 把契约梳理清楚、评审定稿，再落栈实现。脚手架实现已清空待重做。
