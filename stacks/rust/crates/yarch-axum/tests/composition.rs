//! 组合语义回归（对齐 python test_middleware_composition.py 四例）：
//! Rate×Idem 顺序 / 异常释放 / 5xx 不落 done / 2xx 正常回放。

use std::collections::HashMap;
use std::sync::{Arc, Mutex};
use std::time::Duration;

use axum::body::{to_bytes, Body};
use axum::extract::Request;
use axum::http::StatusCode;
use axum::response::Response;
use axum::routing::post;
use axum::Router;
use tower::ServiceExt;
use yarch_axum::store::{AcquireState, IdempotencyStore, RateLimiter};
use yarch_axum::{setup, IdemConfig, Options, RateConfig};

/// (digest, status?, body?) —— status None = pending
type FakeEntry = (String, Option<u16>, Option<String>);

/// 幂等存储假件：三态行为对齐 InMemoryIdempotencyStore，条目可直接观测。
#[derive(Clone, Default)]
struct FakeStore {
    entries: Arc<Mutex<HashMap<String, FakeEntry>>>,
    releases: Arc<Mutex<Vec<String>>>,
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

async fn panic_handler() -> Response {
    panic!("x")
}

fn make_app(store: FakeStore, limiter: ToggleLimiter) -> (Router, Arc<Mutex<u32>>) {
    let count = Arc::new(Mutex::new(0u32));
    let c = count.clone();
    let jobs = move || {
        let mut n = c.lock().unwrap();
        *n += 1;
        let seq = *n;
        drop(n);
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
        panic_handler()
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
            idempotency: Some(IdemConfig {
                store: Arc::new(store),
            }),
            rate: Some(RateConfig {
                limiter: Arc::new(limiter),
                limit: 10,
                window: Duration::from_secs(60),
            }),
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

async fn status_and_envelope(resp: Response) -> (StatusCode, serde_json::Value) {
    let status = resp.status();
    let bytes = to_bytes(resp.into_body(), 1024 * 1024).await.unwrap();
    (status, serde_json::from_slice(&bytes).unwrap())
}

#[tokio::test]
async fn rate_limited_request_must_not_poison_idempotency() {
    // 限流拒绝不得被幂等层捕获落库：限流恢复后同键重试必须真正执行（而非 24h 内回放 429）
    let store = FakeStore::default();
    let limiter = ToggleLimiter::default();
    let (app, count) = make_app(store.clone(), limiter.clone());

    limiter.deny_next();
    let (s, v) = status_and_envelope(
        app.clone()
            .oneshot(post_json("/api/v1/jobs", "job-1", r#"{"a":1}"#))
            .await
            .unwrap(),
    )
    .await;
    assert_eq!(s, StatusCode::TOO_MANY_REQUESTS);
    assert_eq!(v["code"], 1006, "限流信封 code=1006：{v}");
    assert_eq!(*count.lock().unwrap(), 0, "被限流的请求不得触达业务");
    assert!(
        store.entries.lock().unwrap().is_empty(),
        "限流拒绝不得在幂等存储留下任何条目"
    );

    let (s, v) = status_and_envelope(
        app.oneshot(post_json("/api/v1/jobs", "job-1", r#"{"a":1}"#))
            .await
            .unwrap(),
    )
    .await;
    assert_eq!(s, StatusCode::CREATED);
    assert_eq!(v["data"]["seq"], 1, "限流恢复后同键重试必须真正执行：{v}");
    assert!(
        !store.entries.lock().unwrap().is_empty(),
        "正常执行后应落 done 供后续回放"
    );
}

#[tokio::test]
async fn exception_releases_pending_without_partial_done() {
    // 执行异常：占位释放、残缺响应不落库——同键重试可再执行
    let store = FakeStore::default();
    let limiter = ToggleLimiter::default();
    let (app, count) = make_app(store.clone(), limiter);

    let (s, v) = status_and_envelope(
        app.clone()
            .oneshot(post_json("/api/v1/boom", "boom-1", r#"{"a":1}"#))
            .await
            .unwrap(),
    )
    .await;
    assert_eq!(s, StatusCode::INTERNAL_SERVER_ERROR);
    assert_eq!(v["code"], 1000, "{v}");
    assert!(
        store.entries.lock().unwrap().is_empty(),
        "异常后不得残留 pending 或残缺 done"
    );
    assert!(
        store
            .releases
            .lock()
            .unwrap()
            .iter()
            .any(|k| k.contains(":idem:")),
        "异常路径必须 release"
    );

    let (s, _) = status_and_envelope(
        app.oneshot(post_json("/api/v1/boom", "boom-1", r#"{"a":1}"#))
            .await
            .unwrap(),
    )
    .await;
    assert_eq!(s, StatusCode::INTERNAL_SERVER_ERROR);
    assert_eq!(*count.lock().unwrap(), 2, "同键重试必须重新执行");
}

#[tokio::test]
async fn five_xx_response_not_stored_as_done() {
    // 5xx 响应不落 done：释放占位允许重试（与 java 栈 IdempotencyInterceptor 口径一致）
    let store = FakeStore::default();
    let limiter = ToggleLimiter::default();
    let (app, count) = make_app(store.clone(), limiter);

    let (s, _) = status_and_envelope(
        app.clone()
            .oneshot(post_json("/api/v1/flaky", "flaky-1", r#"{"a":1}"#))
            .await
            .unwrap(),
    )
    .await;
    assert_eq!(s, StatusCode::INTERNAL_SERVER_ERROR);
    assert_eq!(*count.lock().unwrap(), 1);
    assert!(
        store.entries.lock().unwrap().is_empty(),
        "5xx 不得作为可回放 done 存储"
    );

    let (s, _) = status_and_envelope(
        app.oneshot(post_json("/api/v1/flaky", "flaky-1", r#"{"a":1}"#))
            .await
            .unwrap(),
    )
    .await;
    assert_eq!(s, StatusCode::INTERNAL_SERVER_ERROR);
    assert_eq!(*count.lock().unwrap(), 2, "同键重试必须重新执行");
}

#[tokio::test]
async fn two_xx_still_replays_normally() {
    // 回归护栏：正常 2xx 语义不变——同键同参回放、不重复执行
    let store = FakeStore::default();
    let limiter = ToggleLimiter::default();
    let (app, count) = make_app(store, limiter);

    let (s1, v1) = status_and_envelope(
        app.clone()
            .oneshot(post_json("/api/v1/jobs", "job-9", r#"{"a":1}"#))
            .await
            .unwrap(),
    )
    .await;
    let (s2, v2) = status_and_envelope(
        app.oneshot(post_json("/api/v1/jobs", "job-9", r#"{"a":1}"#))
            .await
            .unwrap(),
    )
    .await;
    assert_eq!(s1, StatusCode::CREATED);
    assert_eq!(s2, StatusCode::CREATED);
    assert_eq!(v1, v2, "同键同参必须回放原响应");
    assert_eq!(v1["data"]["seq"], 1);
    assert_eq!(*count.lock().unwrap(), 1, "只执行一次");
}
