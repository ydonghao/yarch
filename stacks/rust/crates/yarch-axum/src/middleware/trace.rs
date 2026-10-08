//! traceId 三级入口（traceparent→X-Trace-Id→生成）+ 响应头回显（logging-trace.md 三）。
//! 对偶 python TraceMiddleware：task_local 作用域绑定（contextvars 对偶）。

use axum::extract::Request;
use axum::http::HeaderValue;
use axum::middleware::Next;
use axum::response::Response;

use crate::logx;
use yarch_contract::trace::{parse_traceparent, TraceId};

/// 注入 request extensions 的 trace 上下文（handler 可取）。
#[derive(Clone)]
pub struct TraceCtx(pub String);

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
        resp.headers_mut().insert("x-trace-id", value);
    }
    resp
}

#[cfg(test)]
mod tests {
    use super::*;
    use axum::body::Body;
    use tower::ServiceExt;

    async fn echo_trace(req: Request) -> Response {
        let ctx = req.extensions().get::<TraceCtx>().cloned();
        crate::web::ok(serde_json::json!({ "saw": ctx.map(|c| c.0) }))
    }

    fn app() -> axum::Router {
        axum::Router::new()
            .route("/api/v1/ping", axum::routing::get(echo_trace))
            .layer(axum::middleware::from_fn(trace_mw))
    }

    #[tokio::test]
    async fn generates_when_absent_and_echoes_header() {
        let resp = app()
            .oneshot(
                Request::builder()
                    .uri("/api/v1/ping")
                    .body(Body::empty())
                    .unwrap(),
            )
            .await
            .unwrap();
        assert_eq!(resp.status(), axum::http::StatusCode::OK);
        let echoed = resp
            .headers()
            .get("x-trace-id")
            .unwrap()
            .to_str()
            .unwrap()
            .to_string();
        assert!(
            TraceId::is_valid(&echoed) && !echoed.starts_with("0000"),
            "{echoed}"
        );
        // handler 内经 extensions 看到的与回显一致
        let bytes = axum::body::to_bytes(resp.into_body(), 1024).await.unwrap();
        let v: serde_json::Value = serde_json::from_slice(&bytes).unwrap();
        assert_eq!(v["data"]["saw"], echoed);
    }

    #[tokio::test]
    async fn prefers_traceparent_then_x_trace_id() {
        // traceparent 优先
        let resp = app()
            .clone()
            .oneshot(
                Request::builder()
                    .uri("/api/v1/ping")
                    .header(
                        "traceparent",
                        "00-0af7651916cd43dd8448eb211c80319c-00f067aa0ba902b7-01",
                    )
                    .header("x-trace-id", "should-be-ignored")
                    .body(Body::empty())
                    .unwrap(),
            )
            .await
            .unwrap();
        assert_eq!(
            resp.headers().get("x-trace-id").unwrap(),
            "0af7651916cd43dd8448eb211c80319c"
        );
        // 无 traceparent 时用 x-trace-id
        let resp = app()
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
