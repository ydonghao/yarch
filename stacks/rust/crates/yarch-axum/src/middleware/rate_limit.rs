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
