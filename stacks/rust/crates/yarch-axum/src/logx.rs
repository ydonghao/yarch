//! logx：契约 ndjson 行协议（logging-trace.md v1.0/v1.1）+ task_local traceId 贯穿。
//!
//! 行协议字段序：ts, level, service, env, traceId, logger, msg, ...kv（平铺）。
//! 事件经 tracing 宏发出（target = 模块路径 = logger 字段），由 ContractFormat 渲染成行。
//! 对偶 python logx（structlog）/ golang logx（slog）。

use std::fmt::Write as _;
use std::future::Future;
use std::io;
use std::sync::{Arc, Mutex, OnceLock};
use std::time::{SystemTime, UNIX_EPOCH};

use tracing::{
    field::{Field, Visit},
    Event, Level, Subscriber,
};
use tracing_subscriber::fmt::{format::Writer, FmtContext, FormatEvent, FormatFields};
use tracing_subscriber::layer::SubscriberExt as _;
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

impl FieldsVisitor {
    fn record_str_like(&mut self, name: &str, text: String) {
        match name {
            "message" => self.msg = text,
            "trace_id" | "traceId" => self.explicit_trace_id = Some(text),
            _ => self.kv.push((name.to_string(), text)),
        }
    }
}

impl Visit for FieldsVisitor {
    fn record_debug(&mut self, field: &Field, value: &dyn std::fmt::Debug) {
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
    let now = SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .unwrap_or_default();
    let secs = now.as_secs() as i64;
    let millis = now.subsec_millis();
    let days = secs.div_euclid(86_400);
    let secs_of_day = secs.rem_euclid(86_400);
    let (h, m, s) = (
        secs_of_day / 3600,
        (secs_of_day % 3600) / 60,
        secs_of_day % 60,
    );
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
    ) -> std::fmt::Result {
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
        let trace_id = visitor
            .explicit_trace_id
            .clone()
            .unwrap_or_else(current_trace);
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
        self.0
            .lock()
            .expect("logx capture poisoned")
            .extend_from_slice(buf);
        Ok(buf.len())
    }
    fn flush(&mut self) -> io::Result<()> {
        Ok(())
    }
}

impl tracing_subscriber::fmt::MakeWriter<'_> for CaptureWriter {
    type Writer = CaptureWriter;
    fn make_writer(&self) -> Self::Writer {
        self.clone()
    }
}

/// 全局 subscriber 初始化（幂等：重复调用跳过——并行测试 safety）。
pub fn init(service: &str, env: &str) {
    let _ = SERVICE.set(service.to_string());
    let _ = ENV.set(env.to_string());
    let layer = tracing_subscriber::fmt::layer()
        .event_format(ContractFormat)
        .with_writer(io::stdout);
    let _ = tracing::subscriber::set_global_default(tracing_subscriber::registry().with(layer));
}

/// 测试用：挂 SERVICE/ENV 静态并返回可 set_default 的 subscriber（事件捕获到 sink）。
pub fn subscriber_for_test(
    service: &str,
    env: &str,
    sink: CaptureWriter,
) -> impl tracing::Subscriber + 'static {
    let _ = SERVICE.set(service.to_string());
    let _ = ENV.set(env.to_string());
    let layer = tracing_subscriber::fmt::layer()
        .event_format(ContractFormat)
        .with_writer(sink);
    tracing_subscriber::registry().with(layer)
}

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
        assert!(
            line.contains("\"traceId\":\"0af7651916cd43dd8448eb211c80319c\""),
            "{line}"
        );
        assert!(
            line.contains("\"logger\":\"yarch_axum::logx::tests\""),
            "{line}"
        );
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
        // 2026-09-18T05:48:39.123Z = 24 字符 + 'Z'
        assert_eq!(ts.len(), 24, "{ts}");
        assert!(ts.ends_with('Z') && ts.contains('T'), "{ts}");
    }
}
