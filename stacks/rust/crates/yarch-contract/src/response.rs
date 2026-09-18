//! REST 信封（契约内核）：四字段 camelCase，`code != 0` 时 `data` 必须为 `null`。
//!
//! 语义唯一权威 = contract/api/rest-response.md。

use serde::{Deserialize, Serialize};

use crate::errcode;

/// 响应信封。字段名 camelCase（traceId 经 serde rename），任何栈不得增删改名。
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct RestResponse<T> {
    /// 业务码。0 = 成功；非 0 见 errcode 段位表
    pub code: i32,
    /// 人类可读文案；不得携带堆栈/内部细节
    pub message: String,
    /// 业务负载。code != 0 时必须为 null
    pub data: Option<T>,
    /// 追踪 ID，恒等于响应头 X-Trace-Id；取不到时为空串
    #[serde(rename = "traceId")]
    pub trace_id: String,
}

impl<T> RestResponse<T> {
    /// 成功且带负载
    pub fn ok(data: T, trace_id: impl Into<String>) -> Self {
        Self {
            code: errcode::OK.code,
            message: errcode::OK.message.to_string(),
            data: Some(data),
            trace_id: trace_id.into(),
        }
    }

    /// 成功且无负载（data = null）
    pub fn ok_empty(trace_id: impl Into<String>) -> Self {
        Self {
            code: errcode::OK.code,
            message: errcode::OK.message.to_string(),
            data: None,
            trace_id: trace_id.into(),
        }
    }

    /// 业务失败：message 取码表默认文案；data 恒 None。
    /// 未登记 code 回退 INTERNAL_ERROR（整个信封按 1000 语义出，防止未定义码泄漏）。
    pub fn error(code: i32, trace_id: impl Into<String>) -> Self {
        let c = errcode::lookup(code);
        Self {
            code: c.code,
            message: c.message.to_string(),
            data: None,
            trace_id: trace_id.into(),
        }
    }

    /// 业务失败 + 覆盖文案（按「默认文案：细节」规则由调用方拼装）
    pub fn error_with_message(
        code: i32,
        message: impl Into<String>,
        trace_id: impl Into<String>,
    ) -> Self {
        let c = errcode::lookup(code);
        Self {
            code: c.code,
            message: message.into(),
            data: None,
            trace_id: trace_id.into(),
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[derive(Serialize, Deserialize, PartialEq, Debug)]
    struct Payload {
        value: i32,
    }

    #[test]
    fn ok_serializes_to_exact_envelope() {
        let resp = RestResponse::ok(Payload { value: 1 }, "0af7651916cd43dd8448eb211c80319c");
        let json = serde_json::to_string(&resp).unwrap();
        assert_eq!(
            json,
            r#"{"code":0,"message":"成功","data":{"value":1},"traceId":"0af7651916cd43dd8448eb211c80319c"}"#
        );
    }

    #[test]
    fn error_serializes_data_null() {
        let resp: RestResponse<Payload> = RestResponse::error(1001, "t");
        let json = serde_json::to_string(&resp).unwrap();
        assert_eq!(
            json,
            r#"{"code":1001,"message":"参数校验失败","data":null,"traceId":"t"}"#
        );
    }

    #[test]
    fn ok_empty_keeps_data_null() {
        let resp: RestResponse<Payload> = RestResponse::ok_empty("t");
        assert_eq!(resp.code, 0);
        assert!(resp.data.is_none());
        let json = serde_json::to_string(&resp).unwrap();
        assert_eq!(
            json,
            r#"{"code":0,"message":"成功","data":null,"traceId":"t"}"#
        );
    }

    #[test]
    fn unregistered_code_falls_back_to_internal_error() {
        let resp: RestResponse<Payload> = RestResponse::error(9999, "t");
        assert_eq!(resp.code, 1000);
        assert_eq!(resp.message, "内部错误");
    }

    #[test]
    fn error_with_message_overrides() {
        let resp: RestResponse<Payload> =
            RestResponse::error_with_message(1001, "参数校验失败：pageSize 必须 ≤ 100", "t");
        let json = serde_json::to_string(&resp).unwrap();
        assert_eq!(
            json,
            r#"{"code":1001,"message":"参数校验失败：pageSize 必须 ≤ 100","data":null,"traceId":"t"}"#
        );
    }

    #[test]
    fn deserialize_round_trip() {
        let resp = RestResponse::ok(Payload { value: 7 }, "tid");
        let json = serde_json::to_string(&resp).unwrap();
        let back: RestResponse<Payload> = serde_json::from_str(&json).unwrap();
        assert_eq!(back, resp);
    }
}
