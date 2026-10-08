//! users 端点：分页列表 / 详情 / 创建 / 逻辑删除。

use axum::extract::{Path, Query, State};
use axum::response::Response;
use serde::Deserialize;

use crate::api::AppState;
use crate::errors;
use yarch_axum::web;
use yarch_contract::page::PageQuery;

#[derive(Deserialize)]
pub struct ListParams {
    pub page: Option<String>,
    #[serde(rename = "pageSize")]
    pub page_size: Option<String>,
    pub keyword: Option<String>,
}

pub async fn list(State(state): State<AppState>, Query(params): Query<ListParams>) -> Response {
    let q = match PageQuery::from_params(params.page.as_deref(), params.page_size.as_deref()) {
        Ok(q) => q,
        Err(detail) => return web::err_with_message(1001, format!("参数校验失败：{detail}")),
    };
    match state.users.page(&q, params.keyword.as_deref()).await {
        Ok(pd) => web::ok(pd),
        Err(_) => web::err(1000),
    }
}

pub async fn get_one(State(state): State<AppState>, Path(id): Path<i64>) -> Response {
    match state.users.find(id).await {
        Ok(Some(user)) => web::ok(user),
        Ok(None) => web::err(1004),
        Err(_) => web::err(1000),
    }
}

#[derive(Deserialize)]
pub struct CreateIn {
    pub name: String,
}

pub async fn create(State(state): State<AppState>, axum::Json(body): axum::Json<CreateIn>) -> Response {
    let name = body.name.trim().to_string();
    if name.is_empty() {
        return web::err_with_message(1001, "参数校验失败：name 不得为空".to_string());
    }
    match state.users.create(&name).await {
        Ok(user) => web::ok_status(user, 201),
        Err(e) if e == errors::user_name_taken_message() => web::err(3001),
        Err(_) => web::err(1000),
    }
}

pub async fn remove(State(state): State<AppState>, Path(id): Path<i64>) -> Response {
    match state.users.soft_delete(id).await {
        Ok(true) => web::ok(serde_json::json!({ "removed": id })),
        Ok(false) => web::err(1004),
        Err(_) => web::err(1000),
    }
}
