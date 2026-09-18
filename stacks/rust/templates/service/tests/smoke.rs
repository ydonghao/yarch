//! 冒烟（无 DB）：装配栈 + 信封 + 404 兜底 + 幂等回放。users 路径见 DATABASE_URL 场景。

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
use {{crate_name}}::infra::user_repo::UserRepoSqlx;

fn app() -> Router {
    errors::register_all();
    // 惰性连接：不触库（冒烟只打无 DB 端点）
    let pool = sqlx::postgres::PgPoolOptions::new()
        .connect_lazy("postgres://postgres:postgres@127.0.0.1:1/none?sslmode=disable")
        .expect("惰性连接构造");
    let state = AppState {
        users: Arc::new(UserRepoSqlx::new(pool)),
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
    let resp = app()
        .oneshot(Request::get("/healthz").body(Body::empty()).unwrap())
        .await
        .unwrap();
    assert_eq!(resp.status(), StatusCode::OK);
    assert!(resp.headers().get("x-trace-id").is_some(), "traceId 回显");
    let v = envelope(resp).await;
    assert_eq!(v["code"], 0);
    assert_eq!(v["message"], "成功");
    assert_eq!(v["data"]["status"], "up");
}

#[tokio::test]
async fn unknown_route_returns_1004_envelope_not_bare_404() {
    let resp = app()
        .oneshot(Request::get("/nope").body(Body::empty()).unwrap())
        .await
        .unwrap();
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
