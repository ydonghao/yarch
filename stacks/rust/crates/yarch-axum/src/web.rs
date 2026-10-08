//! web：信封回包 helpers（对偶 python web.ok/page；page 随 2b sqlx 装配）。

use axum::body::Body;
use axum::http::{HeaderValue, StatusCode};
use axum::response::Response;
use serde::Serialize;

use crate::logx;
use yarch_contract::errcode;
use yarch_contract::response::RestResponse;

fn envelope_to_response<T: Serialize>(resp: &RestResponse<T>, status: u16) -> Response {
    let body = serde_json::to_vec(resp).expect("信封序列化不可失败");
    Response::builder()
        .status(StatusCode::from_u16(status).unwrap_or(StatusCode::INTERNAL_SERVER_ERROR))
        .header("content-type", HeaderValue::from_static("application/json"))
        .body(Body::from(body))
        .expect("builder 参数常量合法")
}

/// 成功回包（200）。
pub fn ok<T: Serialize>(data: T) -> Response {
    let resp = RestResponse::ok(data, logx::current_trace());
    envelope_to_response(&resp, 200)
}

/// 成功回包（自定义状态码，如 201）。
pub fn ok_status<T: Serialize>(data: T, status: u16) -> Response {
    let resp = RestResponse::ok(data, logx::current_trace());
    envelope_to_response(&resp, status)
}

/// 业务失败回包：状态码 = 码表映射，message = 默认文案。
pub fn err(code: i32) -> Response {
    let resp = RestResponse::<()>::error(code, logx::current_trace());
    let status = errcode::lookup(code).http;
    envelope_to_response(&resp, status)
}

/// 业务失败回包 + 覆盖文案（「默认文案：细节」规则由调用方拼装）。
pub fn err_with_message(code: i32, message: String) -> Response {
    let resp = RestResponse::<()>::error_with_message(code, message, logx::current_trace());
    let status = errcode::lookup(code).http;
    envelope_to_response(&resp, status)
}
