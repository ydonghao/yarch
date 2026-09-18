# Rust 栈批次一（契约内核）Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 落地 yarch rust 栈第一批——cargo workspace + `yarch-contract` 契约内核（response/errcode/trace 三模块）+ 同源契约断言 + CI 四段门禁，本地与 CI 全绿。

**Architecture:** 依 [设计文档](../specs/2026-09-18-ycomp-component-platform-design.md) SP0 与 `stacks/rust/PLAN.md` 第一批：workspace 两 crate（`yarch-contract` 契约内核零框架 + `yarch-axum` 批次二占位空壳）；错误码表同源断言唯一权威 `contract/dist/error-codes.json`（P1 契约机器可读出口口径，对齐 golang/java/python/web 四栈 conformance）；CI 走 spec 九-1 四段门禁（fmt / clippy -D warnings / cargo-deny / test）+ R7 MSRV 1.80 双档矩阵。

**Tech Stack:** Rust 1.95.0（本地，已核实）、edition 2021、MSRV 1.80；依赖仅 `serde`（derive）+ `rand` 0.9（traceId 生成）+ `serde_json`（dev）；CI 用 dtolnay/rust-toolchain + Swatinem/rust-cache + taiki-e/install-action@cargo-deny。

**范围边界（不在本计划）：** registry 登记 ycomp/ycomp-console（批次 0，验证码批提交后另做）；CL1-CL6 组件库契约拍板；yarch-axum 中间件五件与模板（批次二）；组件库两包（SP1）。

## Global Constraints

- **pathspec 提交铁律**：本仓 index 有滞留 WIP 史，提交必须 `git commit -m "…" -- <paths>`，绝不裸 commit。
- **工作树污染隔离**：工作树现有另一会话未提交的验证码批改动（`contract/README.md`、`contract/api/error-codes.md`、`contract/dist/*`、`stacks/golang/errcode/*`、`stacks/java/**/GlobalErrorCode*`、`stacks/python/**/test_errcode.py`、`stacks/web/**/contract*`、未跟踪 `contract/api/captcha.md`）。本计划**只创建/修改本任务清单 Files 列出的文件**，不碰上述文件。
- **CI 依赖前置（Task 0 核实）**：conformance 断言按 14 码（error-codes v1.1，工作树现状）写内建表；**CI 读提交态 dist json**——验证码批未提交则 CI 必红。执行本计划前必须先由用户提交验证码批（见 Task 0）。
- 契约语义唯一权威 = `contract/api/` 四件套；实现与契约不一致 = bug（四栈同口径）。
- MSRV 1.80（`rust-version`），edition 2021；rustfmt + clippy `-D warnings` 全绿才可提交。
- 本地验证命令一律 `cd stacks/rust` 后执行（Bash 工具 cwd 漂移坑：单条命令内显式 cd 并 pwd 确认）。
- 根 `.gitignore` 已含 `target/`（已核实，无需追加）。

---

### Task 0: 前置确认（验证码批 + 工具链）

**Files:** 无（纯检查）

- [ ] **Step 1: 确认验证码批已提交**

Run: `git status --porcelain | grep -E "captcha|error-codes|GlobalErrorCode|test_errcode|contract.test|errcode_test|dist"`
Expected: 无输出（验证码批已落库）。**若有输出：停下，请用户提交该批后再继续**（dist json 的 14 码是 conformance 前提；勿代提交——非本计划工作）。

- [ ] **Step 2: 确认工具链**

Run: `rustc --version && cargo --version`
Expected: `rustc 1.95.0 …`、`cargo 1.95.0 …`（已核实存在；若环境变化缺 rust，`rustup toolchain install 1.95.0`）。

---

### Task 1: workspace 骨架 + 两 crate 空壳

**Files:**
- Create: `stacks/rust/Cargo.toml`
- Create: `stacks/rust/rust-toolchain.toml`
- Create: `stacks/rust/deny.toml`
- Create: `stacks/rust/crates/yarch-contract/Cargo.toml`
- Create: `stacks/rust/crates/yarch-contract/src/lib.rs`
- Create: `stacks/rust/crates/yarch-axum/Cargo.toml`
- Create: `stacks/rust/crates/yarch-axum/src/lib.rs`

**Interfaces:**
- Consumes: 无（首个任务）
- Produces: workspace 结构与包元数据；`yarch-contract` / `yarch-axum` 两个空 lib（后续任务在其中加模块）

- [ ] **Step 1: 写 workspace 根 `stacks/rust/Cargo.toml`**

```toml
# yarch rust 栈 workspace：契约内核（零框架）+ axum 装配（批次二施工）。
# 规约唯一权威：stacks/rust/spec.md（R1-R7）+ contract/api/ 四件套。
[workspace]
resolver = "2"
members = ["crates/yarch-contract", "crates/yarch-axum"]

[workspace.package]
version = "0.1.0"
edition = "2021"
license = "Apache-2.0"
repository = "https://github.com/ydonghao/yarch"
rust-version = "1.80"

[workspace.dependencies]
serde = { version = "1", features = ["derive"] }
serde_json = "1"
rand = "0.9"
```

- [ ] **Step 2: 写 `stacks/rust/rust-toolchain.toml`**

```toml
# 本地开发钉死 1.95.0（spec 一-1）；CI 矩阵 stable + MSRV 1.80 双档经
# dtolnay/rust-toolchain 的 RUSTUP_TOOLCHAIN 环境变量覆盖，互不干扰。
[toolchain]
channel = "1.95.0"
```

- [ ] **Step 3: 写 `stacks/rust/deny.toml`**

```toml
# cargo-deny（spec 一-3）：许可证白名单 + 禁重复大版本 + RUSTSEC 咨询。
[graph]
all-features = true

[licenses]
allow = [
  "MIT",
  "Apache-2.0",
  "Apache-2.0 WITH LLVM-exception",
  "BSD-2-Clause",
  "BSD-3-Clause",
  "ISC",
  "Unicode-3.0",
  "Zlib",
]
```

- [ ] **Step 4: 写 `stacks/rust/crates/yarch-contract/Cargo.toml`**

```toml
[package]
name = "yarch-contract"
description = "yarch 契约内核（Rust 方言）：REST 信封 / 错误码段位表 / traceId——零框架"
keywords = ["yarch", "contract", "rest", "error-code"]
version.workspace = true
edition.workspace = true
license.workspace = true
repository.workspace = true
rust-version.workspace = true

[dependencies]
serde = { workspace = true }
rand = { workspace = true }

[dev-dependencies]
serde_json = { workspace = true }
```

- [ ] **Step 5: 写 `crates/yarch-contract/src/lib.rs`（占位，模块随 Task 2-4 增）**

```rust
//! yarch-contract：Rust 契约内核（零框架）。
//!
//! 语义唯一权威 = contract/api/ 四件套；实现与契约不一致 = bug（四栈同口径）。
//! 约束对象：yarch 体系全部 Rust 云端业务服务（与 embedded/esp32 固件轨零共享 crate）。
```

- [ ] **Step 6: 写 `crates/yarch-axum/Cargo.toml` 与空壳 lib**

`crates/yarch-axum/Cargo.toml`：

```toml
[package]
name = "yarch-axum"
description = "yarch axum 装配层：中间件五件/提取器/分页/幂等（批次二施工，本批为空壳）"
keywords = ["yarch", "axum", "middleware"]
version.workspace = true
edition.workspace = true
license.workspace = true
repository.workspace = true
rust-version.workspace = true
```

`crates/yarch-axum/src/lib.rs`：

```rust
//! yarch-axum：axum 0.8 装配层——中间件五件（Trace/Recovery/AccessLog/Idempotency/Rate）、
//! sqlx 装配与提取器。批次二施工；本批为占位空壳。
```

- [ ] **Step 7: 验证空壳可构建、可测、机检绿**

Run:
```bash
cd /Users/yuandonghao/sidejob/sources/yarch/stacks/rust && pwd && \
  cargo build --workspace && cargo test --workspace && \
  cargo fmt --all -- --check && cargo clippy --all-targets -- -D warnings
```
Expected: 构建成功；`test result: ok. 0 passed`（两 crate 各 0 测试）；fmt 无 diff；clippy 零告警。

- [ ] **Step 8: Commit**

```bash
git add stacks/rust/Cargo.toml stacks/rust/rust-toolchain.toml stacks/rust/deny.toml \
  stacks/rust/crates/yarch-contract stacks/rust/crates/yarch-axum
git commit -m "feat(rust): 栈批次一开工——workspace 两 crate 空壳 + toolchain 钉 1.95.0 + deny.toml" -- \
  stacks/rust/Cargo.toml stacks/rust/rust-toolchain.toml stacks/rust/deny.toml \
  stacks/rust/crates/yarch-contract stacks/rust/crates/yarch-axum
```

---

### Task 2: errcode 模块（错误码段位表）

**Files:**
- Modify: `stacks/rust/crates/yarch-contract/src/lib.rs`（加 `pub mod errcode;`）
- Create: `stacks/rust/crates/yarch-contract/src/errcode.rs`（实现 + 内联单测）

**Interfaces:**
- Consumes: 无（内核首个模块）
- Produces（后续任务与业务工程依赖）:
  - `pub struct ErrCode { pub code: i32, pub key: &'static str, pub message: &'static str, pub http: u16 }`
  - 常量：`OK`（0）+ 14 个内建码常量（`INTERNAL_ERROR`…`CAPTCHA_INVALID`，UPPER_SNAKE 命名 = dist key 同形）
  - `pub const BUILTIN: &[ErrCode]`（全表，含 OK，供 conformance 对表）
  - `pub fn lookup(code: i32) -> ErrCode`（未登记回退 `INTERNAL_ERROR`）
  - `pub fn is_registered(code: i32) -> bool`
  - `pub fn register(code: i32, key: &'static str, message: &'static str, http: u16) -> Result<(), RegisterError>`（业务段 3xxx-8xxx；幂等重登放行；`RegisterError::{OutOfRange, HttpNotAllowed, Conflict}`）

- [ ] **Step 1: lib.rs 挂模块声明（先挂再写实现 → 编译红）**

在 `lib.rs` 文档注释后追加一行：

```rust
pub mod errcode;
```

- [ ] **Step 2: 创建 `errcode.rs` 骨架（类型/常量/测试全集 + 三函数桩）**

文件内容 = Step 4 完整文件**去掉三个函数体**、其余逐字一致（doc 注释、`ErrCode` + `new`、全部常量、`BUILTIN`、`BUSINESS_MIN/MAX`、`RegisterError`、`BUSINESS` 静态、`http_allowed`、完整 `mod tests`），三个函数先写桩：

```rust
pub fn lookup(code: i32) -> ErrCode {
    todo!()
}

pub fn is_registered(code: i32) -> bool {
    todo!()
}

pub fn register(
    code: i32,
    key: &'static str,
    message: &'static str,
    http: u16,
) -> Result<(), RegisterError> {
    todo!()
}
```

- [ ] **Step 3: 跑测试确认红**

Run: `cd /Users/yuandonghao/sidejob/sources/yarch/stacks/rust && cargo test -p yarch-contract`
Expected: 编译通过；`ok_constant`、`builtin_table_is_unique` 2 项 PASS，其余 4 项 panic（`not yet implemented`）。

- [ ] **Step 4: `errcode.rs` 完整权威内容（把三个 `todo!()` 桩换成真身）**

```rust
//! 错误码段位表（契约内核）。
//!
//! 唯一权威 = contract/api/error-codes.md（机器可读派生层 contract/dist/error-codes.json，
//! 同源断言见 tests/conformance.rs）。yarch 只拥有 0、1xxx、2xxx；3xxx-8xxx 由业务仓
//! 登记后方可使用（[`register`]）；9xxx 预留不得使用。

use std::collections::HashMap;
use std::sync::RwLock;

/// 错误码元数据（不可变值对象）。
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct ErrCode {
    /// 业务码。0 = 成功；非 0 见段位表
    pub code: i32,
    /// UPPER_SNAKE 标识（与 dist json 的 key 同形）
    pub key: &'static str,
    /// 默认中文文案（可直接作为前端降级文案）
    pub message: &'static str,
    /// 固定 HTTP 状态码映射（服务端不得自行发挥）
    pub http: u16,
}

impl ErrCode {
    const fn new(code: i32, key: &'static str, message: &'static str, http: u16) -> Self {
        Self { code, key, message, http }
    }
}

/// 成功（code = 0）
pub const OK: ErrCode = ErrCode::new(0, "OK", "成功", 200);

// 通用段 1xxx
pub const INTERNAL_ERROR: ErrCode = ErrCode::new(1000, "INTERNAL_ERROR", "内部错误", 500);
pub const INVALID_ARGUMENT: ErrCode = ErrCode::new(1001, "INVALID_ARGUMENT", "参数校验失败", 400);
pub const MALFORMED_BODY: ErrCode = ErrCode::new(1002, "MALFORMED_BODY", "请求体格式错误", 400);
pub const NOT_FOUND: ErrCode = ErrCode::new(1004, "NOT_FOUND", "资源不存在", 404);
pub const CONFLICT: ErrCode = ErrCode::new(1005, "CONFLICT", "资源冲突", 409);
pub const RATE_LIMITED: ErrCode = ErrCode::new(1006, "RATE_LIMITED", "触发限流", 429);
pub const IDEMPOTENCY_CONFLICT: ErrCode =
    ErrCode::new(1007, "IDEMPOTENCY_CONFLICT", "幂等冲突：重复提交", 409);
pub const UPSTREAM_TIMEOUT: ErrCode = ErrCode::new(1008, "UPSTREAM_TIMEOUT", "上游依赖超时", 504);
pub const UNAVAILABLE: ErrCode = ErrCode::new(1009, "UNAVAILABLE", "服务暂不可用", 503);

// 认证与权限段 2xxx
pub const UNAUTHORIZED: ErrCode = ErrCode::new(2001, "UNAUTHORIZED", "未认证", 401);
pub const CREDENTIALS_EXPIRED: ErrCode = ErrCode::new(2002, "CREDENTIALS_EXPIRED", "凭证已过期", 401);
pub const FORBIDDEN: ErrCode = ErrCode::new(2003, "FORBIDDEN", "权限不足", 403);
pub const ACCOUNT_DISABLED: ErrCode = ErrCode::new(2004, "ACCOUNT_DISABLED", "账号已禁用", 403);
pub const CAPTCHA_INVALID: ErrCode = ErrCode::new(2005, "CAPTCHA_INVALID", "验证码校验失败", 400);

/// 内建全表（0 + 1xxx + 2xxx；同源断言对表用）
pub const BUILTIN: &[ErrCode] = &[
    OK,
    INTERNAL_ERROR,
    INVALID_ARGUMENT,
    MALFORMED_BODY,
    NOT_FOUND,
    CONFLICT,
    RATE_LIMITED,
    IDEMPOTENCY_CONFLICT,
    UPSTREAM_TIMEOUT,
    UNAVAILABLE,
    UNAUTHORIZED,
    CREDENTIALS_EXPIRED,
    FORBIDDEN,
    ACCOUNT_DISABLED,
    CAPTCHA_INVALID,
];

/// 业务段边界：3xxx-8xxx（9xxx 预留不得使用）
const BUSINESS_MIN: i32 = 3000;
const BUSINESS_MAX: i32 = 8999;

/// 业务码登记失败（对齐 golang panic 语义；Rust 以 Result 承载，装配期调用方 `.expect` fail-fast）
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum RegisterError {
    /// 段位越界（仅 3xxx-8xxx 可登记）
    OutOfRange(i32),
    /// HTTP 状态码不在白名单
    HttpNotAllowed(u16),
    /// 同 code 已登记且元信息不同
    Conflict { code: i32, existing_key: &'static str },
}

static BUSINESS: RwLock<Option<HashMap<i32, ErrCode>>> = RwLock::new(None);

/// 登记业务码。幂等：同 code 同元信息重复登记放行；元信息冲突返回 [`RegisterError::Conflict`]。
pub fn register(
    code: i32,
    key: &'static str,
    message: &'static str,
    http: u16,
) -> Result<(), RegisterError> {
    if !(BUSINESS_MIN..=BUSINESS_MAX).contains(&code) {
        return Err(RegisterError::OutOfRange(code));
    }
    if !http_allowed(http) {
        return Err(RegisterError::HttpNotAllowed(http));
    }
    let mut guard = BUSINESS.write().expect("errcode business registry poisoned");
    let map = guard.get_or_insert_with(HashMap::new);
    match map.get(&code) {
        Some(existing)
            if existing.key == key && existing.message == message && existing.http == http =>
        {
            Ok(())
        }
        Some(existing) => Err(RegisterError::Conflict { code, existing_key: existing.key }),
        None => {
            map.insert(code, ErrCode::new(code, key, message, http));
            Ok(())
        }
    }
}

/// 查码：内建表 → 业务表 → 回退 INTERNAL_ERROR（未登记 code 视为实现缺陷，兜底内部错误语义）。
pub fn lookup(code: i32) -> ErrCode {
    if let Some(c) = BUILTIN.iter().find(|c| c.code == code) {
        return *c;
    }
    if let Ok(guard) = BUSINESS.read() {
        if let Some(c) = guard.as_ref().and_then(|m| m.get(&code)) {
            return *c;
        }
    }
    INTERNAL_ERROR
}

/// code 是否已登记（内建或业务段）
pub fn is_registered(code: i32) -> bool {
    if BUILTIN.iter().any(|c| c.code == code) {
        return true;
    }
    BUSINESS
        .read()
        .map(|g| g.as_ref().is_some_and(|m| m.contains_key(&code)))
        .unwrap_or(false)
}

/// rest-conventions.md 业务 API 状态码白名单（对齐 golang httpAllowed）
fn http_allowed(status: u16) -> bool {
    matches!(status, 200 | 201 | 400 | 401 | 403 | 404 | 409 | 429 | 500 | 503 | 504)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn ok_constant() {
        assert_eq!((OK.code, OK.key, OK.message, OK.http), (0, "OK", "成功", 200));
    }

    #[test]
    fn builtin_table_is_unique() {
        let mut codes: Vec<i32> = BUILTIN.iter().map(|c| c.code).collect();
        let total = codes.len();
        codes.sort_unstable();
        codes.dedup();
        assert_eq!(codes.len(), total, "内建码表存在重复 code");
        let mut keys: Vec<&str> = BUILTIN.iter().map(|c| c.key).collect();
        let ktotal = keys.len();
        keys.sort_unstable();
        keys.dedup();
        assert_eq!(keys.len(), ktotal, "内建码表存在重复 key");
    }

    #[test]
    fn unregistered_falls_back_to_internal_error() {
        assert!(!is_registered(9999));
        let c = lookup(9999);
        assert_eq!((c.code, c.message, c.http), (1000, "内部错误", 500));
    }

    #[test]
    fn register_business_code() {
        // 测试共享全局表：各测试用不同 code 避免相互污染
        assert!(register(3998, "DEMO_BUSY", "示例繁忙", 409).is_ok());
        // 幂等重登放行
        assert!(register(3998, "DEMO_BUSY", "示例繁忙", 409).is_ok());
        // 元信息冲突拒绝
        assert_eq!(
            register(3998, "OTHER", "其他", 409),
            Err(RegisterError::Conflict { code: 3998, existing_key: "DEMO_BUSY" })
        );
        let c = lookup(3998);
        assert_eq!((c.code, c.key, c.message, c.http), (3998, "DEMO_BUSY", "示例繁忙", 409));
        assert!(is_registered(3998));
    }

    #[test]
    fn register_rejects_out_of_range() {
        assert_eq!(register(2999, "X", "x", 400), Err(RegisterError::OutOfRange(2999)));
        assert_eq!(register(9000, "X", "x", 400), Err(RegisterError::OutOfRange(9000)));
    }

    #[test]
    fn register_rejects_http_outside_whitelist() {
        assert_eq!(register(3997, "X", "x", 204), Err(RegisterError::HttpNotAllowed(204)));
    }
}
```

- [ ] **Step 5: 跑测试**

Run: `cd /Users/yuandonghao/sidejob/sources/yarch/stacks/rust && cargo test -p yarch-contract`
Expected: `errcode::tests` 6 项全 PASS。

- [ ] **Step 6: 机检**

Run: `cd /Users/yuandonghao/sidejob/sources/yarch/stacks/rust && cargo fmt --all && cargo clippy --all-targets -- -D warnings`
Expected: fmt 整形无输出；clippy 零告警。

- [ ] **Step 7: Commit**

```bash
git add stacks/rust/crates/yarch-contract
git commit -m "feat(rust): 契约内核 errcode——14 码内建表 + 业务段 3xxx-8xxx 登记（对齐 golang 语义：未登记回退/幂等重登/HTTP 白名单）" -- stacks/rust/crates/yarch-contract
```

---

### Task 3: response 模块（REST 信封）

**Files:**
- Modify: `stacks/rust/crates/yarch-contract/src/lib.rs`（加 `pub mod response;`）
- Create: `stacks/rust/crates/yarch-contract/src/response.rs`

**Interfaces:**
- Consumes: `errcode::OK`、`errcode::lookup`
- Produces:
  - `pub struct RestResponse<T> { pub code: i32, pub message: String, pub data: Option<T>, pub trace_id: String }`（serde 序列化 camelCase `traceId`；字段顺序 code→message→data→traceId）
  - `RestResponse::ok(data: T, trace_id) / ok_empty(trace_id) / error(code: i32, trace_id) / error_with_message(code, message, trace_id)`——错误路径 `data` 恒 `None`

- [ ] **Step 1: lib.rs 挂模块**

```rust
pub mod response;
```

- [ ] **Step 2: 创建 `response.rs` 骨架并确认红**

文件内容 = Step 3 完整文件**去掉四个构造函数体**、其余逐字一致（doc 注释、`RestResponse` 结构 + serde 派生、完整 `mod tests`），四个构造函数先写桩：

```rust
pub fn ok(data: T, trace_id: impl Into<String>) -> Self {
    todo!()
}

pub fn ok_empty(trace_id: impl Into<String>) -> Self {
    todo!()
}

pub fn error(code: i32, trace_id: impl Into<String>) -> Self {
    todo!()
}

pub fn error_with_message(code: i32, message: impl Into<String>, trace_id: impl Into<String>) -> Self {
    todo!()
}
```

Run: `cd /Users/yuandonghao/sidejob/sources/yarch/stacks/rust && cargo test -p yarch-contract response`
Expected: 编译通过；response 6 项全部 panic（`not yet implemented`）。

- [ ] **Step 3: `response.rs` 完整权威内容（把四个 `todo!()` 桩换成真身）**

```rust
//! REST 信封（契约内核）：四字段 camelCase，`code != 0` 时 `data` 必须为 `null`。
//!
//! 语义唯一权威 = contract/api/rest-response.md。

use serde::{Deserialize, Serialize};

use crate::errcode;

/// 响应信封。字段名 camelCase（traceId 经 serde rename），任何栈不得增删改名。
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct RestResponse<T> {
    /// 业务码。0 = 成功；非 0 见 errcode 段位表
    pub code: i32,
    /// 人类可读文案；不得携带堆栈/内部细节
    pub message: String,
    /// 业务负载。code != 0 时必须为 null
    pub data: Option<T>,
    /// 追踪 ID，恒等于响应头 X-Trace-Id；取不到时为空串
    #[serde(rename = "traceId")]
    pub trace_id: String,
}

impl<T> RestResponse<T> {
    /// 成功且带负载
    pub fn ok(data: T, trace_id: impl Into<String>) -> Self {
        Self {
            code: errcode::OK.code,
            message: errcode::OK.message.to_string(),
            data: Some(data),
            trace_id: trace_id.into(),
        }
    }

    /// 成功且无负载（data = null）
    pub fn ok_empty(trace_id: impl Into<String>) -> Self {
        Self { code: errcode::OK.code, message: errcode::OK.message.to_string(), data: None, trace_id: trace_id.into() }
    }

    /// 业务失败：message 取码表默认文案；data 恒 None。
    /// 未登记 code 回退 INTERNAL_ERROR（整个信封按 1000 语义出，防止未定义码泄漏）。
    pub fn error(code: i32, trace_id: impl Into<String>) -> Self {
        let c = errcode::lookup(code);
        Self { code: c.code, message: c.message.to_string(), data: None, trace_id: trace_id.into() }
    }

    /// 业务失败 + 覆盖文案（按「默认文案：细节」规则由调用方拼装）
    pub fn error_with_message(code: i32, message: impl Into<String>, trace_id: impl Into<String>) -> Self {
        let c = errcode::lookup(code);
        Self { code: c.code, message: message.into(), data: None, trace_id: trace_id.into() }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[derive(Serialize, Deserialize, PartialEq, Debug)]
    struct Payload {
        value: i32,
    }

    #[test]
    fn ok_serializes_to_exact_envelope() {
        let resp = RestResponse::ok(Payload { value: 1 }, "0af7651916cd43dd8448eb211c80319c");
        let json = serde_json::to_string(&resp).unwrap();
        assert_eq!(
            json,
            r#"{"code":0,"message":"成功","data":{"value":1},"traceId":"0af7651916cd43dd8448eb211c80319c"}"#
        );
    }

    #[test]
    fn error_serializes_data_null() {
        let resp: RestResponse<Payload> = RestResponse::error(1001, "t");
        let json = serde_json::to_string(&resp).unwrap();
        assert_eq!(json, r#"{"code":1001,"message":"参数校验失败","data":null,"traceId":"t"}"#);
    }

    #[test]
    fn ok_empty_keeps_data_null() {
        let resp: RestResponse<Payload> = RestResponse::ok_empty("t");
        assert_eq!(resp.code, 0);
        assert!(resp.data.is_none());
        let json = serde_json::to_string(&resp).unwrap();
        assert_eq!(json, r#"{"code":0,"message":"成功","data":null,"traceId":"t"}"#);
    }

    #[test]
    fn unregistered_code_falls_back_to_internal_error() {
        let resp: RestResponse<Payload> = RestResponse::error(9999, "t");
        assert_eq!(resp.code, 1000);
        assert_eq!(resp.message, "内部错误");
    }

    #[test]
    fn error_with_message_overrides() {
        let resp: RestResponse<Payload> = RestResponse::error_with_message(1001, "参数校验失败：pageSize 必须 ≤ 100", "t");
        let json = serde_json::to_string(&resp).unwrap();
        assert_eq!(
            json,
            r#"{"code":1001,"message":"参数校验失败：pageSize 必须 ≤ 100","data":null,"traceId":"t"}"#
        );
    }

    #[test]
    fn deserialize_round_trip() {
        let resp = RestResponse::ok(Payload { value: 7 }, "tid");
        let json = serde_json::to_string(&resp).unwrap();
        let back: RestResponse<Payload> = serde_json::from_str(&json).unwrap();
        assert_eq!(back, resp);
    }
}
```

- [ ] **Step 4: 跑测试**

Run: `cd /Users/yuandonghao/sidejob/sources/yarch/stacks/rust && cargo test -p yarch-contract`
Expected: errcode 6 + response 6 全 PASS。

- [ ] **Step 5: 机检 + Commit**

Run: `cd /Users/yuandonghao/sidejob/sources/yarch/stacks/rust && cargo fmt --all && cargo clippy --all-targets -- -D warnings`

```bash
git add stacks/rust/crates/yarch-contract
git commit -m "feat(rust): 契约内核 response——RestResponse 四字段信封（camelCase traceId/错误路径 data 恒 null/未登记码回退 1000）" -- stacks/rust/crates/yarch-contract
```

---

### Task 4: trace 模块（traceId + traceparent）

**Files:**
- Modify: `stacks/rust/crates/yarch-contract/src/lib.rs`（加 `pub mod trace;`）
- Create: `stacks/rust/crates/yarch-contract/src/trace.rs`

**Interfaces:**
- Consumes: 无
- Produces:
  - `pub struct TraceId(String)`：`TraceId::generate()`（32 hex 非全零）、`as_str()`、`Display`、`TraceId::is_valid(s: &str) -> bool`
  - `pub struct TraceParent { pub trace_id: String, pub parent_span_id: String }`
  - `pub fn parse_traceparent(header: &str) -> Option<TraceParent>`（版本 00；非法输入 None）

- [ ] **Step 1: lib.rs 挂模块**

```rust
pub mod trace;
```

- [ ] **Step 2: 创建 `trace.rs` 骨架并确认红**

文件内容 = Step 3 完整文件**去掉三个函数体**、其余逐字一致（doc 注释、`TraceId` 结构 + `Display`、`TraceParent`、`is_all_zero`、完整 `mod tests`），三个函数先写桩：

```rust
impl TraceId {
    pub fn generate() -> Self {
        todo!()
    }

    pub fn as_str(&self) -> &str {
        &self.0
    }

    pub fn is_valid(s: &str) -> bool {
        todo!()
    }
}

pub fn parse_traceparent(header: &str) -> Option<TraceParent> {
    todo!()
}
```

Run: `cd /Users/yuandonghao/sidejob/sources/yarch/stacks/rust && cargo test -p yarch-contract trace`
Expected: 编译通过；trace 5 项全部 panic（`not yet implemented`）。

- [ ] **Step 3: `trace.rs` 完整权威内容（把三个 `todo!()` 桩换成真身）**

完整权威内容：

```rust
//! traceId 语义（契约内核）：32 hex 生成与 W3C traceparent 解析。
//!
//! 语义唯一权威 = contract/api/logging-trace.md（W3C traceparent 传播）、
//! contract/api/rest-response.md（traceId 恒等于响应头 X-Trace-Id）。

use rand::Rng;

/// 32 位小写十六进制 traceId（16 字节）。W3C 规定不得全零。
#[derive(Debug, Clone, PartialEq, Eq, Hash)]
pub struct TraceId(String);

impl TraceId {
    /// 生成随机 traceId（W3C：非全零 32 hex）
    pub fn generate() -> Self {
        let v: u128 = rand::rng().random();
        Self(format!("{v:032x}"))
    }

    pub fn as_str(&self) -> &str {
        &self.0
    }

    /// 校验 32 位小写十六进制（W3C 规定小写，大小写敏感）
    pub fn is_valid(s: &str) -> bool {
        s.len() == 32 && s.bytes().all(|b| matches!(b, b'0'..=b'9' | b'a'..=b'f'))
    }
}

impl std::fmt::Display for TraceId {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        f.write_str(&self.0)
    }
}

/// W3C traceparent 解析结果：`00-{trace-id}-{parent-id}-{flags}`
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct TraceParent {
    pub trace_id: String,
    pub parent_span_id: String,
}

/// 解析 W3C traceparent 头（版本 00；trace-id 32 hex 非全零；parent-id 16 hex 非全零；flags 2 hex）。
/// 非法输入返回 None，由调用方决定重新生成或透传。
pub fn parse_traceparent(header: &str) -> Option<TraceParent> {
    let parts: Vec<&str> = header.trim().split('-').collect();
    if parts.len() != 4 || parts[0] != "00" {
        return None;
    }
    let (trace_id, parent_span_id, flags) = (parts[1], parts[2], parts[3]);
    if !TraceId::is_valid(trace_id) || is_all_zero(trace_id) {
        return None;
    }
    if parent_span_id.len() != 16
        || !parent_span_id.bytes().all(|b| matches!(b, b'0'..=b'9' | b'a'..=b'f'))
        || is_all_zero(parent_span_id)
    {
        return None;
    }
    if flags.len() != 2 || !flags.bytes().all(|b| matches!(b, b'0'..=b'9' | b'a'..=b'f')) {
        return None;
    }
    Some(TraceParent { trace_id: trace_id.to_string(), parent_span_id: parent_span_id.to_string() })
}

fn is_all_zero(hex: &str) -> bool {
    hex.bytes().all(|b| b == b'0')
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn generated_trace_id_is_32_lowercase_hex() {
        let tid = TraceId::generate();
        assert!(TraceId::is_valid(tid.as_str()), "traceId 形状非法: {tid}");
        assert_ne!(tid.as_str(), "00000000000000000000000000000000");
    }

    #[test]
    fn generated_trace_ids_differ() {
        assert_ne!(TraceId::generate(), TraceId::generate());
    }

    #[test]
    fn is_valid_rejects_bad_shapes() {
        assert!(!TraceId::is_valid(""));
        assert!(!TraceId::is_valid("abc"));
        assert!(!TraceId::is_valid("0AF7651916CD43DD8448EB211C80319C")); // 大写拒绝
        assert!(!TraceId::is_valid("0af7651916cd43dd8448eb211c80319")); // 31 位
        assert!(TraceId::is_valid("0af7651916cd43dd8448eb211c80319c"));
    }

    #[test]
    fn parses_valid_traceparent() {
        let tp = parse_traceparent("00-0af7651916cd43dd8448eb211c80319c-00f067aa0ba902b7-01").unwrap();
        assert_eq!(tp.trace_id, "0af7651916cd43dd8448eb211c80319c");
        assert_eq!(tp.parent_span_id, "00f067aa0ba902b7");
        // 首尾空白容忍
        assert!(parse_traceparent("  00-0af7651916cd43dd8448eb211c80319c-00f067aa0ba902b7-01  ").is_some());
    }

    #[test]
    fn rejects_invalid_traceparent() {
        let cases = [
            "",                                                       // 空
            "0af7651916cd43dd8448eb211c80319c",                       // 非 traceparent 形态
            "ff-0af7651916cd43dd8448eb211c80319c-00f067aa0ba902b7-01", // 非 00 版本
            "00-00000000000000000000000000000000-00f067aa0ba902b7-01", // 全零 trace-id
            "00-0af7651916cd43dd8448eb211c80319-00f067aa0ba902b7-01",  // 31 位 trace-id
            "00-0af7651916cd43dd8448eb211c80319c-0000000000000000-01", // 全零 parent-id
            "00-0af7651916cd43dd8448eb211c80319c-00f067aa0ba902b7-1",  // 1 位 flags
            "00-0af7651916cd43dd8448eb211c80319c-00f067aa0ba902b7-xyz", // flags 非法字符
        ];
        for case in cases {
            assert!(parse_traceparent(case).is_none(), "应拒绝: {case}");
        }
    }
}
```

- [ ] **Step 4: 跑测试 + 机检**

Run: `cd /Users/yuandonghao/sidejob/sources/yarch/stacks/rust && cargo test -p yarch-contract && cargo fmt --all && cargo clippy --all-targets -- -D warnings`
Expected: errcode 6 + response 6 + trace 5 全 PASS；机检绿。

- [ ] **Step 5: Commit**

```bash
git add stacks/rust/crates/yarch-contract
git commit -m "feat(rust): 契约内核 trace——TraceId 32 hex 生成 + W3C traceparent 解析（版本 00/非全零/小写严格）" -- stacks/rust/crates/yarch-contract
```

---

### Task 5: 同源 conformance 断言（tests/conformance.rs）

**Files:**
- Create: `stacks/rust/crates/yarch-contract/tests/conformance.rs`

**Interfaces:**
- Consumes: `errcode::BUILTIN`、`errcode::OK`、`errcode::lookup`、`errcode::is_registered`
- Produces: 无（纯测试；CI 必跑、独立环境缺 dist 优雅跳过）

- [ ] **Step 1: 写 `tests/conformance.rs`**

```rust
//! 同源断言：内建码表的唯一机器可读权威 = contract/dist/error-codes.json
//! （由 contract/api/error-codes.md 派生，contract-dist CI 拒双向漂移）。
//! dist 不在场（消费方独立测试环境）则跳过；仓内 CI 必跑（P1 契约机器可读出口口径，
//! 与 golang/java/python/web 四栈 conformance 同表）。

use serde::Deserialize;
use yarch_contract::errcode;

#[derive(Deserialize)]
struct DistRow {
    code: i32,
    key: String,
    message: String,
    http: u16,
}

#[derive(Deserialize)]
struct DistSuccess {
    code: i32,
    message: String,
}

#[derive(Deserialize)]
struct Dist {
    success: DistSuccess,
    codes: Vec<DistRow>,
}

fn load_dist() -> Option<Dist> {
    let raw = std::fs::read_to_string("../../../../contract/dist/error-codes.json").ok()?;
    Some(serde_json::from_str(&raw).expect("dist json 解析失败"))
}

#[test]
fn ok_matches_dist_success() {
    let Some(dist) = load_dist() else {
        println!("contract dist json 不在场，跳过同源断言");
        return;
    };
    assert_eq!(
        (errcode::OK.code, errcode::OK.message),
        (dist.success.code, dist.success.message.as_str())
    );
}

#[test]
fn builtin_table_matches_dist_codes() {
    let Some(dist) = load_dist() else {
        println!("contract dist json 不在场，跳过同源断言");
        return;
    };
    // 双向对表（不硬编码码数——加码只改 md + dist + 内建表三处，本测试零改）：
    // dist 每行都在内建表中且元数据全等；内建表除 OK 外无多余行
    let builtin_rows = errcode::BUILTIN.iter().filter(|c| c.code != 0).count();
    assert_eq!(dist.codes.len(), builtin_rows, "内建码表与 dist 行数不一致（漂移）");
    for row in &dist.codes {
        let c = errcode::lookup(row.code);
        assert!(errcode::is_registered(row.code), "code {} 在内建表中缺失", row.code);
        assert_eq!(
            (c.code, c.key, c.message, c.http),
            (row.code, row.key.as_str(), row.message.as_str(), row.http),
            "code {} 元数据与 dist 不一致",
            row.code
        );
    }
}
```

- [ ] **Step 2: 跑测试（本地 dist 在场，必跑真断言）**

Run: `cd /Users/yuandonghao/sidejob/sources/yarch/stacks/rust && cargo test -p yarch-contract --test conformance`
Expected: 2 项 PASS（本地工作树 dist = 14 码 + success）。

- [ ] **Step 3: 全量 + 机检 + Commit**

Run: `cd /Users/yuandonghao/sidejob/sources/yarch/stacks/rust && cargo test --workspace && cargo fmt --all && cargo clippy --all-targets -- -D warnings`

```bash
git add stacks/rust/crates/yarch-contract/tests/conformance.rs
git commit -m "test(rust): 契约内核 conformance 同源断言——14 码对表 contract/dist/error-codes.json（缺场优雅跳过，双向零硬编码行数）" -- stacks/rust/crates/yarch-contract/tests/conformance.rs
```

---

### Task 6: CI workflow（rust-stack.yml 四段门禁 + MSRV 双档）

**Files:**
- Create: `.github/workflows/rust-stack.yml`
- Modify: `stacks/rust/PLAN.md`（五、施工批次——第一批标记完成）

**Interfaces:**
- Consumes: Task 1-5 全部产物
- Produces: CI 门禁 `rust-stack`（push main/dev + PR，paths 过滤 `stacks/rust/**`）

- [ ] **Step 1: 写 `.github/workflows/rust-stack.yml`**

```yaml
name: rust-stack

# rust 栈 CI：四段门禁（fmt / clippy -D warnings / cargo-deny / test，spec 九-1）+ MSRV 双档（R7）。
# 模板生成后冒烟（spec 九-3）待批次二模板落地后追加 job。
# conformance 测试读仓内 contract/dist/error-codes.json（提交态在场，contract-dist 门拒漂移）。

on:
  push:
    branches: [main, dev]
    paths: ['stacks/rust/**', '.github/workflows/rust-stack.yml']
  pull_request:
    paths: ['stacks/rust/**', '.github/workflows/rust-stack.yml']

jobs:
  check:
    name: Rust ${{ matrix.toolchain }} · 四段门禁
    strategy:
      fail-fast: false
      matrix:
        # R7：stable + MSRV 1.80 双档（dtolnay 动作以 RUSTUP_TOOLCHAIN 覆盖 rust-toolchain.toml）
        toolchain: [stable, '1.80']
    runs-on: ubuntu-latest
    defaults:
      run:
        working-directory: stacks/rust
    steps:
      - uses: actions/checkout@v4
      - uses: dtolnay/rust-toolchain@master
        with:
          toolchain: ${{ matrix.toolchain }}
          components: rustfmt, clippy
      - uses: Swatinem/rust-cache@v2
        with:
          workspaces: stacks/rust/Cargo.toml
      - run: cargo fmt --all -- --check
      - run: cargo clippy --all-targets -- -D warnings
      - run: cargo test --workspace

  deny:
    name: cargo-deny · 依赖审计
    runs-on: ubuntu-latest
    defaults:
      run:
        working-directory: stacks/rust
    steps:
      - uses: actions/checkout@v4
      - uses: dtolnay/rust-toolchain@stable
      - uses: taiki-e/install-action@cargo-deny
      - run: cargo deny check
```

- [ ] **Step 2: 本地 YAML 语法机检（仓有 workflow YAML 暗雷史）**

Run: `ruby -ryaml -e 'YAML.load_file("/Users/yuandonghao/sidejob/sources/yarch/.github/workflows/rust-stack.yml"); puts "yaml ok"'`
Expected: `yaml ok`。

- [ ] **Step 3: PLAN.md 批次一标记完成**

`stacks/rust/PLAN.md` 五、施工批次第 1 条改为（追加状态）：

```markdown
1. **第一批（已完成 2026-09-18）**：spec.md 成文 + PLAN.md 落位 + workspace 骨架（两 crate + 契约内核 response/errcode/trace 三模块 + 同源 conformance 断言读 contract/dist）——本地 cargo test/fmt/clippy 绿，CI rust-stack.yml 四段门禁 + MSRV 双档。
```

- [ ] **Step 4: Commit**

```bash
git add .github/workflows/rust-stack.yml stacks/rust/PLAN.md
git commit -m "ci(rust): rust-stack 四段门禁（fmt/clippy/deny/test + stable/1.80 双档矩阵）——批次一收口" -- .github/workflows/rust-stack.yml stacks/rust/PLAN.md
```

- [ ] **Step 5: 触发 CI 验证（用户授权 push 后）**

Run: `git push origin dev`（经用户确认后执行；或用户自行 push）
Expected: GitHub Actions `rust-stack` workflow 两 job 全绿；1.80 档若因依赖 MSRV 红则按红线预案处理（见下）。

**已知风险与预案**：1.80 档可能因 `serde_json`/`rand` 新版本 MSRV 高于 1.80 而红。预案：在 `[workspace.dependencies]` 钉下界（如 `serde_json = "~1.0.128"`、`rand = "~0.9.1"`）后重试；仍红则在 Cargo.toml 加 `[workspace.package]` 之外单独说明并上报用户决策（R7 的 MSRV 是拍板项，不擅自降档）。

---

## 收尾（本计划 DoD）

1. `cd stacks/rust && cargo test --workspace` 全绿（errcode 6 + response 6 + trace 5 + conformance 2 = 19 项）；
2. `cargo fmt --all -- --check` + `cargo clippy --all-targets -- -D warnings` 绿；
3. CI `rust-stack` 两 job 绿（含 MSRV 1.80 档）；
4. 全部提交走 pathspec，工作树验证码批文件零触碰。

后续计划（依本批真实 crate 形状再写）：批次二（yarch-axum 中间件五件 + sqlx 装配 + cargo-generate 模板，需先 `cargo install cargo-generate`）；批次 0 registry 登记 ycomp（验证码批提交后）；SP1 组件库（CL1-CL6 拍板先行）。
