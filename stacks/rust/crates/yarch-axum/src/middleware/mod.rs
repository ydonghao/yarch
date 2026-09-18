//! 中间件五件（组合语义 spec 六-2；外→内 AccessLog > Trace > Recovery > Rate > Idem）。
//! 模块随任务逐件落地，本文件逐件挂载。

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
