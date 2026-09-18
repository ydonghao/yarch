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
            middleware::IdemState {
                store: idem.store,
                service: opts.service.clone(),
            },
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
        .layer(axum::middleware::from_fn(
            middleware::access_log::access_log_mw,
        ))
}
