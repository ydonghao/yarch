//! 幂等中间件（rest-conventions.md 幂等总则-1）：同键同参回放/异参 1007/并发短暂等待。
//! 对齐 python IdempotencyMiddleware：acquired→执行并捕获；replay→回放；pending→
//! 轮询 5×200ms 后仍无 done 即 1007；mismatch→1007；5xx/异常→release 不落 done。

use std::panic::{resume_unwind, AssertUnwindSafe};
use std::time::Duration;

use axum::body::{to_bytes, Body};
use axum::extract::{Request, State};
use axum::http::HeaderValue;
use axum::middleware::Next;
use axum::response::Response;
use futures_util::FutureExt;
use sha2::{Digest, Sha256};

use crate::middleware::IdemState;
use crate::store::AcquireState;
use crate::web;

/// 幂等捕获的请求/响应体上限（python 无上限；此处 16MB 护栏——超限视为格式错误不落 done）。
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
    matches!(
        *method,
        axum::http::Method::POST
            | axum::http::Method::PUT
            | axum::http::Method::PATCH
            | axum::http::Method::DELETE
    )
}

pub async fn idempotency_mw(State(state): State<IdemState>, req: Request, next: Next) -> Response {
    if !is_unsafe(req.method()) {
        return next.run(req).await;
    }
    let raw_key: String = req
        .headers()
        .get("idempotency-key")
        .and_then(|v| v.to_str().ok())
        .map(str::trim)
        .unwrap_or_default()
        .to_string();
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
    let (resp_parts, resp_body) = resp.into_parts();
    let bytes = match to_bytes(resp_body, CAPTURE_LIMIT).await {
        Ok(bytes) => bytes,
        Err(_) => {
            state.store.release(&key);
            return web::err(1000);
        }
    };
    let status = resp_parts.status.as_u16();
    if status >= 500 {
        // 5xx 不落 done：释放占位允许重试（java IdempotencyInterceptor 同口径）
        state.store.release(&key);
    } else {
        state
            .store
            .store_response(&key, status, String::from_utf8_lossy(&bytes).to_string());
    }
    Response::from_parts(resp_parts, Body::from(bytes))
}

fn conflict() -> Response {
    web::err_with_message(1007, "幂等冲突：重复提交：Idempotency-Key 冲突".to_string())
}

fn raw_json_response(status: u16, body: String) -> Response {
    let mut resp = Response::new(Body::from(body));
    *resp.status_mut() = axum::http::StatusCode::from_u16(status)
        .unwrap_or(axum::http::StatusCode::INTERNAL_SERVER_ERROR);
    resp.headers_mut()
        .insert("content-type", HeaderValue::from_static("application/json"));
    resp
}

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
