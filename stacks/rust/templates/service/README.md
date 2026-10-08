# {{project-name}}

yarch rust 服务（axum + DDD 七包）。由 `cargo generate` 产出，工程守则见 [AGENTS.md](AGENTS.md)。

## 起跑

```bash
cp .env.example .env      # 改 DATABASE_URL 指向你的 PG
cargo run                 # 迁移自动执行（YARCH_SKIP_MIGRATIONS=true 可跳过）
curl -s localhost:8080/healthz | grep '"code":0'
```

## 验证

```bash
cargo test                                  # 冒烟（无 DB 部分）
DATABASE_URL=... cargo test                 # 含真库路径时
cargo clippy --all-targets -- -D warnings
cargo fmt --all -- --check
```

## 分层（DDD 七包）

api（协议转换）→ application（用例编排）→ domain（实体/端口，零框架）← infra（sqlx 实现）。

**服务名登记**：本工程名须已登记 yarch 仓 `contract/registry.md`（`^[a-z][a-z0-9-]{1,31}$` 且禁裸通用词）。
