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
    let cost_ms = start.elapsed().as_millis() as i64;
    tracing::info!(
        target: "yarch_axum::middleware::access_log",
        message = "request completed",
        trace_id = %trace_id,
        method = %method,
        path = %path,
        status,
        cost_ms = cost_ms
    );
    resp
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::sync::{Arc, Mutex};

    use axum::body::Body;
    use axum::http::StatusCode;
    use tower::ServiceExt;

    #[tokio::test]
    async fn logs_request_completed_with_status_and_trace() {
        let sink = crate::logx::CaptureWriter(Arc::new(Mutex::new(Vec::new())));
        let sub = crate::logx::subscriber_for_test("ycomp", "local", sink.clone());
        let _guard = tracing::subscriber::set_default(sub);
        let app = axum::Router::new()
            .route(
                "/api/v1/jobs",
                axum::routing::get(|| async { crate::web::ok(serde_json::json!({"a":1})) }),
            )
            // AccessLog 在外层、Trace 在内层（生产序）——traceId 从响应头快照
            .layer(axum::middleware::from_fn(access_log_mw))
            .layer(axum::middleware::from_fn(
                crate::middleware::trace::trace_mw,
            ));
        let resp = app
            .oneshot(
                Request::builder()
                    .uri("/api/v1/jobs")
                    .body(Body::empty())
                    .unwrap(),
            )
            .await
            .unwrap();
        assert_eq!(resp.status(), StatusCode::OK);
        let buf = sink.0.lock().unwrap().clone();
        let line = String::from_utf8_lossy(&buf);
        assert!(line.contains("\"msg\":\"request completed\""), "{line}");
        assert!(line.contains("\"path\":\"/api/v1/jobs\""), "{line}");
        assert!(line.contains("\"status\":\"200\""), "{line}");
        assert!(line.contains("\"traceId\":\""), "{line}");
    }
}
