# Rust 栈批次 2b（sqlx 装配 + cargo-generate 模板）Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 完成 rust 栈第二批后半——契约分页类型（yarch-contract page.rs）、sqlx 分页/逻辑删除装配（yarch-axum persist.rs）、`templates/service/` cargo-generate 模板（DDD 七包 + users 示例 + AGENTS 三件套）、生成后冒烟（本地 + CI）。

**Architecture:** 依 `stacks/rust/PLAN.md` 第二批 2b 段：分页类型归契约内核（PLAN 目标形态 `yarch-contract/src/page.rs`）；sqlx 装配用**运行时查询**（`query_as_with` 非 query! 宏——模板/平台零 DATABASE_URL 编译前提，宏档随发版批评估 .sqlx 快照）；模板对偶 python `_template`（DDD 七包 users 示例 + 三件套）与 golang 过渡口径（依赖默认 git tag、冒烟以 `--define` 换 path 依赖——golang replace 行对偶）。

**Tech Stack:** sqlx 0.8（postgres + runtime-tokio）、async-trait、chrono、dotenvy（模板侧）；cargo-generate 0.22.1（本地已装；CI 走 taiki-e 预编译二进制）。

**cargo-generate 0.22.1 实证语义（本机验证过，勿凭记忆改）：**
1. 自定义占位符在模板文件里用**裸名** `{{yarch_contract_dep}}`（`{{placeholders.x}}` 前缀**无效**会原样透传）；
2. 只有 `.liquid` 后缀文件被渲染（后缀剥掉），其余文件原样复制；
3. 非交互必须 `--define` 全部会提示的占位符（有 default 也照样提示；不 define 在非 TTY 直接 `not a terminal` 错）；
4. `--name my-svc` 派生 `{{project-name}}`=`my-svc`、`{{crate_name}}`=`my_svc`；
5. 模板目录必须干净（杂物目录会被当模板内容复制出去）。

**范围边界（不在本计划）：** Redis 存储实现（触发式）；query! 宏 + `.sqlx` 离线快照（随发版批）；signed_api；SP1 组件库。

## Global Constraints

- pathspec 提交铁律；本地命令 `cd /Users/yuandonghao/sidejob/sources/yarch/stacks/rust` 后执行。
- 机检三件全绿才提交（**注意 clippy/test 管道掩码坑：用 `; EXIT=$?` 显式判**）。
- 集成测试经 `YARCH_PG_URL` 门控（未设则跳过，CI smoke 不设即跳）；本地值指向共享 PG 上**专用测试库** `yarch_itest`（服务器已建同名角色/库，凭证不入仓——见服务器 compose 纪律；测试每用例独立表名，测试自清）。
- MSRV 1.80；sqlx/async-trait/chrono 均兼容。
- 模板生成物零残留 `{{`（冒烟 grep 校验）。
- 术语表「分页负载 rust —（批次二）」与本计划落地后同步为实（收尾任务）。

---

### Task 1: yarch-contract `page.rs`（分页契约类型）

**Files:**
- Create: `crates/yarch-contract/src/page.rs`
- Modify: `crates/yarch-contract/src/lib.rs`（`pub mod page;`）
- Modify: `contract/README.md` 术语表（分页负载 rust 行 + 追踪 ID 行不变）

**Interfaces:**
- Produces:
  - `pub struct PageQuery { pub page: i32, pub page_size: i32 }`（serde camelCase `pageSize`）：`new(page, page_size) -> Result<Self, String>`（D6：page≥1、pageSize 1..=100）、`from_params(page: Option<&str>, page_size: Option<&str>) -> Result<Self, String>`（默认 1/20）、`offset() -> i64`、`limit() -> i64`
  - `pub struct PageData<T> { pub list: Vec<T>, pub total: i64, pub page: i32, pub page_size: i32, pub next_cursor: Option<String> }`（serde camelCase；nextCursor None 不序列化——空串/缺失=无下一页）
  - `PageData::new(list, total, page, page_size)`

- [ ] **Step 1: 写 `page.rs`（实现 + 内联测试）**

```rust
//! 分页契约（契约内核）：页码通道（D6：越界返回空页）+ keyset 游标字段。
//!
//! 语义唯一权威 = contract/api/rest-response.md 分页负载 + rest-conventions.md 分页。
//! 页码通道由 sqlx 装配（yarch-axum persist）下推；keyset 通道为调用方 SQL 模式
//! （`WHERE id > $last LIMIT n`），nextCursor 由调用方生成。

use serde::{Deserialize, Serialize};

/// 分页查询参数。1-based；pageSize 1~100（rest-conventions D6）。
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
pub struct PageQuery {
    pub page: i32,
    #[serde(rename = "pageSize")]
    pub page_size: i32,
}

impl PageQuery {
    /// 构造并校验（非法返回错误描述，供上层拼 1001 文案）。
    pub fn new(page: i32, page_size: i32) -> Result<Self, String> {
        if page < 1 {
            return Err("page 须为正整数".to_string());
        }
        if !(1..=100).contains(&page_size) {
            return Err("pageSize 须在 1~100".to_string());
        }
        Ok(Self { page, page_size })
    }

    /// 从查询参数解析：缺省 page=1、pageSize=20；非整数/越界返回错误描述。
    pub fn from_params(page: Option<&str>, page_size: Option<&str>) -> Result<Self, String> {
        let page = match page {
            None => 1,
            Some(raw) => raw
                .trim()
                .parse::<i32>()
                .map_err(|_| "page 须为整数".to_string())?,
        };
        let page_size = match page_size {
            None => 20,
            Some(raw) => raw
                .trim()
                .parse::<i32>()
                .map_err(|_| "pageSize 须为整数".to_string())?,
        };
        Self::new(page, page_size)
    }

    /// OFFSET（页码通道下推用）
    pub fn offset(&self) -> i64 {
        (self.page as i64 - 1) * self.page_size as i64
    }

    /// LIMIT
    pub fn limit(&self) -> i64 {
        self.page_size as i64
    }
}

/// 分页负载（rest-response.md：list 可空数组不得为 null；total 过滤后总数）。
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct PageData<T> {
    pub list: Vec<T>,
    pub total: i64,
    pub page: i32,
    #[serde(rename = "pageSize")]
    pub page_size: i32,
    #[serde(rename = "nextCursor", skip_serializing_if = "Option::is_none")]
    pub next_cursor: Option<String>,
}

impl<T> PageData<T> {
    pub fn new(list: Vec<T>, total: i64, page: i32, page_size: i32) -> Self {
        Self { list, total, page, page_size, next_cursor: None }
    }

    /// keyset 通道：设置下一页游标（空串按 None 处理——缺失或空串都表示没有下一页）
    pub fn with_next_cursor(mut self, cursor: impl Into<String>) -> Self {
        let cursor = cursor.into();
        self.next_cursor = if cursor.is_empty() { None } else { Some(cursor) };
        self
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn new_validates_d6_rules() {
        assert!(PageQuery::new(1, 20).is_ok());
        assert_eq!(PageQuery::new(0, 20).unwrap_err(), "page 须为正整数");
        assert_eq!(PageQuery::new(1, 0).unwrap_err(), "pageSize 须在 1~100");
        assert_eq!(PageQuery::new(1, 101).unwrap_err(), "pageSize 须在 1~100");
    }

    #[test]
    fn from_params_defaults_and_parses() {
        let q = PageQuery::from_params(None, None).unwrap();
        assert_eq!((q.page, q.page_size), (1, 20));
        let q = PageQuery::from_params(Some("3"), Some("10")).unwrap();
        assert_eq!((q.page, q.page_size, q.offset(), q.limit()), (3, 10, 20, 10));
        assert!(PageQuery::from_params(Some("x"), None).is_err());
        assert!(PageQuery::from_params(None, Some("999")).is_err());
    }

    #[test]
    fn page_data_serializes_camel_case_and_empty_list() {
        let pd = PageData::new(Vec::<i32>::new(), 5, 2, 2);
        let json = serde_json::to_string(&pd).unwrap();
        assert_eq!(json, r#"{"list":[],"total":5,"page":2,"pageSize":2}"#);
        let pd = PageData::new(vec![1, 2], 5, 1, 2).with_next_cursor("abc");
        let json = serde_json::to_string(&pd).unwrap();
        assert_eq!(json, r#"{"list":[1,2],"total":5,"page":1,"pageSize":2,"nextCursor":"abc"}"#);
        // 空串游标 = 无下一页（不序列化）
        let pd = PageData::new(vec![1], 1, 1, 1).with_next_cursor("");
        assert!(!serde_json::to_string(&pd).unwrap().contains("nextCursor"));
    }
}
```

- [ ] **Step 2: lib.rs 挂模块 + 跑测试 + 机检 + 术语表同步 + Commit**

```bash
cd /Users/yuandonghao/sidejob/sources/yarch/stacks/rust && cargo test -p yarch-contract page && cargo fmt --all && cargo clippy --all-targets -- -D warnings
```

`contract/README.md` 术语表 rust 行改：
`| 分页负载 | … | \`PageData<T>\`（yarch_contract::page） |`（原「—（批次二 sqlx 装配）」）。

```bash
cd /Users/yuandonghao/sidejob/sources/yarch && git add stacks/rust/crates/yarch-contract contract/README.md
git commit -m "feat(rust): 契约内核 page——PageQuery（D6 校验/默认 1·20）+ PageData（camelCase/空 list 非空体/nextCursor 空串即无）" -- stacks/rust/crates/yarch-contract contract/README.md
```

---

### Task 2: yarch-axum `persist.rs`（sqlx 分页/逻辑删除装配）

**Files:**
- Create: `crates/yarch-axum/src/persist.rs`
- Modify: `crates/yarch-axum/src/lib.rs`（`pub mod persist;`）、`crates/yarch-axum/Cargo.toml`（sqlx 依赖）
- Test: `crates/yarch-axum/tests/persist_itest.rs`（YARCH_PG_URL 门控）

**Interfaces:**
- Consumes: `yarch_contract::page::{PageData, PageQuery}`
- Produces:
  - `pub async fn page_of<T>(pool: &sqlx::postgres::PgPool, select: &str, base: &str, order_by: &str, args: sqlx::postgres::PgArguments, q: &PageQuery) -> sqlx::Result<PageData<T>> where T: for<'r> sqlx::FromRow<'r, sqlx::postgres::PgRow>`
  - `pub const NOT_DELETED: &str = "is_deleted IS NULL"`（四栈 is_deleted 逻辑删除语义）
  - keyset 注释位（页码通道装配；keyset = 调用方 SQL 模式）

- [ ] **Step 1: Cargo.toml 加 sqlx**

```toml
sqlx = { version = "0.8", default-features = false, features = ["runtime-tokio", "postgres"] }
```

- [ ] **Step 2: 写 `persist.rs`**

```rust
//! sqlx 装配（spec 五-1/五-2）：分页下推（D6）+ 逻辑删除纪律。
//! 运行时查询口径（query_as_with，非 query! 宏）——业务工程零 DATABASE_URL 编译前提；
//! query! 宏 + .sqlx 离线快照档随发版批评估。
//!
//! 纪律：select/base/order_by 只允许**调用方常量字符串**（对偶 golang Scope），
//! 用户输入只经 PgArguments 绑定，杜绝 SQL 注入面。

use sqlx::postgres::{PgArguments, PgPool};
use yarch_contract::page::{PageData, PageQuery};

/// 逻辑删除过滤片段（四栈口径：is_deleted timestamptz，NULL = 存活）。
pub const NOT_DELETED: &str = "is_deleted IS NULL";

/// 分页查询装配：count（过滤后总数）+ LIMIT/OFFSET 下推当前页。
/// D6 天然成立：page 超出总页数时 OFFSET 越界返回空 list、total 仍为真实总数。
///
/// - `select`：列清单（如 "id, name, created_at, is_deleted"）
/// - `base`：FROM + WHERE 片段（如 "FROM users WHERE is_deleted IS NULL AND name ILIKE $1"）
/// - `order_by`：排序（如 "id"）；keyset 通道由调用方走 `WHERE id > $n` 模式 + PageData::with_next_cursor
///
/// LIMIT/OFFSET 追加为末两个占位符（编号 = args.len()+1 / +2），PgArguments 可 Clone 派生。
pub async fn page_of<T>(
    pool: &PgPool,
    select: &str,
    base: &str,
    order_by: &str,
    mut args: PgArguments,
    q: &PageQuery,
) -> sqlx::Result<PageData<T>>
where
    T: for<'r> sqlx::FromRow<'r, sqlx::postgres::PgRow>,
{
    let total: i64 = sqlx::query_scalar_with(&format!("SELECT count(*) {base}"), args.clone())?
        .fetch_one(pool)
        .await?;

    let limit_no = args.len() + 1;
    let offset_no = args.len() + 2;
    let list_sql = format!("SELECT {select} {base} ORDER BY {order_by} LIMIT ${limit_no} OFFSET ${offset_no}");
    args.add(q.limit());
    args.add(q.offset());
    let list: Vec<T> = sqlx::query_as_with(&list_sql, args)?
        .fetch_all(pool)
        .await?;
    Ok(PageData::new(list, total, q.page, q.page_size))
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn not_deleted_fragment_is_constant() {
        assert_eq!(NOT_DELETED, "is_deleted IS NULL");
    }
}
```

（注：`query_scalar_with`/`query_as_with` 的泛型标注以编译器报错为准微调——语义不变：count 与 list 复用同一 base/args，LIMIT/OFFSET 为追加参数。）

- [ ] **Step 3: 写 `tests/persist_itest.rs`（YARCH_PG_URL 门控真库）**

```rust
//! persist 集成测试：真 PG（YARCH_PG_URL 门控，未设跳过——CI 冒烟不设即跳）。
//! 共享实例纪律：一律 yarch_itest schema + 每测试独立表名，测试自建自清。

use sqlx::postgres::{PgArguments, PgPool};
use yarch_axum::persist::{page_of, NOT_DELETED};
use yarch_contract::page::{PageData, PageQuery};

#[derive(Debug, sqlx::FromRow, PartialEq)]
struct Row {
    id: i64,
    name: String,
}

async fn pool() -> Option<PgPool> {
    let url = std::env::var("YARCH_PG_URL").ok()?;
    Some(
        sqlx::postgres::PgPoolOptions::new()
            .max_connections(2)
            .connect(&url)
            .await
            .expect("YARCH_PG_URL 连接失败"),
    )
}

async fn setup_table(pool: &PgPool, table: &str, rows: i64) {
    sqlx::query(&format!("DROP TABLE IF EXISTS yarch_itest.{table}")).execute(pool).await.unwrap();
    sqlx::query(&format!("CREATE SCHEMA IF NOT EXISTS yarch_itest")).execute(pool).await.unwrap();
    sqlx::query(&format!(
        "CREATE TABLE yarch_itest.{table} (id BIGSERIAL PRIMARY KEY, name TEXT NOT NULL, is_deleted TIMESTAMPTZ)"
    ))
    .execute(pool)
    .await
    .unwrap();
    for i in 1..=rows {
        sqlx::query(&format!(
            "INSERT INTO yarch_itest.{table} (name) VALUES ($1)"
        ))
        .bind(format!("u{i}"))
        .execute(pool)
        .await
        .unwrap();
    }
}

fn empty_args() -> PgArguments {
    PgArguments::default()
}

#[tokio::test]
async fn page_of_paginates_and_counts() {
    let Some(pool) = pool().await else {
        println!("YARCH_PG_URL 未设置，跳过 persist 集成测试");
        return;
    };
    setup_table(&pool, "page_a", 5).await;
    let q = PageQuery::new(2, 2).unwrap();
    let pd: PageData<Row> = page_of::<Row>(
        &pool,
        "id, name",
        &format!("FROM yarch_itest.page_a WHERE {NOT_DELETED}"),
        "id",
        empty_args(),
        &q,
    )
    .await
    .unwrap();
    assert_eq!((pd.total, pd.page, pd.page_size), (5, 2, 2));
    assert_eq!(pd.list, vec![Row { id: 3, name: "u3".into() }, Row { id: 4, name: "u4".into() }]);
}

#[tokio::test]
async fn page_of_d6_out_of_range_returns_empty_list_with_real_total() {
    let Some(pool) = pool().await else {
        println!("YARCH_PG_URL 未设置，跳过 persist 集成测试");
        return;
    };
    setup_table(&pool, "page_b", 3).await;
    let q = PageQuery::new(99, 20).unwrap();
    let pd: PageData<Row> = page_of::<Row>(
        &pool,
        "id, name",
        &format!("FROM yarch_itest.page_b WHERE {NOT_DELETED}"),
        "id",
        empty_args(),
        &q,
    )
    .await
    .unwrap();
    assert!(pd.list.is_empty(), "越界页必须空 list");
    assert_eq!(pd.total, 3, "total 仍为真实总数（D6）");
}

#[tokio::test]
async fn page_of_respects_logical_delete_and_bound_args() {
    let Some(pool) = pool().await else {
        println!("YARCH_PG_URL 未设置，跳过 persist 集成测试");
        return;
    };
    setup_table(&pool, "page_c", 4).await;
    sqlx::query("UPDATE yarch_itest.page_c SET is_deleted = now() WHERE id = 2")
        .execute(&pool)
        .await
        .unwrap();
    let mut args = PgArguments::default();
    args.add("u%".to_string());
    let q = PageQuery::new(1, 10).unwrap();
    let pd: PageData<Row> = page_of::<Row>(
        &pool,
        "id, name",
        &format!("FROM yarch_itest.page_c WHERE {NOT_DELETED} AND name LIKE $1"),
        "id",
        args,
        &q,
    )
    .await
    .unwrap();
    assert_eq!(pd.total, 3, "逻辑删除行不计入（is_deleted IS NULL）");
    assert_eq!(pd.list.len(), 3);
    assert!(pd.list.iter().all(|r| r.id != 2));
}
```

- [ ] **Step 4: 跑测试（带 PG）+ 机检 + Commit**

```bash
cd /Users/yuandonghao/sidejob/sources/yarch/stacks/rust
YARCH_PG_URL='postgres://yarch_itest:<专用测试库凭证>@yuandonghao-linux:5432/yarch_itest' cargo test -p yarch-axum
cargo fmt --all; cargo clippy --all-targets -- -D warnings; echo "clippy exit: $?"
```

```bash
cd /Users/yuandonghao/sidejob/sources/yarch && git add stacks/rust/crates/yarch-axum stacks/rust/Cargo.lock
git commit -m "feat(rust): yarch-axum persist——sqlx 分页装配 page_of（count+LIMIT/OFFSET 下推，D6 越界空页）+ NOT_DELETED 逻辑删除纪律（运行时查询口径；集成测试 YARCH_PG_URL 门控真库三例）" -- stacks/rust/crates/yarch-axum stacks/rust/Cargo.lock
```

---

### Task 3: 模板骨架（cargo-generate.toml + 壳文件）

**Files:**
- Create: `stacks/rust/templates/service/cargo-generate.toml`
- Create: `templates/service/Cargo.toml.liquid`
- Create: `templates/service/.gitignore`、`.env.example`、`README.md`、`crossdomain/README.md`

**Interfaces:**
- Produces: 可 `cargo generate --path` 生成服务的模板骨架；占位符 `yarch_contract_dep`/`yarch_axum_dep`（默认 git tag 形态，冒烟 `--define` 换 path——golang replace 行对偶）

- [ ] **Step 1: `cargo-generate.toml`**

```toml
[template]
cargo_generate_version = ">=0.21.0"

[placeholders]
yarch_contract_dep = { type = "string", prompt = "yarch-contract 依赖形态（TOML 值；发版后默认 git tag，本地开发可换 path）", default = '{ git = "https://github.com/ydonghao/yarch.git", tag = "stacks/rust/v0.1.0" }' }
yarch_axum_dep = { type = "string", prompt = "yarch-axum 依赖形态（TOML 值；同上）", default = '{ git = "https://github.com/ydonghao/yarch.git", tag = "stacks/rust/v0.1.0" }' }
```

- [ ] **Step 2: `Cargo.toml.liquid`**

```liquid
[package]
name = "{{project-name}}"
version = "0.1.0"
edition = "2021"
description = "yarch rust 服务（axum + DDD 七包，cargo generate 产出）"

[dependencies]
yarch-contract = {{yarch_contract_dep}}
yarch-axum = {{yarch_axum_dep}}
axum = "0.8"
tokio = { version = "1", features = ["full"] }
sqlx = { version = "0.8", default-features = false, features = ["runtime-tokio", "postgres", "migrate", "chrono"] }
async-trait = "0.1"
chrono = { version = "0.4", features = ["serde"] }
serde = { version = "1", features = ["derive"] }
serde_json = "1"
dotenvy = "0.8"

[dev-dependencies]
tower = { version = "0.5", features = ["util"] }
```

- [ ] **Step 3: 壳文件**

`.gitignore`：
```
/target
.env
```

`.env.example`：
```
# yarch rust 服务环境（.env 复制后改）
YARCH_SERVICE={{project-name}}
YARCH_ENV=local
DATABASE_URL=postgres://postgres:postgres@127.0.0.1:5432/{{crate_name}}?sslmode=disable
# 探活跳过建库迁移（engine 惰性连接场景）
# YARCH_SKIP_MIGRATIONS=true
```

`README.md`：
```markdown
# {{project-name}}

yarch rust 服务（axum + DDD 七包）。由 `cargo generate` 产出，规约锚点见 [AGENTS.md](AGENTS.md)。

## 起跑

```bash
cp .env.example .env      # 改 DATABASE_URL 指向你的 PG
cargo run                 # 迁移自动执行（YARCH_SKIP_MIGRATIONS=true 跳过）
curl -s localhost:8080/healthz | grep '"code":0'
```

## 验证

```bash
cargo test          # 冒烟（无 DB 部分）；DATABASE_URL 设置后含集成
cargo clippy --all-targets -- -D warnings
cargo fmt --all -- --check
```

## 分层（DDD 七包）

api（协议转换）→ application（用例编排）→ domain（实体/端口，零框架）← infra（sqlx 实现）。
**服务名登记**：本工程名须已登记 yarch 仓 registry.md（`^[a-z][a-z0-9-]{1,31}$` 且禁裸通用词）。
```

`crossdomain/README.md`：
```markdown
# crossdomain · 防腐层

域间交互端口与适配（跨域调用别的服务/领域时的隔离层）。users 示例未涉及，留位。
```

- [ ] **Step 4: Commit**

```bash
cd /Users/yuandonghao/sidejob/sources/yarch && git add stacks/rust/templates/service
git commit -m "feat(rust): cargo-generate 模板骨架——cargo-generate.toml（yarch 依赖占位 git tag 默认）+ Cargo.toml.liquid + 壳文件" -- stacks/rust/templates/service
```

---

### Task 4: 模板 src（DDD 七包 + users 示例 + 迁移）

**Files:**
- Create: `templates/service/src/main.rs`、`src/api/{mod.rs,users.rs}`、`src/application/mod.rs`、`src/domain/{mod.rs,entity.rs,repository.rs}`、`src/infra/{mod.rs,user_row.rs,user_repo.rs}`、`src/types/mod.rs`、`src/errors/mod.rs`
- Create: `templates/service/migrations/0001_init.sql`

**Interfaces:**
- Consumes: yarch-axum（setup/Options/web/persist）、yarch-contract（errcode/page）
- Produces: 生成工程完整可跑（main：连接 PG + 迁移 + setup 装配 + axum::serve；users CRUD 全链路）

- [ ] **Step 1: `src/main.rs`**

```rust
mod api;
mod application;
mod crossdomain;
mod domain;
mod errors;
mod infra;
mod types;

use std::sync::Arc;
use std::time::Duration;

use axum::routing::get;
use axum::Router;

use yarch_axum::store::{InMemoryIdempotencyStore, InMemoryRateLimiter};
use yarch_axum::{setup, IdemConfig, Options, RateConfig};

#[tokio::main]
async fn main() {
    dotenvy::dotenv().ok();
    errors::register_all();
    let service = std::env::var("YARCH_SERVICE").unwrap_or_else(|_| "{{project-name}}".to_string());
    let env = std::env::var("YARCH_ENV").unwrap_or_else(|_| "local".to_string());
    let database_url =
        std::env::var("DATABASE_URL").expect("DATABASE_URL 必须设置（参照 .env.example）");

    let pool = sqlx::postgres::PgPoolOptions::new()
        .max_connections(10)
        .connect(&database_url)
        .await
        .expect("PG 连接失败");
    if std::env::var("YARCH_SKIP_MIGRATIONS").as_deref() != Ok("true") {
        sqlx::migrate!("migrations").run(&pool).await.expect("迁移失败");
    }

    let state = api::AppState {
        users: Arc::new(infra::user_repo::UserRepoSqlx::new(pool)),
    };
    let router = Router::new()
        .route("/healthz", get(api::healthz))
        .route("/api/v1/echo", axum::routing::post(api::echo))
        .route(
            "/api/v1/users",
            get(api::users::list).post(api::users::create),
        )
        .route(
            "/api/v1/users/{id}",
            get(api::users::get_one).delete(api::users::remove),
        )
        .fallback(api::not_found)
        .with_state(state);

    let app = setup(
        router,
        Options {
            service,
            env,
            idempotency: Some(IdemConfig { store: Arc::new(InMemoryIdempotencyStore::new()) }),
            rate: Some(RateConfig {
                limiter: Arc::new(InMemoryRateLimiter::new()),
                limit: 60,
                window: Duration::from_secs(60),
            }),
        },
    );
    let listener = tokio::net::TcpListener::bind("0.0.0.0:8080").await.expect("端口绑定失败");
    axum::serve(listener, app).await.expect("服务异常退出");
}
```

- [ ] **Step 2: `src/api/mod.rs` 与 `src/api/users.rs`**

`api/mod.rs`：
```rust
//! 入口层：协议转换（DTO/校验/路由），零业务。

pub mod users;

use std::sync::Arc;

use axum::extract::{Path, Query, State};
use axum::http::StatusCode;
use axum::response::Response;
use serde::Deserialize;

use crate::domain::repository::UserRepository;
use crate::infra::user_repo::UserRepoSqlx;
use yarch_axum::web;
use yarch_contract::page::PageQuery;

pub struct AppState {
    pub users: Arc<dyn UserRepository>,
}

pub async fn healthz() -> Response {
    web::ok(serde_json::json!({ "status": "up" }))
}

/// 未匹配路由兜底：404 → 1004 信封（禁裸 404 文本——前端解包不失效）
pub async fn not_found() -> Response {
    web::err(1004)
}

#[derive(Deserialize)]
pub struct EchoIn {
    pub name: String,
}

/// 演示端点：信封回包 + 幂等中间件联动（带 Idempotency-Key 即受 1007/回放保护）
pub async fn echo(axum::Json(body): axum::Json<EchoIn>) -> Response {
    web::ok(serde_json::json!({ "greeting": format!("hello, {}", body.name) }))
}
```

`api/users.rs`：
```rust
//! users 端点：分页列表 / 详情 / 创建 / 逻辑删除。

use axum::extract::{Path, Query, State};
use axum::response::Response;
use serde::Deserialize;

use crate::api::AppState;
use crate::errors;
use yarch_axum::web;
use yarch_contract::page::PageQuery;

#[derive(Deserialize)]
pub struct ListParams {
    pub page: Option<String>,
    #[serde(rename = "pageSize")]
    pub page_size: Option<String>,
    pub keyword: Option<String>,
}

pub async fn list(State(state): State<AppState>, Query(params): Query<ListParams>) -> Response {
    let q = match PageQuery::from_params(params.page.as_deref(), params.page_size.as_deref()) {
        Ok(q) => q,
        Err(detail) => return web::err_with_message(1001, format!("参数校验失败：{detail}")),
    };
    match state.users.page(&q, params.keyword.as_deref()).await {
        Ok(pd) => web::ok(pd),
        Err(_) => web::err(1000),
    }
}

pub async fn get_one(State(state): State<AppState>, Path(id): Path<i64>) -> Response {
    match state.users.find(id).await {
        Ok(Some(user)) => web::ok(user),
        Ok(None) => web::err(1004),
        Err(_) => web::err(1000),
    }
}

pub async fn create(State(state): State<AppState>, axum::Json(body): axum::Json<CreateIn>) -> Response {
    let name = body.name.trim().to_string();
    if name.is_empty() {
        return web::err_with_message(1001, "参数校验失败：name 不得为空".to_string());
    }
    match state.users.create(&name).await {
        Ok(user) => web::ok_status(user, 201),
        Err(e) if e == errors::user_name_taken_message() => web::err(3001),
        Err(_) => web::err(1000),
    }
}

#[derive(Deserialize)]
pub struct CreateIn {
    pub name: String,
}

pub async fn remove(State(state): State<AppState>, Path(id): Path<i64>) -> Response {
    match state.users.soft_delete(id).await {
        Ok(true) => web::ok(serde_json::json!({ "removed": id })),
        Ok(false) => web::err(1004),
        Err(_) => web::err(1000),
    }
}
```

- [ ] **Step 3: `src/domain/`（实体 + 端口，零框架）**

`domain/mod.rs`：`pub mod entity; pub mod repository;`

`domain/entity.rs`：
```rust
//! 领域实体（零框架：不依赖 axum/sqlx/tokio）。

#[derive(Debug, Clone, PartialEq)]
pub struct User {
    pub id: i64,
    pub name: String,
    pub created_at: chrono::DateTime<chrono::Utc>,
}
```

`domain/repository.rs`：
```rust
//! 仓储端口（依赖倒置：端口在 domain，实现在 infra）。

use async_trait::async_trait;

use super::entity::User;
use yarch_contract::page::{PageData, PageQuery};

#[async_trait]
pub trait UserRepository: Send + Sync {
    /// 分页列表（keyword 模糊过滤；D6 越界空页）
    async fn page(&self, q: &PageQuery, keyword: Option<&str>) -> Result<PageData<User>, String>;
    /// 按 id 查存活用户；None = 不存在或已逻辑删除
    async fn find(&self, id: i64) -> Result<Option<User>, String>;
    /// 创建；重名返回 errors::user_name_taken_message()
    async fn create(&self, name: &str) -> Result<User, String>;
    /// 逻辑删除（is_deleted = now()）；false = 不存在或已删
    async fn soft_delete(&self, id: i64) -> Result<bool, String>;
}
```

- [ ] **Step 4: `src/infra/`（sqlx 实现：UserRow FromRow + 映射回 domain）**

`infra/mod.rs`：`pub mod user_repo; pub mod user_row;`

`infra/user_row.rs`（ORM 行对象归 infra——domain 实体零 sqlx 依赖，对偶 python models.py/entity.py 分层）：
```rust
//! sqlx 行对象（FromRow 派生归 infra；domain 实体不沾 sqlx）。

use crate::domain::entity::User;

#[derive(Debug, sqlx::FromRow)]
pub struct UserRow {
    pub id: i64,
    pub name: String,
    pub created_at: chrono::DateTime<chrono::Utc>,
}

impl From<UserRow> for User {
    fn from(r: UserRow) -> Self {
        Self { id: r.id, name: r.name, created_at: r.created_at }
    }
}
```

`infra/user_repo.rs`：
```rust
//! 仓储实现：sqlx PG（运行时查询；page_of 下推 + NOT_DELETED 纪律）。

use async_trait::async_trait;

use super::user_row::UserRow;
use crate::domain::entity::User;
use crate::domain::repository::UserRepository;
use crate::errors;
use yarch_axum::persist::{page_of, NOT_DELETED};
use yarch_contract::page::{PageData, PageQuery};

pub struct UserRepoSqlx {
    pool: sqlx::postgres::PgPool,
}

impl UserRepoSqlx {
    pub fn new(pool: sqlx::postgres::PgPool) -> Self {
        Self { pool }
    }
}

#[async_trait]
impl UserRepository for UserRepoSqlx {
    async fn page(&self, q: &PageQuery, keyword: Option<&str>) -> Result<PageData<User>, String> {
        let (base, args) = match keyword {
            Some(kw) if !kw.trim().is_empty() => (
                format!("FROM users WHERE {NOT_DELETED} AND name ILIKE $1"),
                {
                    let mut a = sqlx::postgres::PgArguments::default();
                    a.add(format!("%{}%", kw.trim()));
                    a
                },
            ),
            _ => (format!("FROM users WHERE {NOT_DELETED}"), Default::default()),
        };
        let pd: PageData<UserRow> = page_of(
            &self.pool,
            "id, name, created_at",
            &base,
            "id",
            args,
            q,
        )
        .await
        .map_err(|e| format!("page users: {e}"))?;
        Ok(PageData::new(pd.list.into_iter().map(Into::into).collect(), pd.total, pd.page, pd.page_size))
    }

    async fn find(&self, id: i64) -> Result<Option<User>, String> {
        let row: Option<UserRow> = sqlx::query_as(
            &format!("SELECT id, name, created_at FROM users WHERE id = $1 AND {NOT_DELETED}"),
        )
        .bind(id)
        .fetch_optional(&self.pool)
        .await
        .map_err(|e| format!("find user: {e}"))?;
        Ok(row.map(Into::into))
    }

    async fn create(&self, name: &str) -> Result<User, String> {
        let exists: Option<i64> = sqlx::query_scalar(
            &format!("SELECT id FROM users WHERE name = $1 AND {NOT_DELETED}"),
        )
        .bind(name)
        .fetch_optional(&self.pool)
        .await
        .map_err(|e| format!("check user name: {e}"))?;
        if exists.is_some() {
            return Err(errors::user_name_taken_message());
        }
        let row: UserRow = sqlx::query_as(
            "INSERT INTO users (name) VALUES ($1) RETURNING id, name, created_at",
        )
        .bind(name)
        .fetch_one(&self.pool)
        .await
        .map_err(|e| format!("create user: {e}"))?;
        Ok(row.into())
    }

    async fn soft_delete(&self, id: i64) -> Result<bool, String> {
        let result = sqlx::query(&format!(
            "UPDATE users SET is_deleted = now() WHERE id = $1 AND {NOT_DELETED}"
        ))
        .bind(id)
        .execute(&self.pool)
        .await
        .map_err(|e| format!("soft delete user: {e}"))?;
        Ok(result.rows_affected() > 0)
    }
}
```

- [ ] **Step 5: `src/types/mod.rs`、`src/errors/mod.rs`、`src/application/mod.rs`**

`types/mod.rs`：
```rust
//! 共享类型（标识/枚举/值对象）。users 示例未提炼独立类型，留位。
```

`errors/mod.rs`：
```rust
//! 业务错误码（3xxx 段，业务仓登记后方可使用；装配期 fail-fast）。

use yarch_contract::errcode;

pub const USER_NAME_TAKEN_CODE: i32 = 3001;
const USER_NAME_TAKEN_KEY: &str = "USER_NAME_TAKEN";
const USER_NAME_TAKEN_MSG: &str = "用户名已存在";

/// 3001 的默认文案（仓储层以字符串回传业务错误，api 层映射回码）
pub fn user_name_taken_message() -> String {
    USER_NAME_TAKEN_MSG.to_string()
}

pub fn register_all() {
    errcode::register(3001, USER_NAME_TAKEN_KEY, USER_NAME_TAKEN_MSG, 409)
        .expect("errno 3001 登记失败（冲突=段位已占用）");
}
```

`application/mod.rs`：
```rust
//! 应用层：用例编排（一用例一函数，事务边界在此）。users 示例为单步用例，
//! 编排直接经仓储端口（无跨实体事务），事务边界注释位留待复杂用例。
```

- [ ] **Step 6: `migrations/0001_init.sql`**

```sql
-- users 表（postgresql.md 口径：snake_case/单数语义表名因 user 是 PG 保留字取 users/
-- 时间列 timestamptz/逻辑删除 is_deleted 为 timestamptz——四栈同列名）
CREATE TABLE IF NOT EXISTS users (
    id         BIGSERIAL PRIMARY KEY,
    name       TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    is_deleted TIMESTAMPTZ
);
CREATE INDEX IF NOT EXISTS idx_users_is_deleted ON users (is_deleted) WHERE is_deleted IS NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_users_name_alive ON users (name) WHERE is_deleted IS NULL;
```

- [ ] **Step 7: Commit**

```bash
cd /Users/yuandonghao/sidejob/sources/yarch && git add stacks/rust/templates/service
git commit -m "feat(rust): 模板 src——DDD 七包 users 全链路（domain 端口零框架/infra UserRow 映射/api 信封回包/3001 登记）+ 迁移（is_deleted timestamptz + 部分唯一索引）" -- stacks/rust/templates/service
```

---

### Task 5: 模板 AGENTS 三件套 + tests/smoke.rs

**Files:**
- Create: `templates/service/AGENTS.md`、`CLAUDE.md`、`GEMINI.md`
- Create: `templates/service/tests/smoke.rs`

- [ ] **Step 1: `AGENTS.md`（四章节定式，≤150 行；红线取 rust spec 条文直译）**

```markdown
# AGENTS.md — {{project-name}}

> yarch rust 服务（axum + DDD 七包）。改码前先读本文件；条文冲突以 yarch 仓 contract/ 为准。

## 工程地图

| 目录 | 职责 |
|---|---|
| `src/api/` | 入口层：axum handler/DTO/校验——只做协议转换 |
| `src/application/` | 用例编排，事务边界 |
| `src/domain/` | 实体/仓储端口——零框架零基础设施依赖 |
| `src/crossdomain/` | 防腐层：域间端口与适配 |
| `src/infra/` | sqlx 仓储实现（UserRow 映射回 domain 实体） |
| `src/types/` | 共享类型；`src/errors/` | 3xxx 业务码登记 |
| `migrations/` | sqlx 版本化迁移（禁 runtime 建表） |

## 命令表

| 验证 | 命令 |
|---|---|
| 测试 | `cargo test`（DATABASE_URL 设置后含集成） |
| 机检 | `cargo clippy --all-targets -- -D warnings` + `cargo fmt --all -- --check` |
| 起跑 | `cargo run`（迁移自动执行；探活 `YARCH_SKIP_MIGRATIONS=true`） |

## 红线清单

- 响应一律 `yarch_axum::web` 信封回包（`web::ok/err`）；判错只看 body.code
- 错误码只用 `yarch_contract::errcode` 常量/本仓 `src/errors` 登记的 3xxx；禁裸数字散落
- traceId 不许自造：Trace 中间件已注入 extensions + task_local
- `src/domain/` 禁 import axum/sqlx/tokio（零框架）；仓储端口在 domain、实现在 infra
- 写操作幂等走 `Idempotency-Key` 头（setup 已装配中间件），handler 不自实现
- 逻辑删除 `is_deleted TIMESTAMPTZ`（NULL=存活）；禁物理 DELETE users
- 依赖方向单向 api → application → domain ← infra；禁反向 import
- 禁 `println!`（logx ndjson 已装配，tracing 宏输出）；禁 runtime 建表

## 契约锚点

- 信封/错误码/分页：yarch 仓 contract/api/ 四件套
- 分页装配：`yarch_axum::persist::page_of`（D6：越界空页真实 total）
- yarch 规约：https://github.com/ydonghao/yarch（stacks/rust/spec.md）
```

`CLAUDE.md`：`@AGENTS.md`（单行）；`GEMINI.md`：`@AGENTS.md`（单行）。

- [ ] **Step 2: `tests/smoke.rs`（无 DB 冒烟：健康/信封/404/幂等回放）**

```rust
//! 冒烟（无 DB）：装配栈 + 信封 + 404 兜底 + 幂等回放。users 集成见 DATABASE_URL 场景。

use std::sync::Arc;
use std::time::Duration;

use axum::body::{to_bytes, Body};
use axum::extract::Request;
use axum::http::StatusCode;
use axum::response::Response;
use axum::Router;
use tower::ServiceExt;
use yarch_axum::store::{InMemoryIdempotencyStore, InMemoryRateLimiter};
use yarch_axum::{setup, IdemConfig, Options, RateConfig};

use {{crate_name}}::api::{self, AppState};
use {{crate_name}}::errors;

fn app() -> Router {
    errors::register_all();
    // 惰性连接：不触库（冒烟只打无 DB 端点）
    let pool = sqlx::postgres::PgPoolOptions::new()
        .connect_lazy("postgres://postgres:postgres@127.0.0.1:1/none?sslmode=disable")
        .expect("惰性连接构造");
    let state = AppState {
        users: Arc::new({{crate_name}}::infra::user_repo::UserRepoSqlx::new(pool)),
    };
    let router = Router::new()
        .route("/healthz", axum::routing::get(api::healthz))
        .route("/api/v1/echo", axum::routing::post(api::echo))
        .route(
            "/api/v1/users",
            axum::routing::get(api::users::list).post(api::users::create),
        )
        .fallback(api::not_found)
        .with_state(state);
    setup(
        router,
        Options {
            service: "smoke".to_string(),
            env: "local".to_string(),
            idempotency: Some(IdemConfig { store: Arc::new(InMemoryIdempotencyStore::new()) }),
            rate: Some(RateConfig {
                limiter: Arc::new(InMemoryRateLimiter::new()),
                limit: 60,
                window: Duration::from_secs(60),
            }),
        },
    )
}

async fn envelope(resp: Response) -> serde_json::Value {
    let bytes = to_bytes(resp.into_body(), 64 * 1024).await.unwrap();
    serde_json::from_slice(&bytes).unwrap()
}

#[tokio::test]
async fn healthz_envelope_code_zero_with_trace_id() {
    let resp = app().oneshot(Request::get("/healthz").body(Body::empty()).unwrap()).await.unwrap();
    assert_eq!(resp.status(), StatusCode::OK);
    assert!(resp.headers().get("x-trace-id").is_some(), "traceId 回显");
    let v = envelope(resp).await;
    assert_eq!(v["code"], 0);
    assert_eq!(v["message"], "成功");
    assert_eq!(v["data"]["status"], "up");
}

#[tokio::test]
async fn unknown_route_returns_1004_envelope_not_bare_404() {
    let resp = app().oneshot(Request::get("/nope").body(Body::empty()).unwrap()).await.unwrap();
    assert_eq!(resp.status(), StatusCode::NOT_FOUND);
    let v = envelope(resp).await;
    assert_eq!(v["code"], 1004);
}

#[tokio::test]
async fn idempotency_replays_same_response() {
    let app = app();
    let req = || {
        Request::builder()
            .method("POST")
            .uri("/api/v1/echo")
            .header("content-type", "application/json")
            .header("idempotency-key", "smoke-1")
            .body(Body::from(r#"{"name":"yarch"}"#.to_string()))
            .unwrap()
    };
    let (s1, v1) = {
        let r = app.clone().oneshot(req()).await.unwrap();
        (r.status(), envelope(r).await)
    };
    let (s2, v2) = {
        let r = app.oneshot(req()).await.unwrap();
        (r.status(), envelope(r).await)
    };
    assert_eq!(s1, StatusCode::OK);
    assert_eq!((s1, v1), (s2, v2), "同键同参回放");
    assert_eq!(v1["data"]["greeting"], "hello, yarch");
}
```

- [ ] **Step 3: main.rs 模块可见性修正（tests 引 crate 根）**

`main.rs` 的 `mod` 声明改为 `pub mod`（`pub mod api; pub mod errors; pub mod infra;` 等——集成测试经 crate 根访问）。可执行 crate 的模块须 pub 才能被 `tests/` 引用；`{{crate_name}}::api` 即 `main.rs` 的 pub mod。

- [ ] **Step 4: Commit**

```bash
cd /Users/yuandonghao/sidejob/sources/yarch && git add stacks/rust/templates/service
git commit -m "feat(rust): 模板 AGENTS 三件套（红线=spec 直译）+ 冒烟测试（healthz 信封/404→1004 兜底/幂等回放——无 DB 惰性连接）" -- stacks/rust/templates/service
```

---

### Task 6: 本地生成冒烟（cargo generate → build → test）

**Files:** 无新文件（生成物在 /tmp，不进仓）

- [ ] **Step 1: 生成**

```bash
cd /Users/yuandonghao/sidejob/sources/yarch
rm -rf /tmp/rust-smoke-svc
~/.cargo/bin/cargo-generate generate \
  --path stacks/rust/templates/service \
  --name rust-smoke-svc \
  --define "yarch_contract_dep={ path = '/Users/yuandonghao/sidejob/sources/yarch/stacks/rust/crates/yarch-contract' }" \
  --define "yarch_axum_dep={ path = '/Users/yuandonghao/sidejob/sources/yarch/stacks/rust/crates/yarch-axum' }" \
  -o /tmp
```

（`-o /tmp` 控制输出根；生成目录 `/tmp/rust-smoke-svc`。若 flag 不符以 `--help` 为准调整。）

- [ ] **Step 2: 零残留校验 + 构建 + 测试**

```bash
cd /tmp/rust-smoke-svc
! grep -rn "{{" --include="*.rs" --include="*.toml" --include="*.md" . | grep -v "^./target" && echo "零残留 OK"
cargo test 2>&1 | tail -8
cargo clippy --all-targets -- -D warnings 2>&1 | tail -1
```

Expected: 冒烟 3 项 PASS；clippy 零告警。编译报错按错修模板（模板即源码），修后重生成验证。

- [ ] **Step 3: 带 DB 集成冒烟（可选跑）**

```bash
cd /tmp/rust-smoke-svc
DATABASE_URL='postgres://yarch_itest:<专用测试库凭证>@yuandonghao-linux:5432/yarch_itest' YARCH_SKIP_MIGRATIONS=false cargo run &
sleep 3 && curl -s localhost:8080/healthz | grep '"code":0' && kill %1
```
（迁移落在共享 PG 的 postgres 库 public schema——users 表；冒烟后 `DROP TABLE IF EXISTS users` 清理。若决定不在共享库跑 main，本步可跳过——冒烟以 cargo test 为准。）

- [ ] **Step 4: 清理 + Commit（模板如经修复）**

```bash
rm -rf /tmp/rust-smoke-svc
cd /Users/yuandonghao/sidejob/sources/yarch && git add stacks/rust/templates/service
git commit -m "fix(rust): 模板冒烟修复收口（生成→test→clippy 全绿，零残留占位符）" -- stacks/rust/templates/service
```

---

### Task 7: CI template-smoke + 收口

**Files:**
- Modify: `.github/workflows/rust-stack.yml`（加 template-smoke job）
- Modify: `stacks/rust/PLAN.md`（第二批 2b 完成标记）
- Modify: `contract/README.md`（术语表业务异常行如落地 errors 口径——按实际改动）

- [ ] **Step 1: rust-stack.yml 加 job**

```yaml
  template-smoke:
    name: cargo generate 模板冒烟
    runs-on: ubuntu-latest
    needs: check
    steps:
      - uses: actions/checkout@v4
      - uses: dtolnay/rust-toolchain@stable
      - uses: taiki-e/install-action@cargo-generate
      - name: 生成（yarch 依赖以 path 覆盖 git tag 默认——发版前过渡，golang replace 对偶）
        run: |
          cargo generate --path stacks/rust/templates/service --name smoke-svc \
            --define "yarch_contract_dep={ path = '$GITHUB_WORKSPACE/stacks/rust/crates/yarch-contract' }" \
            --define "yarch_axum_dep={ path = '$GITHUB_WORKSPACE/stacks/rust/crates/yarch-axum' }" \
            -o /tmp
      - name: 零残留 + test + clippy
        run: |
          cd /tmp/smoke-svc
          ! grep -rn "{{" --include="*.rs" --include="*.toml" --include="*.md" . | grep -v "^./target"
          cargo test
          cargo clippy --all-targets -- -D warnings
```

（env 已在 check job 级设 RUSTUP_TOOLCHAIN；此 job 单 stable 无需。生成命令的非交互 flag 以本地实证为准同步。）

- [ ] **Step 2: 本地 YAML 语法检查 + PLAN/README 收口 + Commit**

```bash
ruby -ryaml -e 'YAML.load_file(".github/workflows/rust-stack.yml"); puts "yaml ok"'
```

`stacks/rust/PLAN.md` 批次二条改：`2b 已完成 2026-09-18：page.rs 分页契约 + persist 装配（运行时查询口径，集成测试 YARCH_PG_URL 门控）+ cargo-generate 模板（DDD 七包 users + AGENTS 三件套）+ 生成后冒烟（本地+CI template-smoke job）。批次二全部完成。`

```bash
cd /Users/yuandonghao/sidejob/sources/yarch && git add .github/workflows/rust-stack.yml stacks/rust/PLAN.md
git commit -m "ci(rust): template-smoke job（cargo generate → 零残留 → test → clippy）——批次二收口" -- .github/workflows/rust-stack.yml stacks/rust/PLAN.md
```

- [ ] **Step 3: push + 盯 CI 绿（用户授权后）**

---

## 收尾（本计划 DoD）

1. `cargo test --workspace` 全绿（2a 基线 35 + page 3 + persist 单测 1 + 集成 3（本地带 YARCH_PG_URL））；
2. 模板本地生成冒烟全绿：生成零残留 + `cargo test`（3 冒烟）+ clippy -D warnings；
3. CI rust-stack 四 job 绿（check 双档 + deny + **template-smoke**）；
4. pathspec 提交、术语表/PLAN 状态同步。

后续（批次三另册）：crates.io 发版流水线（rust-publish.yml，tag `stacks/rust/vX.Y.Z`）+ query! 宏/.sqlx 快照评估 + 统一 CLI 收编 `--lang rust`。
