//! traceId 语义（契约内核）：32 hex 生成与 W3C traceparent 解析。
//!
//! 语义唯一权威 = contract/api/logging-trace.md（W3C traceparent 传播）、
//! contract/api/rest-response.md（traceId 恒等于响应头 X-Trace-Id）。

use rand::Rng;

/// 32 位小写十六进制 traceId（16 字节）。W3C 规定不得全零。
#[derive(Debug, Clone, PartialEq, Eq, Hash)]
pub struct TraceId(String);

impl TraceId {
    /// 生成随机 traceId（W3C：非全零 32 hex）
    pub fn generate() -> Self {
        let v: u128 = rand::rng().random();
        Self(format!("{v:032x}"))
    }

    pub fn as_str(&self) -> &str {
        &self.0
    }

    /// 校验 32 位小写十六进制（W3C 规定小写，大小写敏感）
    pub fn is_valid(s: &str) -> bool {
        s.len() == 32 && s.bytes().all(|b| matches!(b, b'0'..=b'9' | b'a'..=b'f'))
    }
}

impl std::fmt::Display for TraceId {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        f.write_str(&self.0)
    }
}

/// W3C traceparent 解析结果：`00-{trace-id}-{parent-id}-{flags}`
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct TraceParent {
    pub trace_id: String,
    pub parent_span_id: String,
}

/// 解析 W3C traceparent 头（版本 00；trace-id 32 hex 非全零；parent-id 16 hex 非全零；flags 2 hex）。
/// 非法输入返回 None，由调用方决定重新生成或透传。
pub fn parse_traceparent(header: &str) -> Option<TraceParent> {
    let parts: Vec<&str> = header.trim().split('-').collect();
    if parts.len() != 4 || parts[0] != "00" {
        return None;
    }
    let (trace_id, parent_span_id, flags) = (parts[1], parts[2], parts[3]);
    if !TraceId::is_valid(trace_id) || is_all_zero(trace_id) {
        return None;
    }
    if parent_span_id.len() != 16
        || !parent_span_id
            .bytes()
            .all(|b| matches!(b, b'0'..=b'9' | b'a'..=b'f'))
        || is_all_zero(parent_span_id)
    {
        return None;
    }
    if flags.len() != 2
        || !flags
            .bytes()
            .all(|b| matches!(b, b'0'..=b'9' | b'a'..=b'f'))
    {
        return None;
    }
    Some(TraceParent {
        trace_id: trace_id.to_string(),
        parent_span_id: parent_span_id.to_string(),
    })
}

fn is_all_zero(hex: &str) -> bool {
    hex.bytes().all(|b| b == b'0')
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn generated_trace_id_is_32_lowercase_hex() {
        let tid = TraceId::generate();
        assert!(TraceId::is_valid(tid.as_str()), "traceId 形状非法: {tid}");
        assert_ne!(tid.as_str(), "00000000000000000000000000000000");
    }

    #[test]
    fn generated_trace_ids_differ() {
        assert_ne!(TraceId::generate(), TraceId::generate());
    }

    #[test]
    fn is_valid_rejects_bad_shapes() {
        assert!(!TraceId::is_valid(""));
        assert!(!TraceId::is_valid("abc"));
        assert!(!TraceId::is_valid("0AF7651916CD43DD8448EB211C80319C")); // 大写拒绝
        assert!(!TraceId::is_valid("0af7651916cd43dd8448eb211c80319")); // 31 位
        assert!(TraceId::is_valid("0af7651916cd43dd8448eb211c80319c"));
    }

    #[test]
    fn parses_valid_traceparent() {
        let tp =
            parse_traceparent("00-0af7651916cd43dd8448eb211c80319c-00f067aa0ba902b7-01").unwrap();
        assert_eq!(tp.trace_id, "0af7651916cd43dd8448eb211c80319c");
        assert_eq!(tp.parent_span_id, "00f067aa0ba902b7");
        // 首尾空白容忍
        assert!(
            parse_traceparent("  00-0af7651916cd43dd8448eb211c80319c-00f067aa0ba902b7-01  ")
                .is_some()
        );
    }

    #[test]
    fn rejects_invalid_traceparent() {
        let cases = [
            "",                                                         // 空
            "0af7651916cd43dd8448eb211c80319c",                         // 非 traceparent 形态
            "ff-0af7651916cd43dd8448eb211c80319c-00f067aa0ba902b7-01",  // 非 00 版本
            "00-00000000000000000000000000000000-00f067aa0ba902b7-01",  // 全零 trace-id
            "00-0af7651916cd43dd8448eb211c80319-00f067aa0ba902b7-01",   // 31 位 trace-id
            "00-0af7651916cd43dd8448eb211c80319c-0000000000000000-01",  // 全零 parent-id
            "00-0af7651916cd43dd8448eb211c80319c-00f067aa0ba902b7-1",   // 1 位 flags
            "00-0af7651916cd43dd8448eb211c80319c-00f067aa0ba902b7-xyz", // flags 非法字符
        ];
        for case in cases {
            assert!(parse_traceparent(case).is_none(), "应拒绝: {case}");
        }
    }
}
