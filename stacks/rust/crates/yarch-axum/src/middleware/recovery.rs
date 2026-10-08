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

#[cfg(test)]
mod tests {
    use super::*;
    use axum::body::Body;
    use axum::http::StatusCode;
    use tower::ServiceExt;

    #[tokio::test]
    async fn panic_becomes_500_envelope_code_1000() {
        async fn boom() -> Response {
            panic!("boom")
        }
        let app = axum::Router::new()
            .route("/api/v1/boom", axum::routing::post(boom))
            .layer(axum::middleware::from_fn(recovery_mw));
        let resp = app
            .oneshot(
                Request::builder()
                    .method("POST")
                    .uri("/api/v1/boom")
                    .body(Body::empty())
                    .unwrap(),
            )
            .await
            .unwrap();
        assert_eq!(resp.status(), StatusCode::INTERNAL_SERVER_ERROR);
        let bytes = axum::body::to_bytes(resp.into_body(), 1024).await.unwrap();
        let v: serde_json::Value = serde_json::from_slice(&bytes).unwrap();
        assert_eq!(v["code"], 1000);
        assert_eq!(v["message"], "内部错误");
    }
}
