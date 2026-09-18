# stacks/rust/ · Rust 云栈施工计划

> **状态：规约已立（spec.md v1.0），工程未动工**——按「规约先行」铁律，本文件是施工入口。
> 决策清单 R1-R7（拍板口径 2026-09-14，唯一登记处 [../../contract/README.md](../../contract/README.md) 关键架构决策登记表；调研见 [../../docs/references/rust-stack-digest.md](../../docs/references/rust-stack-digest.md)）。
> 本轨是**云端业务栈**（axum + DDD），与 `embedded/esp32` 固件轨零共享 crate（R8/E8 同口径）。

## 一、既定约束（来自规约与拍板，本计划不再议）

1. 框架与生态：axum 0.8 + tokio 1.x + sqlx 0.8（PG 一等公民，对齐 `contract/infra/postgresql.md`）+ tower/tower-http + tracing。
2. 契约语义：信封/错误码/traceId/分页/幂等/逻辑删除全面对齐 `contract/api` 四件套——实现与契约不一致 = bug（同四栈口径）。
3. 一行命令建工程（全局脚手架铁律）：**cargo generate + 模板目录**（R4 生态标准通道，不自研渲染引擎）。
4. 机检「违反即测试失败」：clippy deny warnings + rustfmt + 契约断言测试（R6）。

## 二、与四栈范式对齐

| 维度 | java | golang | python | web | **rust（本轨）** |
|---|---|---|---|---|---|
| 工程形态 | Maven reactor 16 模块 | 单 module 多 package | uv workspace 双发行版 | pnpm workspace 5 包 | **cargo workspace（contract + axum + template）** |
| 平台构件 | yarch-java 8 starter | yarch-go 六构件 | yarch-python 11 module | @yarch/* 三包 | **yarch-contract + yarch-axum 两 crate** |
| 模板资产 | archetype（Velocity） | `_template/`+archetype.json | `_template/`（wheel） | templates/（{{var}}） | **templates/（cargo-generate Liquid）** |
| 生成器 | archetype:generate | yarch-init | yarch-init | create-admin | **cargo generate --path** |
| 发版 | tag→Central | tag→proxy | tag→PyPI | tag→npm | **tag→crates.io**（R5） |
| 机检 | ArchUnit+Spotless | golangci depguard | ruff+import-linter | depcruise | **clippy+rustfmt+cargo-deny+契约断言** |

## 三、目标形态

```
stacks/rust/
├── PLAN.md / spec.md
├── Cargo.toml                       # workspace 根
├── crates/
│   ├── yarch-contract/              # 契约内核（零框架）：信封/13 码/traceId/分页/幂等语义
│   │   └── src/{response, errcode, trace, page}.rs
│   └── yarch-axum/                  # axum 装配：中间件（Trace/Recovery/AccessLog/Idem/Rate）
│       └── src/{middleware, extract, web}.rs
└── templates/
    └── service/                     # cargo-generate 模板（DDD 七包 + users 示例）
        ├── Cargo.toml / archetype 声明
        └── src/{api, application, domain, crossdomain, infra, types, errors}/
```

## 四、决策 R1-R7（拍板口径，引用 digest）

| # | 决策 | 结论 |
|---|---|---|
| R1 | 栈定位 | axum + sqlx + tokio 单体业务栈（DDD 七包同构 java/golang）；微服务化由 ysaas 后续触发 |
| R2 | 目录结构 | DDD 七包（api/application/domain/crossdomain/infra/types/errors，同 python 修正后口径） |
| R3 | 契约内核 | workspace 两 crate：`yarch-contract`（零框架）+ `yarch-axum`（装配）——对偶 java starter / golang package |
| R4 | 生成器通道 | cargo-generate + 模板目录（生态标准）；远期统一 CLI 收编为 `--lang rust` |
| R5 | 发版 | crates.io 发布 `yarch-contract`/`yarch-axum`；tag `stacks/rust/vX.Y.Z` 走 CI（对齐四栈发版链路） |
| R6 | 机检 | clippy（-D warnings）+ rustfmt + cargo-deny + 契约断言测试（13 码全表/信封形状/ndjson 字段） |
| R7 | MSRV | 1.80+（axum 0.8 要求）；CI 矩阵 stable + 1.80 双档 |

## 五、施工批次

1. **第一批（已完成 2026-09-18）**：spec.md 成文 + PLAN.md 落位 + workspace 骨架（两 crate + 契约内核 response/errcode/trace 三模块 + 同源 conformance 断言读 contract/dist）——本地 cargo test/fmt/clippy 绿，CI rust-stack.yml 四段门禁 + MSRV 双档。
2. **第二批（拆 2a/2b 执行，已全部完成 2026-09-18）**：2a = yarch-axum 中间件五件（Trace/Recovery/AccessLog/Idempotency/Rate，组合序外→内 AccessLog>Trace>Recovery>Rate>Idem——Rate 压 Idem 外层防 429 落库毒化）+ logx 契约 ndjson（tracing-subscriber 自定义 FormatEvent：ts/level/service/env/traceId/logger/msg+kv 平铺）+ 存储 trait 与 InMemory 实现（Redis 触发式）+ setup 一行装配 + 组合语义测试四例（镜像 python test_middleware_composition.py）。2b = 契约内核 page.rs（PageQuery/PageData）+ persist 装配（sqlx 运行时查询口径：page_of 下推 D6 + NOT_DELETED；真库集成 YARCH_PG_URL 门控专用测试库）+ cargo-generate 模板（DDD 七包 users 全链路 + AGENTS 三件套 + 迁移）+ 生成后冒烟（本地 + CI template-smoke job：生成 → 零残留 → test → clippy）。
3. **第三批**：生成后冒烟（cargo generate → cargo test）+ crates.io 发版流水线（tag 触发）+ conformance 读契约 dist（待 N2 落地后接）。

## 六、验收口径

1. workspace `cargo test` 全绿（契约断言对齐 testx/ContractAsserts 同表）；
2. clippy -D warnings / rustfmt --check / cargo-deny 三机检全绿；
3. 模板生成 → build → test 冒烟绿（CI）。

## 七、已知风险

1. sqlx 编译期 SQL 校验依赖 DATABASE_URL（离线 CI 用 `sqlx prepare` 快照模式）——模板须预置 `.sqlx/` 缓存或离线模式说明；
2. axum 0.8 中间件生态（tower-http 版本矩阵）与 tracing 的 async 上下文传播需逐项对齐 python 中间件组合语义（Rate 在 Idem 外层等）——施工时对照 python test_middleware_composition.py 逐条翻译；
3. crates.io 发布凭证与 GPG 签名流程与 Maven Central 不同（token + cargo publish），首版需走通。
