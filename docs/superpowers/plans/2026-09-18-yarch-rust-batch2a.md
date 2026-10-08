# Rust 栈批次 2a（yarch-axum 中间件五件）Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 落地 `yarch-axum` 装配层核心——ndjson logx + 中间件五件（AccessLog/Trace/Recovery/Idempotency/RateLimit）+ 存储 trait 与 InMemory 实现 + `setup()` 装配 + 组合语义测试（逐条翻译 python `test_middleware_composition.py` 四例）。

**Architecture:** 依 `stacks/rust/PLAN.md` 第二批与 spec 六-2 组合语义：axum `from_fn`/`from_fn_with_state` 中间件；traceId 用 `tokio::task_local` 贯穿（对偶 python contextvars）；组合顺序外→内 **AccessLog > Trace > Recovery > Rate > Idem**（Rate 在 Idem 外层——429 不得被幂等层捕获落库）；日志走 `tracing` 宏 + 自定义 `FormatEvent` 输出契约行协议（ts/level/service/env/traceId/logger/msg + kv 平铺）。批次二拆 2a（本计划，纯 axum 装配）与 2b（sqlx 装配 + cargo-generate 模板 + 生成冒烟，另册）。

**Tech Stack:** axum 0.8 + tokio 1 + tower（dev，oneshot）+ futures-util（catch_unwind）+ sha2 + serde/serde_json + tracing/tracing-subscriber 0.3 + yarch-contract（path 依赖）。InMemory 存储为首批实现（ycomp 平台不用 Redis，Redis 实现触发式——首个需要跨实例幂等/限流的服务立项时）。

**范围边界（不在本计划）：** sqlx 装配与分页（2b）；cargo-generate 模板（2b）；Redis 存储（触发式）；signed_api（EP5，触发式）。

## Global Constraints

- **pathspec 提交铁律**（同批次一）；本地命令 `cd /Users/yuandonghao/sidejob/sources/yarch/stacks/rust` 后执行。
- 机检三件全绿才可提交：`cargo fmt --all -- --check` / `cargo clippy --all-targets -- -D warnings` / `cargo test --workspace`。
- 契约语义唯一权威 = `contract/api/`；中间件行为对齐 python 实现（`stacks/python/.../middleware/`），组合语义对齐 `test_middleware_composition.py`。
- MSRV 1.80：不使用 1.80 之后的稳定 API（let-else 1.65 ✓、is_some_and 1.70 ✓、task_local ✓）。
- panic 安全：Idempotency 捕获 unwind → release → `resume_unwind`；Recovery 捕获 unwind → 500 信封（两者都在 async 上下文，用 `AssertUnwindSafe + catch_unwind`）。
- axum `.layer()` 语义：后调用者包外层（`r.layer(idem).layer(rate)` → rate 在 idem 外）——组合测试是最终裁判，语义不对就翻序。

---

### Task 1: logx——task_local traceId + 契约 ndjson FormatEvent

**Files:**
- Create: `stacks/rust/crates/yarch-axum/src/logx.rs`
- Modify: `stacks/rust/crates/yarch-axum/src/lib.rs`（`pub mod logx;`，替换批次一占位注释）
- Modify: `stacks/rust/crates/yarch-axum/Cargo.toml`（依赖）
- Test: `stacks/rust/crates/yarch-axum/src/logx.rs` 内联 `mod tests`

**Interfaces:**
- Produces:
  - `logx::current_trace() -> String`（task_local 读，未绑定返回 `""`）
  - `logx::scope_trace<F: Future>(trace_id: String, fut: F) -> impl Future<Output = F::Output>`（task_local 作用域）
  - `logx::init(service: &str, env: &str)`（全局 subscriber，幂等：已设置则跳过）
  - `logx::init_for_test(service, env, writer) -> impl Subscriber`（测试用捕获 writer 构造 subscriber，不注册全局）
  - 事件宏即 `tracing::info!` 等（target=模块路径自动成 logger）；FormatEvent 输出 `{ts, level, service, env, traceId, logger, msg, ...kv}` 平铺单行 JSON。

- [ ] **Step 1: Cargo.toml 依赖**

`crates/yarch-axum/Cargo.toml` 追加：

```toml
[dependencies]
yarch-contract = { path = "../yarch-contract" }
axum = "0.8"
tokio = { version = "1", features = ["macros", "rt", "sync", "time"] }
futures-util = "0.3"
sha2 = "0.10"
serde = { workspace = true }
serde_json = { workspace = true }
tracing = "0.1"
tracing-subscriber = { version = "0.3", features = ["fmt", "std"] }

[dev-dependencies]
tower = { version = "0.5", features = ["util"] }
serde_json = { workspace = true }
```

- [ ] **Step 2: 写 `logx.rs`（完整权威内容）**

```rust
//! logx：契约 ndjson 行协议（logging-trace.md v1.0/v1.1）+ task_local traceId 贯穿。
//!
//! 行协议字段序：ts, level, service, env, traceId, logger, msg, ...kv（平铺）。
//! 事件经 tracing 宏发出（target = 模块路径 = logger 字段），由 ContractFormat 渲染成行。
//! 对偶 python logx（structlog）/ golang logx（slog）。

use std::fmt::Write as _;
use std::io;
use std::sync::{Arc, Mutex, OnceLock};
use std::time::{SystemTime, UNIX_EPOCH};

use tracing::{
    field::{Field, Visit},
    Event, Level, Subscriber,
};
use tracing_subscriber::fmt::{format::Writer, FmtContext, FormatEvent, FormatFields};
use tracing_subscriber::registry::LookupSpan;

tokio::task_local! {
    static TRACE_ID: String;
}

/// 当前 traceId（task_local 未绑定时返回空串——对偶 python contextvar 默认 ""）。
pub fn current_trace() -> String {
    TRACE_ID.try_with(|t| t.clone()).unwrap_or_default()
}

/// 在 traceId 作用域内运行 future（对偶 python bind_trace/reset_trace 对）。
pub async fn scope_trace<F: Future>(trace_id: String, fut: F) -> F::Output {
    TRACE_ID.scope(trace_id, fut).await
}

static SERVICE: OnceLock<String> = OnceLock::new();
static ENV: OnceLock<String> = OnceLock::new();

/// 契约行协议渲染器（tracing-subscriber FormatEvent 承接，spec 三-6）。
struct ContractFormat;

struct FieldsVisitor {
    msg: String,
    kv: Vec<(String, String)>,
    explicit_trace_id: Option<String>,
}

impl Visit for FieldsVisitor {
    fn record_debug(&mut self, field: &Field, value: &dyn fmt::Debug) {
        let text = format!("{value:?}");
        self.record_str_like(field.name(), text);
    }
    fn record_str(&mut self, field: &Field, value: &str) {
        self.record_str_like(field.name(), value.to_string());
    }
    fn record_i64(&mut self, field: &Field, value: i64) {
        self.record_str_like(field.name(), value.to_string());
    }
    fn record_u64(&mut self, field: &Field, value: u64) {
        self.record_str_like(field.name(), value.to_string());
    }
    fn record_bool(&mut self, field: &Field, value: bool) {
        self.record_str_like(field.name(), value.to_string());
    }
    fn record_f64(&mut self, field: &Field, value: f64) {
        self.record_str_like(field.name(), value.to_string());
    }
}

impl FieldsVisitor {
    fn record_str_like(&mut self, name: &str, text: String) {
        match name {
            "message" => self.msg = text,
            "trace_id" | "traceId" => self.explicit_trace_id = Some(text),
            _ => self.kv.push((name.to_string(), text)),
        }
    }
}

fn escape_json(s: &str) -> String {
    let mut out = String::with_capacity(s.len() + 2);
    for c in s.chars() {
        match c {
            '"' => out.push_str("\\\""),
            '\\' => out.push_str("\\\\"),
            '\n' => out.push_str("\\n"),
            '\r' => out.push_str("\\r"),
            '\t' => out.push_str("\\t"),
            c if (c as u32) < 0x20 => {
                let _ = write!(out, "\\u{:04x}", c as u32);
            }
            c => out.push(c),
        }
    }
    out
}

/// ts："2026-09-18T05:48:39.123Z"（UTC，毫秒）——无 chrono 依赖的手写换算（Hinnant civil_from_days）。
fn format_ts() -> String {
    let now = SystemTime::now().duration_since(UNIX_EPOCH).unwrap_or_default();
    let secs = now.as_secs() as i64;
    let millis = now.subsec_millis();
    let days = secs.div_euclid(86_400);
    let secs_of_day = secs.rem_euclid(86_400);
    let (h, m, s) = (secs_of_day / 3600, (secs_of_day % 3600) / 60, secs_of_day % 60);
    // civil_from_days（Howard Hinnant 算法，1970-01-01 = day 0）
    let z = days + 719_468;
    let era = z.div_euclid(146_097);
    let doe = z.rem_euclid(146_097);
    let yoe = (doe - doe / 1460 + doe / 36_524 - doe / 146_096) / 365;
    let y = yoe + era * 400;
    let doy = doe - (365 * yoe + yoe / 4 - yoe / 100);
    let mp = (5 * doy + 2) / 153;
    let d = doy - (153 * mp + 2) / 5 + 1;
    let month = if mp < 10 { mp + 3 } else { mp - 9 };
    let year = if month <= 2 { y + 1 } else { y };
    format!("{year:04}-{month:02}-{d:02}T{h:02}:{m:02}:{s:02}.{millis:03}Z")
}

impl<C, N> FormatEvent<C, N> for ContractFormat
where
    C: Subscriber + for<'a> LookupSpan<'a>,
    N: for<'a> FormatFields<'a> + 'static,
{
    fn format_event(
        &self,
        _ctx: &FmtContext<'_, C, N>,
        mut writer: Writer<'_>,
        event: &Event<'_>,
    ) -> fmt::Result {
        let mut visitor = FieldsVisitor {
            msg: String::new(),
            kv: Vec::new(),
            explicit_trace_id: None,
        };
        event.record(&mut visitor);
        let meta = event.metadata();
        let level = match *meta.level() {
            Level::WARN => "WARN",
            Level::INFO => "INFO",
            Level::ERROR => "ERROR",
            Level::DEBUG => "DEBUG",
            Level::TRACE => "TRACE",
        };
        let service = SERVICE.get().map(String::as_str).unwrap_or("");
        let env = ENV.get().map(String::as_str).unwrap_or("");
        let trace_id = visitor.explicit_trace_id.clone().unwrap_or_else(current_trace);
        writer.write_str("{\"ts\":\"")?;
        writer.write_str(&format_ts())?;
        writer.write_str("\",\"level\":\"")?;
        writer.write_str(level)?;
        writer.write_str("\",\"service\":\"")?;
        writer.write_str(&escape_json(service))?;
        writer.write_str("\",\"env\":\"")?;
        writer.write_str(&escape_json(env))?;
        writer.write_str("\",\"traceId\":\"")?;
        writer.write_str(&escape_json(&trace_id))?;
        writer.write_str("\",\"logger\":\"")?;
        writer.write_str(&escape_json(meta.target()))?;
        writer.write_str("\",\"msg\":\"")?;
        writer.write_str(&escape_json(&visitor.msg))?;
        writer.write_str("\"")?;
        for (k, v) in &visitor.kv {
            writer.write_str(",\"")?;
            writer.write_str(&escape_json(k))?;
            writer.write_str("\":\"")?;
            writer.write_str(&escape_json(v))?;
            writer.write_str("\"")?;
        }
        writer.write_str("}\n")
    }
}

/// 捕获型 MakeWriter（测试断言行协议用）。
#[derive(Clone)]
pub struct CaptureWriter(pub Arc<Mutex<Vec<u8>>>);

impl io::Write for CaptureWriter {
    fn write(&mut self, buf: &[u8]) -> io::Result<usize> {
        self.0.lock().expect("logx capture poisoned").extend_from_slice(buf);
        Ok(buf.len())
    }
    fn flush(&mut self) -> io::Result<()> {
        Ok(())
    }
}

impl<'a> tracing_subscriber::fmt::MakeWriter<'a> for CaptureWriter {
    type Writer = CaptureWriter;
    fn make_writer(&'a self) -> Self::Writer {
        self.clone()
    }
}

/// 全局 subscriber 初始化（幂等：重复调用跳过——并行测试 safety；RUST_LOG 未设默认 INFO）。
pub fn init(service: &str, env: &str) {
    let _ = SERVICE.set(service.to_string());
    let _ = ENV.set(env.to_string());
    let layer = tracing_subscriber::fmt::layer()
        .event_format(ContractFormat)
        .with_writer(io::stdout);
    let _ = tracing::subscriber::set_global_default(tracing_subscriber::registry().with(layer));
}

/// 测试用：挂 SERVICE/ENV 静态并返回可 set_default 的 subscriber（捕获到 sink）。
pub fn subscriber_for_test(service: &str, env: &str, sink: CaptureWriter) -> tracing_subscriber::Registry {
    let _ = SERVICE.set(service.to_string());
    let _ = ENV.set(env.to_string());
    let layer = tracing_subscriber::fmt::layer()
        .event_format(ContractFormat)
        .with_writer(sink);
    tracing_subscriber::registry().with(layer)
}
```

- [ ] **Step 3: 内联测试（先红：init 前先写测试跑一次看编译断言路径）**

`logx.rs` 尾部：

```rust
#[cfg(test)]
mod tests {
    use super::*;
    use std::sync::Arc;

    fn last_line(sink: &CaptureWriter) -> String {
        let buf = sink.0.lock().expect("poisoned").clone();
        String::from_utf8_lossy(&buf).trim_end().to_string()
    }

    #[tokio::test]
    async fn emits_contract_ndjson_line_with_scoped_trace() {
        let sink = CaptureWriter(Arc::new(Mutex::new(Vec::new())));
        let sub = subscriber_for_test("ycomp", "local", sink.clone());
        let _guard = tracing::subscriber::set_default(sub);
        scope_trace("0af7651916cd43dd8448eb211c80319c".to_string(), async {
            tracing::info!(target: "yarch_axum::logx::tests", message = "hello", method = "POST", status = 201u64);
        })
        .await;
        let line = last_line(&sink);
        assert!(line.starts_with("{\"ts\":\""), "行协议须以 ts 开头: {line}");
        assert!(line.contains("\"level\":\"INFO\""), "{line}");
        assert!(line.contains("\"service\":\"ycomp\""), "{line}");
        assert!(line.contains("\"env\":\"local\""), "{line}");
        assert!(line.contains("\"traceId\":\"0af7651916cd43dd8448eb211c80319c\""), "{line}");
        assert!(line.contains("\"logger\":\"yarch_axum::logx::tests\""), "{line}");
        assert!(line.contains("\"msg\":\"hello\""), "{line}");
        assert!(line.contains("\"method\":\"POST\""), "{line}");
        assert!(line.contains("\"status\":\"201\""), "{line}");
        assert!(line.ends_with("}"), "{line}");
    }

    #[test]
    fn current_trace_empty_when_unbound() {
        assert_eq!(current_trace(), "");
    }

    #[test]
    fn ts_shape_is_iso8601_utc_millis() {
        let ts = format_ts();
        // 2026-09-18T05:48:39.123Z = 24 + 'Z'
        assert_eq!(ts.len(), 24, "{ts}");
        assert!(ts.ends_with('Z') && ts.contains('T'), "{ts}");
    }
}
```

- [ ] **Step 4: 跑测试**

Run: `cd /Users/yuandonghao/sidejob/sources/yarch/stacks/rust && cargo test -p yarch-axum logx`
Expected: 3 PASS。（注意：task_local 在 `#[tokio::test]` 默认 current_thread 单线程内 scope 正常。）

- [ ] **Step 5: fmt/clippy + Commit**

```bash
cd /Users/yuandonghao/sidejob/sources/yarch/stacks/rust && cargo fmt --all && cargo clippy --all-targets -- -D warnings
cd /Users/yuandonghao/sidejob/sources/yarch && git add stacks/rust/crates/yarch-axum stacks/rust/Cargo.lock
git commit -m "feat(rust): yarch-axum logx——契约 ndjson 行协议（tracing-subscriber 自定义 FormatEvent：ts/level/service/env/traceId/logger/msg+kv 平铺）+ task_local traceId 贯穿" -- stacks/rust/crates/yarch-axum stacks/rust/Cargo.lock
```

---

### Task 2: store——IdempotencyStore/RateLimiter trait + InMemory 实现

**Files:**
- Create: `crates/yarch-axum/src/store.rs`
- Modify: `crates/yarch-axum/src/lib.rs`（`pub mod store;`）

**Interfaces:**
- Produces:
  - `pub enum AcquireState { Acquired, Replay, Pending, Mismatch }`
  - `pub trait IdempotencyStore: Send + Sync + 'static { fn acquire(&self, key, digest) -> AcquireState; fn load(&self, key) -> Option<(u16, String)>; fn store_response(&self, key, status, body); fn release(&self, key); }`
  - `pub trait RateLimiter: Send + Sync + 'static { fn hit(&self, key, window_s: u64, limit: u64) -> bool; }`
  - `InMemoryIdempotencyStore`（无 TTL——同进程内存档，跨实例/持久化走 Redis 触发档）
  - `InMemoryRateLimiter`（固定窗口）

- [ ] **Step 1: 写 `store.rs`（实现 + 单测一次落，测试先行红步 = 先只写 mod tests 再补 impl 同文件，执行时按批次二惯例整文件写入后跑）**

```rust
//! 幂等/限流存储端口（spec 五-3：Redis 经 yarch-axum 封装；此处先立端口与进程内实现，
//! Redis 实现触发式——首个需要跨实例幂等/限流的服务立项时）。
//! 语义对齐 python redix.IdempotencyStore / FixedWindowLimiter。

use std::collections::HashMap;
use std::sync::Mutex;
use std::time::{Duration, Instant};

/// 幂等 acquire 三态 + mismatch（对齐 python redix 口径）。
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum AcquireState {
    /// 首次占位成功，执行业务
    Acquired,
    /// 同 digest 已有 done，可回放
    Replay,
    /// 同 key 异 digest，拒绝（1007）
    Mismatch,
    /// 同 digest 占位中（并发请求）
    Pending,
}

pub trait IdempotencyStore: Send + Sync + 'static {
    fn acquire(&self, key: &str, digest: &str) -> AcquireState;
    fn load(&self, key: &str) -> Option<(u16, String)>;
    fn store_response(&self, key: &str, status: u16, body: String);
    fn release(&self, key: &str);
}

/// 进程内幂等存储（测试与单实例档；无 TTL——key 生命周期随进程）。
#[derive(Default)]
pub struct InMemoryIdempotencyStore {
    entries: Mutex<HashMap<String, Entry>>,
}

#[derive(Clone)]
struct Entry {
    digest: String,
    status: Option<u16>,
    body: Option<String>,
}

impl InMemoryIdempotencyStore {
    pub fn new() -> Self {
        Self::default()
    }
}

impl IdempotencyStore for InMemoryIdempotencyStore {
    fn acquire(&self, key: &str, digest: &str) -> AcquireState {
        let mut entries = self.entries.lock().expect("idempotency store poisoned");
        match entries.get(key) {
            None => {
                entries.insert(key.to_string(), Entry { digest: digest.to_string(), status: None, body: None });
                AcquireState::Acquired
            }
            Some(e) if e.digest != digest => AcquireState::Mismatch,
            Some(e) if e.status.is_some() => AcquireState::Replay,
            Some(_) => AcquireState::Pending,
        }
    }

    fn load(&self, key: &str) -> Option<(u16, String)> {
        self.entries
            .lock()
            .expect("idempotency store poisoned")
            .get(key)
            .and_then(|e| e.status.map(|s| (s, e.body.clone().unwrap_or_default())))
    }

    fn store_response(&self, key: &str, status: u16, body: String) {
        if let Some(e) = self.entries.lock().expect("idempotency store poisoned").get_mut(key) {
            e.status = Some(status);
            e.body = Some(body);
        }
    }

    fn release(&self, key: &str) {
        self.entries.lock().expect("idempotency store poisoned").remove(key);
    }
}

pub trait RateLimiter: Send + Sync + 'static {
    /// 固定窗口计数：命中且未超限返回 true。
    fn hit(&self, key: &str, window: Duration, limit: u64) -> bool;
}

/// 进程内固定窗口限流（窗口起点 = 首次命中时刻）。
#[derive(Default)]
pub struct InMemoryRateLimiter {
    windows: Mutex<HashMap<String, (Instant, u64)>>,
}

impl InMemoryRateLimiter {
    pub fn new() -> Self {
        Self::default()
    }
}

impl RateLimiter for InMemoryRateLimiter {
    fn hit(&self, key: &str, window: Duration, limit: u64) -> bool {
        let mut windows = self.windows.lock().expect("rate limiter poisoned");
        let now = Instant::now();
        let entry = windows.entry(key.to_string()).or_insert((now, 0));
        if now.duration_since(entry.0) >= window {
            *entry = (now, 1);
            return true;
        }
        entry.1 += 1;
        entry.1 <= limit
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn idempotency_acquire_replay_mismatch_pending() {
        let store = InMemoryIdempotencyStore::new();
        assert_eq!(store.acquire("k", "d1"), AcquireState::Acquired);
        assert_eq!(store.acquire("k", "d1"), AcquireState::Pending); // 未 done
        assert_eq!(store.acquire("k", "d2"), AcquireState::Mismatch);
        store.store_response("k", 201, "{\"code\":0}".to_string());
        assert_eq!(store.acquire("k", "d1"), AcquireState::Replay);
        assert_eq!(store.load("k"), Some((201, "{\"code\":0}".to_string())));
        store.release("k");
        assert_eq!(store.load("k"), None);
    }

    #[test]
    fn fixed_window_limits_burst() {
        let limiter = InMemoryRateLimiter::new();
        for i in 0..3 {
            assert!(limiter.hit("rl", Duration::from_secs(60), 3), "第 {} 次应放行", i + 1);
        }
        assert!(!limiter.hit("rl", Duration::from_secs(60), 3), "第 4 次应拒绝");
    }

    #[test]
    fn fixed_window_resets_after_window() {
        let limiter = InMemoryRateLimiter::new();
        let tiny = Duration::from_millis(5);
        assert!(limiter.hit("k", tiny, 1));
        assert!(!limiter.hit("k", tiny, 1));
        std::thread::sleep(Duration::from_millis(10));
        assert!(limiter.hit("k", tiny, 1), "窗口过后应重置");
    }
}
```

- [ ] **Step 2: 跑测试 + 机检 + Commit**

Run: `cd /Users/yuandonghao/sidejob/sources/yarch/stacks/rust && cargo test -p yarch-axum store && cargo fmt --all && cargo clippy --all-targets -- -D warnings`

```bash
cd /Users/yuandonghao/sidejob/sources/yarch && git add stacks/rust/crates/yarch-axum
git commit -m "feat(rust): yarch-axum store——IdempotencyStore/RateLimiter 端口 + InMemory 实现（固定窗口；Redis 实现触发式）" -- stacks/rust/crates/yarch-axum
```

---

### Task 3: 信封回包 helpers + Trace 中间件

**Files:**
- Create: `crates/yarch-axum/src/web.rs`（ok/err Response helpers）
- Create: `crates/yarch-axum/src/middleware/mod.rs`、`crates/yarch-axum/src/middleware/trace.rs`
- Modify: `crates/yarch-axum/src/lib.rs`（`pub mod middleware; pub mod web;`）

**Interfaces:**
- Consumes: `logx::current_trace/scope_trace`、`yarch_contract::{errcode, response::RestResponse, trace::parse_traceparent/TraceId}`
- Produces:
  - `web::ok<T: Serialize>(data: T) -> Response` / `web::ok_status<T: Serialize>(data: T, status: u16)` / `web::err(code: i32) -> Response` / `web::err_with_message(code: i32, message: String) -> Response`（Content-Type: application/json，traceId 取 current_trace）
  - `middleware::trace_mw`（`axum::middleware::from_fn` 兼容签名：`async fn(Request, Next) -> Response`）：解析 traceparent → X-Trace-Id（≤64）→ 生成；scope 绑定 + 注入 extensions `TraceCtx` + 响应头回显 `x-trace-id`
  - `middleware::TraceCtx(pub String)`（供 handler 取当前 traceId）

- [ ] **Step 1: 写 `web.rs`**

```rust
//! web：信封回包 helpers（对偶 python web.ok/page；page 随 2b sqlx 装配）。

use axum::http::{HeaderValue, StatusCode};
use axum::response::Response;
use axum::body::Body;
use serde::Serialize;

use crate::logx;
use yarch_contract::errcode;
use yarch_contract::response::RestResponse;

fn envelope_to_response<T: Serialize>(resp: &RestResponse<T>, status: u16) -> Response {
    let body = serde_json::to_vec(resp).expect("信封序列化不可失败");
    Response::builder()
        .status(StatusCode::from_u16(status).unwrap_or(StatusCode::INTERNAL_SERVER_ERROR))
        .header("content-type", HeaderValue::from_static("application/json"))
        .body(Body::from(body))
        .expect("builder 参数常量合法")
}

/// 成功回包（200）。
pub fn ok<T: Serialize>(data: T) -> Response {
    let resp = RestResponse::ok(data, logx::current_trace());
    envelope_to_response(&resp, 200)
}

/// 成功回包（自定义状态码，如 201）。
pub fn ok_status<T: Serialize>(data: T, status: u16) -> Response {
    let resp = RestResponse::ok(data, logx::current_trace());
    envelope_to_response(&resp, status)
}

/// 业务失败回包：状态码 = 码表映射，message = 默认文案。
pub fn err(code: i32) -> Response {
    let resp = RestResponse::<()>::error(code, logx::current_trace());
    let status = errcode::lookup(code).http;
    envelope_to_response(&resp, status)
}

/// 业务失败回包 + 覆盖文案（「默认文案：细节」规则由调用方拼装）。
pub fn err_with_message(code: i32, message: String) -> Response {
    let resp = RestResponse::<()>::error_with_message(code, message, logx::current_trace());
    let status = errcode::lookup(code).http;
    envelope_to_response(&resp, status)
}
```

- [ ] **Step 2: 写 `middleware/mod.rs` 与 `middleware/trace.rs`**

`middleware/mod.rs`：

```rust
//! 中间件五件（组合语义 spec 六-2；外→内 AccessLog > Trace > Recovery > Rate > Idem）。

pub mod access_log;
pub mod idempotency;
pub mod rate_limit;
pub mod recovery;
pub mod trace;

use std::sync::Arc;

use crate::store::{IdempotencyStore, RateLimiter};

/// 幂等中间件装配参数。
#[derive(Clone)]
pub struct IdemState {
    pub store: Arc<dyn IdempotencyStore>,
    pub service: String,
}

/// 限流中间件装配参数。
#[derive(Clone)]
pub struct RateState {
    pub limiter: Arc<dyn RateLimiter>,
    pub limit: u64,
    pub window: std::time::Duration,
    pub service: String,
}
```

`middleware/trace.rs`：

```rust
//! traceId 三级入口（traceparent→X-Trace-Id→生成）+ 响应头回显（logging-trace.md 三）。
//! 对偶 python TraceMiddleware：task_local 作用域绑定（contextvars 对偶）。

use axum::extract::Request;
use axum::http::{header::HeaderName, HeaderValue};
use axum::middleware::Next;
use axum::response::Response;

use crate::logx;
use yarch_contract::trace::{parse_traceparent, TraceId};

/// 注入 request extensions 的 trace 上下文（handler 可取）。
#[derive(Clone)]
pub struct TraceCtx(pub String);

static X_TRACE_ID: HeaderName = HeaderName::from_static("x-trace-id");

/// 解析入口 traceId：traceparent（00-…-…-…取 trace-id 段）→ X-Trace-Id（≤64）→ 生成。
fn resolve_trace_id(headers: &axum::http::HeaderMap) -> String {
    if let Some(tp) = headers.get("traceparent").and_then(|v| v.to_str().ok()) {
        if let Some(parsed) = parse_traceparent(tp.trim()) {
            return parsed.trace_id;
        }
    }
    if let Some(raw) = headers.get("x-trace-id").and_then(|v| v.to_str().ok()) {
        let trimmed = raw.trim();
        if !trimmed.is_empty() {
            return trimmed.chars().take(64).collect();
        }
    }
    TraceId::generate().to_string()
}

pub async fn trace_mw(mut req: Request, next: Next) -> Response {
    let trace_id = resolve_trace_id(req.headers());
    req.extensions_mut().insert(TraceCtx(trace_id.clone()));
    let resp: Response = logx::scope_trace(trace_id.clone(), next.run(req)).await;
    let mut resp = resp;
    if let Ok(value) = HeaderValue::from_str(&trace_id) {
        resp.headers_mut().insert(X_TRACE_ID, value);
    }
    resp
}
```

（注意：`Next::run` 在 axum 0.8 返回 `Result<Response, Infallible>` 还是 `Response` 以编译器为准——执行时按报错微调 `.expect("infallible")`。）

- [ ] **Step 3: 红测试（`middleware/trace.rs` 内联）**

```rust
#[cfg(test)]
mod tests {
    use super::*;
    use axum::body::Body;
    use axum::extract::Request;
    use axum::http::StatusCode;
    use tower::ServiceExt;

    async fn echo_trace(req: Request) -> Response {
        let ctx = req.extensions().get::<TraceCtx>().cloned();
        crate::web::ok(serde_json::json!({ "saw": ctx.map(|c| c.0) }))
    }

    #[tokio::test]
    async fn generates_when_absent_and_echoes_header() {
        let app = axum::Router::new()
            .route("/api/v1/ping", axum::routing::get(echo_trace))
            .layer(axum::middleware::from_fn(trace_mw));
        let resp = app
            .oneshot(Request::builder().uri("/api/v1/ping").body(Body::empty()).unwrap())
            .await
            .unwrap();
        assert_eq!(resp.status(), StatusCode::OK);
        let echoed = resp.headers().get("x-trace-id").unwrap().to_str().unwrap().to_string();
        assert!(TraceId::is_valid(&echoed) && !echoed.starts_with("0000"), "{echoed}");
    }

    #[tokio::test]
    async fn prefers_traceparent_then_x_trace_id() {
        let app = axum::Router::new()
            .route("/api/v1/ping", axum::routing::get(echo_trace))
            .layer(axum::middleware::from_fn(trace_mw));
        // traceparent 优先
        let resp = app
            .clone()
            .oneshot(
                Request::builder()
                    .uri("/api/v1/ping")
                    .header("traceparent", "00-0af7651916cd43dd8448eb211c80319c-00f067aa0ba902b7-01")
                    .header("x-trace-id", "should-be-ignored")
                    .body(Body::empty())
                    .unwrap(),
            )
            .await
            .unwrap();
        assert_eq!(resp.headers().get("x-trace-id").unwrap(), "0af7651916cd43dd8448eb211c80319c");
        // 无 traceparent 时用 x-trace-id
        let resp = app
            .oneshot(
                Request::builder()
                    .uri("/api/v1/ping")
                    .header("x-trace-id", "custom-id-123")
                    .body(Body::empty())
                    .unwrap(),
            )
            .await
            .unwrap();
        assert_eq!(resp.headers().get("x-trace-id").unwrap(), "custom-id-123");
    }
}
```

- [ ] **Step 4: 跑测试 + 机检 + Commit**

Run: `cd /Users/yuandonghao/sidejob/sources/yarch/stacks/rust && cargo test -p yarch-axum trace && cargo fmt --all && cargo clippy --all-targets -- -D warnings`

```bash
cd /Users/yuandonghao/sidejob/sources/yarch && git add stacks/rust/crates/yarch-axum
git commit -m "feat(rust): yarch-axum web 信封回包 helpers + Trace 中间件（三级入口 traceparent/X-Trace-Id/生成，task_local 作用域 + 响应头回显）" -- stacks/rust/crates/yarch-axum
```

---

### Task 4: Recovery + AccessLog 中间件

**Files:**
- Create: `crates/yarch-axum/src/middleware/recovery.rs`、`crates/yarch-axum/src/middleware/access_log.rs`

**Interfaces:**
- Consumes: `web::err`、`logx::current_trace`
- Produces: `recovery_mw`（panic → 500 信封 1000 + error 日志）、`access_log_mw`（ndjson request completed：method/path/status/costMs/traceId——traceId 取响应头快照，事件字段显式传）

- [ ] **Step 1: 写 `recovery.rs`**

```rust
//! 未捕获 panic → 500 信封 code=1000（message 固定「内部错误」，细节只进日志）。
//! 对偶 python RecoveryMiddleware（其 except Exception ≈ rust catch_unwind——axum 的
//! handler 错误已由类型系统强制处理，运行期未捕获物即 panic）。

use std::panic::AssertUnwindSafe;

use axum::extract::Request;
use axum::middleware::Next;
use axum::response::Response;
use futures_util::FutureExt;

use crate::web;

pub async fn recovery_mw(req: Request, next: Next) -> Response {
    match AssertUnwindSafe(next.run(req)).catch_unwind().await {
        Ok(resp) => resp,
        Err(panic) => {
            let detail = if let Some(s) = panic.downcast_ref::<&str>() {
                (*s).to_string()
            } else if let Some(s) = panic.downcast_ref::<String>() {
                s.clone()
            } else {
                "non-string panic payload".to_string()
            };
            tracing::error!(target: "yarch_axum::middleware::recovery", message = "internal error", detail = %detail);
            web::err(1000)
        }
    }
}
```

- [ ] **Step 2: 写 `access_log.rs`**

```rust
//! 访问日志：ndjson request completed（method/path/status/costMs + traceId）。
//! traceId 从响应头快照（内层 Trace 已回显 x-trace-id）——本层在 Trace 外层，
//! 事件发生时 task_local 已出作用域，显式字段优先于 FormatEvent 注入（对偶 python
//! accesslog 的响应期快照再 bind 手法）。

use std::time::Instant;

use axum::extract::Request;
use axum::http::Method;
use axum::middleware::Next;
use axum::response::Response;

pub async fn access_log_mw(req: Request, next: Next) -> Response {
    let method: Method = req.method().clone();
    let path = req.uri().path().to_string();
    let start = Instant::now();
    let resp = next.run(req).await;
    let status = resp.status().as_u16();
    let trace_id = resp
        .headers()
        .get("x-trace-id")
        .and_then(|v| v.to_str().ok())
        .unwrap_or_default()
        .to_string();
    let cost_ms = start.elapsed().as_millis() as u64;
    tracing::info!(
        target: "yarch_axum::middleware::access_log",
        message = "request completed",
        trace_id = %trace_id,
        method = %method,
        path = %path,
        status,
        cost_ms = cost_ms as i64
    );
    resp
}
```

- [ ] **Step 3: 红测试（内联，先跑确认行为）**

```rust
#[cfg(test)]
mod tests {
    use super::*;
    use axum::body::Body;
    use axum::http::StatusCode;
    use axum::extract::Request;
    use tower::ServiceExt;

    // recovery.rs tests:
    #[tokio::test]
    async fn panic_becomes_500_envelope_code_1000() {
        let app = axum::Router::new()
            .route("/api/v1/boom", axum::routing::post(|| async { panic!("boom") }))
            .layer(axum::middleware::from_fn(super::super::recovery::recovery_mw));
        let resp = app
            .oneshot(Request::builder().method("POST").uri("/api/v1/boom").body(Body::empty()).unwrap())
            .await
            .unwrap();
        assert_eq!(resp.status(), StatusCode::INTERNAL_SERVER_ERROR);
        let bytes = axum::body::to_bytes(resp.into_body(), 1024).await.unwrap();
        let v: serde_json::Value = serde_json::from_slice(&bytes).unwrap();
        assert_eq!(v["code"], 1000);
        assert_eq!(v["message"], "内部错误");
    }

    // access_log.rs tests:
    #[tokio::test]
    async fn logs_request_completed_with_status() {
        let sink = crate::logx::CaptureWriter(std::sync::Arc::new(std::sync::Mutex::new(Vec::new())));
        let sub = crate::logx::subscriber_for_test("ycomp", "local", sink.clone());
        let _guard = tracing::subscriber::set_default(sub);
        let app = axum::Router::new()
            .route("/api/v1/jobs", axum::routing::get(|| async { crate::web::ok(serde_json::json!({"a":1})) }))
            .layer(axum::middleware::from_fn(access_log_mw))
            .layer(axum::middleware::from_fn(super::super::trace::trace_mw));
        let resp = app
            .oneshot(Request::builder().uri("/api/v1/jobs").body(Body::empty()).unwrap())
            .await
            .unwrap();
        assert_eq!(resp.status(), StatusCode::OK);
        let buf = sink.0.lock().unwrap().clone();
        let line = String::from_utf8_lossy(&buf);
        assert!(line.contains("\"msg\":\"request completed\""), "{line}");
        assert!(line.contains("\"path\":\"/api/v1/jobs\""), "{line}");
        assert!(line.contains("\"status\":\"200\""), "{line}");
        assert!(line.contains("\"trace_id\":\""), "{line}");
    }
}
```

- [ ] **Step 4: 跑测试 + 机检 + Commit**

Run: `cd /Users/yuandonghao/sidejob/sources/yarch/stacks/rust && cargo test -p yarch-axum && cargo fmt --all && cargo clippy --all-targets -- -D warnings`

```bash
cd /Users/yuandonghao/sidejob/sources/yarch && git add stacks/rust/crates/yarch-axum
git commit -m "feat(rust): yarch-axum Recovery（panic→500 信封 1000，catch_unwind）+ AccessLog（request completed 行协议，traceId 响应头快照显式字段）" -- stacks/rust/crates/yarch-axum
```

---

### Task 5: Idempotency 中间件

**Files:**
- Create: `crates/yarch-axum/src/middleware/idempotency.rs`

**Interfaces:**
- Consumes: `IdemState`（Task 3 mod.rs 定义）、`IdempotencyStore/AcquireState`、`web::err_with_message`、`logx::current_trace`
- Produces: `idempotency_mw(State<IdemState>, Request, Next) -> Response`——不安全方法（POST/PUT/PATCH/DELETE）+ `Idempotency-Key` 头；digest = sha256("{method} {path} " + body)；key = `{service}:idem:{sha256(raw_key)}`；pending 轮询 5×200ms；回放原状态码+原体；执行捕获响应，5xx 释放不落 done，panic 释放后 `resume_unwind`

- [ ] **Step 1: 写 `idempotency.rs`**

```rust
//! 幂等中间件（rest-conventions.md 幂等总则-1）：同键同参回放/异参 1007/并发短暂等待。
//! 对齐 python IdempotencyMiddleware：acquired→执行并捕获；replay→回放；pending→
//! 轮询 5×200ms 后仍无 done 即 1007；mismatch→1007；5xx/异常→release 不落 done。

use std::panic::{resume_unwind, AssertUnwindSafe};
use std::sync::Arc;
use std::time::Duration;

use axum::body::{to_bytes, Body};
use axum::extract::{Request, State};
use axum::http::HeaderValue;
use axum::middleware::Next;
use axum::response::Response;
use futures_util::FutureExt;
use sha2::{Digest, Sha256};

use crate::store::{AcquireState, IdemState};
use crate::web;

/// 幂等捕获的响应体上限（python 无上限；此处 16MB 护栏——超限视为内部错误不落 done）。
const CAPTURE_LIMIT: usize = 16 * 1024 * 1024;

fn sha256_hex(input: &[u8]) -> String {
    let mut hasher = Sha256::new();
    hasher.update(input);
    let out = hasher.finalize();
    let mut hex = String::with_capacity(64);
    for b in out {
        hex.push_str(&format!("{b:02x}"));
    }
    hex
}

fn is_unsafe(method: &axum::http::Method) -> bool {
    matches!(*method, axum::http::Method::POST | axum::http::Method::PUT | axum::http::Method::PATCH | axum::http::Method::DELETE)
}

pub async fn idempotency_mw(
    State(state): State<IdemState>,
    req: Request,
    next: Next,
) -> Response {
    if !is_unsafe(req.method()) {
        return next.run(req).await;
    }
    let raw_key = req
        .headers()
        .get("idempotency-key")
        .and_then(|v| v.to_str().ok())
        .map(str::trim)
        .unwrap_or_default();
    if raw_key.is_empty() {
        return next.run(req).await;
    }

    // 读全量请求体（幂等须以完整参数做摘要）
    let (parts, body) = req.into_parts();
    let body_bytes = match to_bytes(body, CAPTURE_LIMIT).await {
        Ok(bytes) => bytes,
        Err(e) => {
            tracing::warn!(target: "yarch_axum::middleware::idempotency", message = "read body failed", detail = %e);
            return web::err(1002);
        }
    };
    let mut digest_input = format!("{} {} ", parts.method, parts.uri.path()).into_bytes();
    digest_input.extend_from_slice(&body_bytes);
    let digest = sha256_hex(&digest_input);
    let key = format!("{}:idem:{}", state.service, sha256_hex(raw_key.as_bytes()));

    let mut acquired = state.store.acquire(&key, &digest);
    if acquired == AcquireState::Mismatch {
        return conflict();
    }
    if acquired == AcquireState::Pending {
        for _ in 0..5 {
            tokio::time::sleep(Duration::from_millis(200)).await;
            acquired = state.store.acquire(&key, &digest);
            if matches!(acquired, AcquireState::Replay | AcquireState::Mismatch) {
                break;
            }
        }
        if acquired != AcquireState::Replay {
            return conflict();
        }
    }
    if acquired == AcquireState::Replay {
        return match state.store.load(&key) {
            Some((status, body)) => raw_json_response(status, body),
            None => conflict(),
        };
    }

    // acquired：执行并捕获响应
    let rebuilt = Request::from_parts(parts, Body::from(body_bytes.clone()));
    let result = AssertUnwindSafe(next.run(rebuilt)).catch_unwind().await;
    let resp = match result {
        Ok(resp) => resp,
        Err(panic) => {
            // 执行失败释放 pending：同键重试可再执行；残缺响应不落库
            state.store.release(&key);
            resume_unwind(panic);
        }
    };
    let (mut resp_parts, resp_body) = resp.into_parts();
    let bytes = match to_bytes(resp_body, CAPTURE_LIMIT).await {
        Ok(bytes) => bytes,
        Err(_) => {
            state.store.release(&key);
            return Response::builder()
                .status(500)
                .body(Body::from("{\"code\":1000,\"message\":\"内部错误\",\"data\":null,\"traceId\":\"\"}"))
                .expect("常量");
        }
    };
    let status = resp_parts.status.as_u16();
    if status >= 500 {
        // 5xx 不落 done：释放占位允许重试（java IdempotencyInterceptor 同口径）
        state.store.release(&key);
    } else {
        state.store.store_response(&key, status, String::from_utf8_lossy(&bytes).to_string());
    }
    // 原样回放本次的响应（重建 body）
    resp_parts.extensions.get_or_insert_with(axum::http::Extensions::new);
    Response::from_parts(resp_parts, Body::from(bytes))
}

fn conflict() -> Response {
    let message = "幂等冲突：重复提交：Idempotency-Key 冲突".to_string();
    web::err_with_message(1007, message)
}

fn raw_json_response(status: u16, body: String) -> Response {
    let mut resp = Response::new(Body::from(body));
    *resp.status_mut() = axum::http::StatusCode::from_u16(status).unwrap_or(axum::http::StatusCode::INTERNAL_SERVER_ERROR);
    if resp.headers_mut().insert("content-type", HeaderValue::from_static("application/json")).is_ok() {}
    resp
}
```

（执行时按编译器报错微调：`Next::run` 的 Result 形态、`Response::from_parts` 与 Extensions 重建等；语义不变。）

- [ ] **Step 2: 内联单测（acquired/replay/mismatch 三路径直测 store；组合语义在 Task 7 全链路验）**

```rust
#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn digest_shape() {
        let d = sha256_hex(b"POST /api/v1/jobs ");
        assert_eq!(d.len(), 64);
        assert!(d.bytes().all(|b| matches!(b, b'0'..=b'9' | b'a'..=b'f')));
    }

    #[test]
    fn key_shape() {
        let key = format!("{}:idem:{}", "ycomp", sha256_hex(b"job-1"));
        assert!(key.starts_with("ycomp:idem:"));
        assert_eq!(key.len(), "ycomp:idem:".len() + 64);
    }
}
```

- [ ] **Step 3: 跑测试 + 机检 + Commit**

Run: `cd /Users/yuandonghao/sidejob/sources/yarch/stacks/rust && cargo test -p yarch-axum idempotency && cargo fmt --all && cargo clippy --all-targets -- -D warnings`

```bash
cd /Users/yuandonghao/sidejob/sources/yarch && git add stacks/rust/crates/yarch-axum
git commit -m "feat(rust): yarch-axum Idempotency 中间件——digest 摘要 + 三态流转 + 5xx/异常释放（对齐 python 口径）" -- stacks/rust/crates/yarch-axum
```

---

### Task 6: RateLimit 中间件 + setup() 装配

**Files:**
- Create: `crates/yarch-axum/src/middleware/rate_limit.rs`
- Modify: `crates/yarch-axum/src/lib.rs`（`setup` 函数 + re-export）

**Interfaces:**
- Consumes: `RateState`、`RateLimiter`、`web::err`、所有五件
- Produces:
  - `rate_limit_mw(State<RateState>, Request, Next) -> Response`：key=`{service}:rl:{path}`，超限 429 + 1006 信封
  - `lib::setup(router: Router, opts: Options) -> Router`：logx init + 五件装配（外→内 AccessLog > Trace > Recovery > Rate > Idem；可选项缺省跳过）
  - `lib::Options { service, env, idempotency: Option<IdemConfig>, rate: Option<RateConfig> }`，`IdemConfig { store: Arc<dyn IdempotencyStore> }`，`RateConfig { limiter: Arc<dyn RateLimiter>, limit: u64, window: Duration }`

- [ ] **Step 1: 写 `rate_limit.rs`**

```rust
//! 限流中间件（固定窗口，跨实例口径）：超限 1006/429 信封。
//! 对齐 python RateLimitMiddleware：key = {service}:rl:{path}。

use axum::extract::{Request, State};
use axum::middleware::Next;
use axum::response::Response;

use crate::middleware::RateState;
use crate::web;

pub async fn rate_limit_mw(State(state): State<RateState>, req: Request, next: Next) -> Response {
    let key = format!("{}:rl:{}", state.service, req.uri().path());
    if !state.limiter.hit(&key, state.window, state.limit) {
        return web::err(1006);
    }
    next.run(req).await
}
```

- [ ] **Step 2: 写 `lib.rs` setup**

```rust
//! yarch-axum：axum 0.8 装配层——中间件五件 + 信封回包 + （2b）sqlx 装配与分页。
//! 语义唯一权威 = contract/api/ 四件套；中间件组合语义对齐 python 终审口径。

pub mod logx;
pub mod middleware;
pub mod store;
pub mod web;

use std::sync::Arc;
use std::time::Duration;

use axum::Router;

use crate::store::{IdempotencyStore, RateLimiter};

/// setup 装配参数。
pub struct Options {
    pub service: String,
    pub env: String,
    pub idempotency: Option<IdemConfig>,
    pub rate: Option<RateConfig>,
}

pub struct IdemConfig {
    pub store: Arc<dyn IdempotencyStore>,
}

pub struct RateConfig {
    pub limiter: Arc<dyn RateLimiter>,
    pub limit: u64,
    pub window: Duration,
}

/// 一行装配（对偶 python web.setup）：
/// 外→内 AccessLog > Trace > Recovery > Rate > Idem。
/// axum `.layer()` 后调用者包外层——故内层先 layer、外层后 layer。
/// Rate 必须在 Idem 外层：限流拒绝不得进入幂等流程——否则 429 会被幂等层捕获
/// 落库为 done，同键合法重试在 TTL 内永远回放 429、操作永不执行。
pub fn setup(router: Router, opts: Options) -> Router {
    logx::init(&opts.service, &opts.env);
    let router = match opts.idempotency {
        Some(idem) => router.layer(axum::middleware::from_fn_with_state(
            middleware::IdemState { store: idem.store, service: opts.service.clone() },
            middleware::idempotency::idempotency_mw,
        )),
        None => router,
    };
    let router = match opts.rate {
        Some(rate) => router.layer(axum::middleware::from_fn_with_state(
            middleware::RateState {
                limiter: rate.limiter,
                limit: rate.limit,
                window: rate.window,
                service: opts.service.clone(),
            },
            middleware::rate_limit::rate_limit_mw,
        )),
        None => router,
    };
    router
        .layer(axum::middleware::from_fn(middleware::recovery::recovery_mw))
        .layer(axum::middleware::from_fn(middleware::trace::trace_mw))
        .layer(axum::middleware::from_fn(middleware::access_log::access_log_mw))
}
```

- [ ] **Step 3: 跑全量 + 机检 + Commit**

Run: `cd /Users/yuandonghao/sidejob/sources/yarch/stacks/rust && cargo test -p yarch-axum && cargo fmt --all && cargo clippy --all-targets -- -D warnings`

```bash
cd /Users/yuandonghao/sidejob/sources/yarch && git add stacks/rust/crates/yarch-axum
git commit -m "feat(rust): yarch-axum RateLimit 中间件 + setup 一行装配（外→内 AccessLog>Trace>Recovery>Rate>Idem，Rate 压 Idem 外层防 429 落库毒化）" -- stacks/rust/crates/yarch-axum
```

---

### Task 7: 组合语义测试（镜像 python 四例）

**Files:**
- Create: `crates/yarch-axum/tests/composition.rs`

**Interfaces:**
- Consumes: `setup/Options/IdemConfig/RateConfig`、`store::{IdempotencyStore, AcquireState, RateLimiter}`、`web::ok_status`

- [ ] **Step 1: 写 `tests/composition.rs`**

```rust
//! 组合语义回归（对齐 python test_middleware_composition.py 四例）：
//! Rate×Idem 顺序 / 异常释放 / 5xx 不落 done / 2xx 正常回放。

use std::collections::HashMap;
use std::panic::AssertUnwindSafe;
use std::sync::{Arc, Mutex};
use std::time::Duration;

use axum::body::{to_bytes, Body};
use axum::extract::Request;
use axum::http::StatusCode;
use axum::response::Response;
use axum::routing::post;
use axum::Router;
use futures_util::FutureExt;
use tower::ServiceExt;
use yarch_axum::{setup, IdemConfig, Options, RateConfig};
use yarch_axum::store::{AcquireState, IdempotencyStore, RateLimiter};

/// 幂等存储假件：三态行为对齐 InMemoryIdempotencyStore，条目可直接观测。
#[derive(Clone, Default)]
struct FakeStore {
    entries: Arc<Mutex<HashMap<String, (String, Option<u16>, Option<String>)>>>,
    releases: Arc<Mutex<Vec<String>>>,
}

impl FakeStore {
    // 断言直接读 entries/releases（观测条目存在性与释放记录）
}

impl IdempotencyStore for FakeStore {
    fn acquire(&self, key: &str, digest: &str) -> AcquireState {
        let mut entries = self.entries.lock().unwrap();
        match entries.get(key) {
            None => {
                entries.insert(key.to_string(), (digest.to_string(), None, None));
                AcquireState::Acquired
            }
            Some((d, _, _)) if d != digest => AcquireState::Mismatch,
            Some((_, Some(_), _)) => AcquireState::Replay,
            Some(_) => AcquireState::Pending,
        }
    }
    fn load(&self, key: &str) -> Option<(u16, String)> {
        self.entries
            .lock()
            .unwrap()
            .get(key)
            .and_then(|(_, s, b)| s.map(|s| (s, b.clone().unwrap_or_default())))
    }
    fn store_response(&self, key: &str, status: u16, body: String) {
        let mut entries = self.entries.lock().unwrap();
        if let Some(e) = entries.get_mut(key) {
            e.1 = Some(status);
            e.2 = Some(body);
        }
    }
    fn release(&self, key: &str) {
        self.entries.lock().unwrap().remove(key);
        self.releases.lock().unwrap().push(key.to_string());
    }
}

/// 可控限流假件：deny_next=true 时下一次 hit 拒绝。
#[derive(Clone, Default)]
struct ToggleLimiter {
    inner: Arc<Mutex<bool>>,
}

impl ToggleLimiter {
    fn deny_next(&self) {
        *self.inner.lock().unwrap() = true;
    }
}

impl RateLimiter for ToggleLimiter {
    fn hit(&self, _key: &str, _window: Duration, _limit: u64) -> bool {
        let mut deny = self.inner.lock().unwrap();
        if *deny {
            *deny = false;
            return false;
        }
        true
    }
}

fn make_app(store: FakeStore, limiter: ToggleLimiter) -> (Router, Arc<Mutex<u32>>) {
    let count = Arc::new(Mutex::new(0u32));
    let c = count.clone();
    let jobs = move || {
        let mut n = c.lock().unwrap();
        *n += 1;
        let seq = *n;
        async move { yarch_axum::web::ok_status(serde_json::json!({ "seq": seq }), 201) }
    };
    let c = count.clone();
    let flaky = move || {
        let mut n = c.lock().unwrap();
        *n += 1;
        drop(n);
        async {
            Response::builder()
                .status(500)
                .header("content-type", "application/json")
                .body(Body::from("{\"code\":1000,\"message\":\"x\"}"))
                .unwrap()
        }
    };
    let c = count.clone();
    let boom = move || {
        let mut n = c.lock().unwrap();
        *n += 1;
        drop(n);
        async { panic!("x") }
    };
    let router = Router::new()
        .route("/api/v1/jobs", post(jobs))
        .route("/api/v1/flaky", post(flaky))
        .route("/api/v1/boom", post(boom));
    let app = setup(
        router,
        Options {
            service: "ysaas-scan".to_string(),
            env: "local".to_string(),
            idempotency: Some(IdemConfig { store: Arc::new(store) }),
            rate: Some(RateConfig { limiter: Arc::new(limiter), limit: 10, window: Duration::from_secs(60) }),
        },
    );
    (app, count)
}

fn post_json(uri: &str, key: &str, body: &str) -> Request {
    Request::builder()
        .method("POST")
        .uri(uri)
        .header("content-type", "application/json")
        .header("idempotency-key", key)
        .body(Body::from(body.to_string()))
        .unwrap()
}

async fn envelope(resp: Response) -> serde_json::Value {
    let bytes = to_bytes(resp.into_body(), 1024 * 1024).await.unwrap();
    serde_json::from_slice(&bytes).unwrap()
}

async fn status_and_envelope(resp: Response) -> (StatusCode, serde_json::Value) {
    let status = resp.status();
    (status, envelope(resp).await)
}

#[tokio::test]
async fn rate_limited_request_must_not_poison_idempotency() {
    let store = FakeStore::default();
    let limiter = ToggleLimiter::default();
    let (app, count) = make_app(store.clone(), limiter.clone());

    limiter.deny_next();
    let (s, v) = status_and_envelope(app.clone().oneshot(post_json("/api/v1/jobs", "job-1", r#"{"a":1}"#)).await.unwrap()).await;
    assert_eq!(s, StatusCode::TOO_MANY_REQUESTS);
    assert_eq!(v["code"], 1006, "限流信封 code=1006：{v}");
    assert_eq!(*count.lock().unwrap(), 0, "被限流的请求不得触达业务");
    assert!(store.entries.lock().unwrap().is_empty(), "限流拒绝不得在幂等存储留下任何条目");

    let (s, v) = status_and_envelope(app.oneshot(post_json("/api/v1/jobs", "job-1", r#"{"a":1}"#)).await.unwrap()).await;
    assert_eq!(s, StatusCode::CREATED);
    assert_eq!(v["data"]["seq"], 1, "限流恢复后同键重试必须真正执行：{v}");
    assert!(!store.entries.lock().unwrap().is_empty(), "正常执行后应落 done 供后续回放");
}

#[tokio::test]
async fn exception_releases_pending_without_partial_done() {
    let store = FakeStore::default();
    let limiter = ToggleLimiter::default();
    let (app, count) = make_app(store.clone(), limiter);

    let (s, v) = status_and_envelope(app.clone().oneshot(post_json("/api/v1/boom", "boom-1", r#"{"a":1}"#)).await.unwrap()).await;
    assert_eq!(s, StatusCode::INTERNAL_SERVER_ERROR);
    assert_eq!(v["code"], 1000, "{v}");
    let entries = store.entries.lock().unwrap();
    assert!(entries.is_empty(), "异常后不得残留 pending 或残缺 done");
    drop(entries);
    assert!(
        store.releases.lock().unwrap().iter().any(|k| k.contains(":idem:")),
        "异常路径必须 release"
    );

    let (s, _) = status_and_envelope(app.oneshot(post_json("/api/v1/boom", "boom-1", r#"{"a":1}"#)).await.unwrap()).await;
    assert_eq!(s, StatusCode::INTERNAL_SERVER_ERROR);
    assert_eq!(*count.lock().unwrap(), 2, "同键重试必须重新执行");
}

#[tokio::test]
async fn five_xx_response_not_stored_as_done() {
    let store = FakeStore::default();
    let limiter = ToggleLimiter::default();
    let (app, count) = make_app(store.clone(), limiter);

    let (s, _) = status_and_envelope(app.clone().oneshot(post_json("/api/v1/flaky", "flaky-1", r#"{"a":1}"#)).await.unwrap()).await;
    assert_eq!(s, StatusCode::INTERNAL_SERVER_ERROR);
    assert_eq!(*count.lock().unwrap(), 1);
    assert!(store.entries.lock().unwrap().is_empty(), "5xx 不得作为可回放 done 存储");

    let (s, _) = status_and_envelope(app.oneshot(post_json("/api/v1/flaky", "flaky-1", r#"{"a":1}"#)).await.unwrap()).await;
    assert_eq!(s, StatusCode::INTERNAL_SERVER_ERROR);
    assert_eq!(*count.lock().unwrap(), 2, "同键重试必须重新执行");
}

#[tokio::test]
async fn two_xx_still_replays_normally() {
    let store = FakeStore::default();
    let limiter = ToggleLimiter::default();
    let (app, count) = make_app(store.clone(), limiter);

    let (s1, v1) = status_and_envelope(app.clone().oneshot(post_json("/api/v1/jobs", "job-9", r#"{"a":1}"#)).await.unwrap()).await;
    let (s2, v2) = status_and_envelope(app.oneshot(post_json("/api/v1/jobs", "job-9", r#"{"a":1}"#)).await.unwrap()).await;
    assert_eq!(s1, StatusCode::CREATED);
    assert_eq!(s2, StatusCode::CREATED);
    assert_eq!(v1, v2, "同键同参必须回放原响应");
    assert_eq!(v1["data"]["seq"], 1);
    assert_eq!(*count.lock().unwrap(), 1, "只执行一次");
}
```

- [ ] **Step 2: 跑测试**

Run: `cd /Users/yuandonghao/sidejob/sources/yarch/stacks/rust && cargo test -p yarch-axum --test composition`
Expected: 4 PASS。**若 layer 顺序语义与预期相反（429 被幂等捕获）→ 翻转 setup 中 idem/rate 的 layer 调用序再跑。**

- [ ] **Step 3: Commit**

```bash
cd /Users/yuandonghao/sidejob/sources/yarch && git add stacks/rust/crates/yarch-axum
git commit -m "test(rust): 中间件组合语义回归四例——Rate×Idem 顺序/异常释放/5xx 不落 done/2xx 回放（逐条翻译 python test_middleware_composition.py）" -- stacks/rust/crates/yarch-axum
```

---

### Task 8: PLAN.md 批次二标记（2a 段）+ 收口

**Files:**
- Modify: `stacks/rust/PLAN.md`

- [ ] **Step 1: 批次二条目追加 2a 完成态**

`stacks/rust/PLAN.md` 五、施工批次第 2 条改为：

```markdown
2. **第二批（拆 2a/2b 执行；2a 已完成 2026-09-18）**：2a = yarch-axum 中间件五件（Trace/Recovery/AccessLog/Idempotency/Rate，组合序外→内 AccessLog>Trace>Recovery>Rate>Idem——Rate 压 Idem 外层防 429 落库毒化）+ logx 契约 ndjson（tracing-subscriber 自定义 FormatEvent）+ 存储 trait 与 InMemory 实现（Redis 触发式）+ setup 一行装配 + 组合语义测试四例（镜像 python）。2b（待做）= sqlx 装配（逻辑删除/审计/分页 D6）+ 模板 users 示例 + 生成后冒烟。
```

- [ ] **Step 2: 全量三件 + Commit + push（经用户授权）**

Run: `cd /Users/yuandonghao/sidejob/sources/yarch/stacks/rust && cargo test --workspace && cargo fmt --all -- --check && cargo clippy --all-targets -- -D warnings`

```bash
cd /Users/yuandonghao/sidejob/sources/yarch && git add stacks/rust/PLAN.md
git commit -m "docs(rust): PLAN 批次二 2a 段完成标记——中间件五件 + logx + 组合测试（2b sqlx/模板待做）" -- stacks/rust/PLAN.md
```

---

## 收尾（本计划 DoD）

1. `cargo test --workspace` 全绿（批次一 19 + 2a 新增：logx 3 + store 3 + trace 2 + recovery/access_log 2 + idempotency 2 + composition 4 = 16，合计 35）；
2. fmt --check + clippy -D warnings 绿；
3. 组合语义四例与 python 逐条同义（Rate×Idem / 异常释放 / 5xx 不落 / 2xx 回放）；
4. 全部 pathspec 提交。

后续（2b 另册）：sqlx 装配（PageData/keyset/逻辑删除）+ cargo-generate 模板（DDD 七包 + users + AGENTS.md 三件套）+ 生成后冒烟进 rust-stack CI + 模板内 PG 集成（services 容器或 .sqlx 离线快照）。
