# Rust 云栈解析（axum 生态 + cargo-generate 生成器）

> **定位**：服务于 `stacks/rust` 规约评审的解析材料。**规范性条文一律以 [../../contract/](../../contract/README.md) 为准**，本文件不承载权威条文。
> 调研日期：2026-09-14；决策清单 **R1-R7** 见文末（唯一登记处 contract/README.md 关键架构决策登记表，拍板后条文才成文）。
> 依据：architecture.md 45 行 `stacks/rust/  # yarch-rust：axum+DDD → crates.io（触发式）`——本次触发=用户指令「rust 开始写，先写出规约再建工程」。

## 一、为什么是 axum（市场验证）

[crates.io 下载量](https://crates.io/crates/axum)（2026-09 快照）：

| 框架 | 总下载 | 版本 | 判断 |
|---|---|---|---|
| **axum** | **4.67 亿**（近 30 天 ~1.14 亿） | 0.8.9（2026-04） | tokio-rs 组织维护，人体工学+模块化定位 |
| actix-web | 8.09 千万 | 4.15.0 | 性能导向，生态独立 |
| poem / rocket / salvo | 低一个数量级 | — | 不入册 |

结论：axum 是 Rust 云栈无争议默认（tokio 官方组织、tower 中间件生态、与 tower-http/tracing 天然同族）。yarch 栈框架选型历来取生态事实标准（Java=Spring Boot、Golang=Hertz、Python=FastAPI），rust 取 axum 同口径。

## 二、关键生态件（成文时的依赖面）

| 关注点 | 建议栈 | 依据 |
|---|---|---|
| HTTP 框架 | axum 0.8 | 上文 |
| 异步运行时 | tokio 1.x | axum 底座 |
| ORM | sqlx 0.8（编译期 SQL 校验、async 原生、PG 一等公民） | sea-orm 更重量；sqlx 与 PG 契约（`postgresql.md`）同构 |
| 中间件 | tower + tower-http（trace/timeout/cors） | axum 官方配套 |
| 序列化 | serde + serde_json | 无替代 |
| 观测 | tracing + tracing-subscriber（ndjson 输出接 api/logging-trace） | tokio 官方 |
| 错误 | thiserror（库）+ anyhow（应用） | 社区分约定式 |
| 配置 | config-rs 或 figment（.env + 环境变量三层） | 对齐 python pydantic-settings 三环境口径 |
| 迁移 | sqlx-cli migrate 或 refinery | 对齐 flyway/alembic/golang-migrate 哲学 |
| 测试 | tokio-test + testcontainers-rs | 对齐四栈 TC 口径 |

## 三、生成器通道（cargo-generate）

[cargo-generate](https://github.com/cargo-generate/cargo-generate)（`cargo install cargo-generate --locked`）是 Rust 生态标准模板生成器：

- `cargo generate --git <repo>` 从 git 模板仓库生成；Liquid 模板引擎做占位替换；支持本地模板（`--path`）；
- 与 yarch 既有生成器范式同构（archetype 全量渲染 + 残留扫描）；

**待拍板**：rust 栈生成器走「cargo-generate + 模板仓」（生态标准通道，零自研）还是「自研 `yarch-init --lang rust`」（与 golang/python/clients 同构，统一到未来 CLI）。cargo-generate 省自研但分叉未来统一 CLI 路线（发展策划支柱 3）。

## 四、待评审决策清单（R1-R7）

> 每条给建议倾向；拍板后才成文 `stacks/rust` 规约 + architecture.md 触发式标记转正。

| # | 决策点 | 建议 | 备注 |
|---|---|---|---|
| R1 | 栈定位 | axum + sqlx + tokio 单体业务栈（DDD 七包同构 java/golang） | 微服务化由 ysaas 后续触发，栈本身按单体起步 |
| R2 | 目录结构 | DDD 七包（api/application/domain/crossdomain/infra/types/errors，同 python 修正后的 errors） | 与 golang/python 模板逐目录对齐 |
| R3 | 契约内核形态 | workspace 多 crate：`yarch-contract`（信封/错误码/trace）+ `yarch-axum`（中间件/分页/幂等） | 对偶 java starter / golang package；零框架 crate 可独立复用 |
| R4 | 生成器通道 | **cargo-generate + 模板目录**（生态标准），模板进 `stacks/rust/templates/`；远期由统一 CLI 收编为 `--lang rust` | 不自研渲染引擎（三栈已各有一套，第四套走生态标准省维护） |
| R5 | 发版 | crates.io 发布 `yarch-contract`/`yarch-axum`；tag `stacks/rust/vX.Y.Z` 走 CI（对齐四栈发版链路） | crates.io 生态坐标 `yarch` 已查净（2026-08-31） |
| R6 | 机检 | clippy（deny warnings）+ rustfmt + cargo-deny（依赖审计/许可证）+ 契约断言测试 | 对齐四栈「违反即测试失败」口径 |
| R7 | MSRV | 1.80+（axum 0.8 要求） | 与 CI 矩阵同步 |

## 五、不在本栈范围

- **嵌入式 Rust**（no_std / esp32）：另一条轨，见 [embedded-esp32-rust-digest.md](embedded-esp32-rust-digest.md)；云栈 rust 与固件 rust **不共享 crate**（云栈 tokio 全功能 vs 固件 no_std，运行时模型完全不同）；
- Node 栈（NestJS）：全域扩展拍板已列，独立施工批次；
- rust 版 web 前端 / WASM：不入册。

## 来源

- [crates.io/crates/axum](https://crates.io/crates/axum) / [crates.io/crates/actix-web](https://crates.io/crates/actix-web)（2026-09 下载量快照）
- [github.com/tokio-rs/axum](https://github.com/tokio-rs/axum)
- [github.com/cargo-generate/cargo-generate](https://github.com/cargo-generate/cargo-generate)
