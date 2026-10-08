//! 入口层：协议转换（DTO/校验/路由），零业务。

pub mod users;

use std::sync::Arc;

use axum::response::Response;
use serde::Deserialize;

use crate::domain::repository::UserRepository;
use yarch_axum::web;

#[derive(Clone)]
pub struct AppState {
    pub users: Arc<dyn UserRepository>,
}

pub async fn healthz() -> Response {
    web::ok(serde_json::json!({ "status": "up" }))
}

/// 未匹配路由兜底：404 → 1004 信封（禁裸 404 文本——前端解包不失效）
pub async fn not_found() -> Response {
    web::err(1004)
}

#[derive(Deserialize)]
pub struct EchoIn {
    pub name: String,
}

/// 演示端点：信封回包 + 幂等中间件联动（带 Idempotency-Key 即受 1007/回放保护）
pub async fn echo(axum::Json(body): axum::Json<EchoIn>) -> Response {
    web::ok(serde_json::json!({ "greeting": format!("hello, {}", body.name) }))
}
