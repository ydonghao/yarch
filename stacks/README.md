# stacks/ · 云端业务工程方言层

各栈方言实现按 [../docs/architecture.md](../docs/architecture.md) 的节奏、以 [../contract/](../contract/README.md) 为唯一权威来源生长，一栈一目录（DDD+REST 语义只在本层成立）：

| 目录 | 规划 | 发布生态 |
|---|---|---|
| `java/` | yarch-java：parent / BOM / common / framework / archetype（kotlin 拟作本栈第二 archetype，不独立发栈） | Maven Central |
| `web/` | yarch-web：pnpm workspace（contract 契约包 + react/vue 适配 + UI 档模板） | npm |
| `golang/` | yarch-go：单 module 多 package（Hertz + DDD 七包模板）+ testcontainers 子 module | Go module |
| `rust/` | yarch-rust：axum + DDD | crates.io |
| `python/` | yarch-python：FastAPI + DDD | PyPI |
| `node/` | yarch-node：NestJS + DDD | npm |

> 当前阶段：java ✅（reactor 16 模块全绿）；web ✅（W0-W5 冻结 + admin-demo 全链路）；golang ✅（第一批 2026-09-02：契约内核三包 + logx/middleware/web/persist/redix/httpx + testx + DDD 模板，三 module 全绿）；rust 触发式；python / node 已入册待启动（2026-09-03 全域扩展拍板，python 先行）。
>
> 边界：dotnet / php 经评审裁撤不纳入（2026-09-03）；C/C++ 不设业务栈，以 [../embedded/](../embedded/)（规划）固件形态进入；交互端见 [../clients/](../clients/)（android / ios 原生双轨已入册规划，触发式）。全域治理护栏（触发登记制 / 最低维护标准 / 方言一致性机检）见 [../docs/architecture.md](../docs/architecture.md) 第六节。
