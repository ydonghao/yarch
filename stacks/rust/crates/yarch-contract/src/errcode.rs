//! 错误码段位表（契约内核）。
//!
//! 唯一权威 = contract/api/error-codes.md（机器可读派生层 contract/dist/error-codes.json，
//! 同源断言见 tests/conformance.rs）。yarch 只拥有 0、1xxx、2xxx；3xxx-8xxx 由业务仓
//! 登记后方可使用（[`register`]）；9xxx 预留不得使用。

use std::collections::HashMap;
use std::sync::RwLock;

/// 错误码元数据（不可变值对象）。
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct ErrCode {
    /// 业务码。0 = 成功；非 0 见段位表
    pub code: i32,
    /// UPPER_SNAKE 标识（与 dist json 的 key 同形）
    pub key: &'static str,
    /// 默认中文文案（可直接作为前端降级文案）
    pub message: &'static str,
    /// 固定 HTTP 状态码映射（服务端不得自行发挥）
    pub http: u16,
}

impl ErrCode {
    const fn new(code: i32, key: &'static str, message: &'static str, http: u16) -> Self {
        Self {
            code,
            key,
            message,
            http,
        }
    }
}

/// 成功（code = 0）
pub const OK: ErrCode = ErrCode::new(0, "OK", "成功", 200);

// 通用段 1xxx
pub const INTERNAL_ERROR: ErrCode = ErrCode::new(1000, "INTERNAL_ERROR", "内部错误", 500);
pub const INVALID_ARGUMENT: ErrCode = ErrCode::new(1001, "INVALID_ARGUMENT", "参数校验失败", 400);
pub const MALFORMED_BODY: ErrCode = ErrCode::new(1002, "MALFORMED_BODY", "请求体格式错误", 400);
pub const NOT_FOUND: ErrCode = ErrCode::new(1004, "NOT_FOUND", "资源不存在", 404);
pub const CONFLICT: ErrCode = ErrCode::new(1005, "CONFLICT", "资源冲突", 409);
pub const RATE_LIMITED: ErrCode = ErrCode::new(1006, "RATE_LIMITED", "触发限流", 429);
pub const IDEMPOTENCY_CONFLICT: ErrCode =
    ErrCode::new(1007, "IDEMPOTENCY_CONFLICT", "幂等冲突：重复提交", 409);
pub const UPSTREAM_TIMEOUT: ErrCode = ErrCode::new(1008, "UPSTREAM_TIMEOUT", "上游依赖超时", 504);
pub const UNAVAILABLE: ErrCode = ErrCode::new(1009, "UNAVAILABLE", "服务暂不可用", 503);

// 认证与权限段 2xxx
pub const UNAUTHORIZED: ErrCode = ErrCode::new(2001, "UNAUTHORIZED", "未认证", 401);
pub const CREDENTIALS_EXPIRED: ErrCode =
    ErrCode::new(2002, "CREDENTIALS_EXPIRED", "凭证已过期", 401);
pub const FORBIDDEN: ErrCode = ErrCode::new(2003, "FORBIDDEN", "权限不足", 403);
pub const ACCOUNT_DISABLED: ErrCode = ErrCode::new(2004, "ACCOUNT_DISABLED", "账号已禁用", 403);
pub const CAPTCHA_INVALID: ErrCode = ErrCode::new(2005, "CAPTCHA_INVALID", "验证码校验失败", 400);

/// 内建全表（0 + 1xxx + 2xxx；同源断言对表用）
pub const BUILTIN: &[ErrCode] = &[
    OK,
    INTERNAL_ERROR,
    INVALID_ARGUMENT,
    MALFORMED_BODY,
    NOT_FOUND,
    CONFLICT,
    RATE_LIMITED,
    IDEMPOTENCY_CONFLICT,
    UPSTREAM_TIMEOUT,
    UNAVAILABLE,
    UNAUTHORIZED,
    CREDENTIALS_EXPIRED,
    FORBIDDEN,
    ACCOUNT_DISABLED,
    CAPTCHA_INVALID,
];

/// 业务段边界：3xxx-8xxx（9xxx 预留不得使用）
const BUSINESS_MIN: i32 = 3000;
const BUSINESS_MAX: i32 = 8999;

/// 业务码登记失败（对齐 golang panic 语义；Rust 以 Result 承载，装配期调用方 `.expect` fail-fast）
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum RegisterError {
    /// 段位越界（仅 3xxx-8xxx 可登记）
    OutOfRange(i32),
    /// HTTP 状态码不在白名单
    HttpNotAllowed(u16),
    /// 同 code 已登记且元信息不同
    Conflict {
        code: i32,
        existing_key: &'static str,
    },
}

static BUSINESS: RwLock<Option<HashMap<i32, ErrCode>>> = RwLock::new(None);

/// 查码：内建表 → 业务表 → 回退 INTERNAL_ERROR（未登记 code 视为实现缺陷，兜底内部错误语义）。
pub fn lookup(code: i32) -> ErrCode {
    if let Some(c) = BUILTIN.iter().find(|c| c.code == code) {
        return *c;
    }
    if let Ok(guard) = BUSINESS.read() {
        if let Some(c) = guard.as_ref().and_then(|m| m.get(&code)) {
            return *c;
        }
    }
    INTERNAL_ERROR
}

/// code 是否已登记（内建或业务段）
pub fn is_registered(code: i32) -> bool {
    if BUILTIN.iter().any(|c| c.code == code) {
        return true;
    }
    BUSINESS
        .read()
        .map(|g| g.as_ref().is_some_and(|m| m.contains_key(&code)))
        .unwrap_or(false)
}

/// 登记业务码。幂等：同 code 同元信息重复登记放行；元信息冲突返回 [`RegisterError::Conflict`]。
pub fn register(
    code: i32,
    key: &'static str,
    message: &'static str,
    http: u16,
) -> Result<(), RegisterError> {
    if !(BUSINESS_MIN..=BUSINESS_MAX).contains(&code) {
        return Err(RegisterError::OutOfRange(code));
    }
    if !http_allowed(http) {
        return Err(RegisterError::HttpNotAllowed(http));
    }
    let mut guard = BUSINESS
        .write()
        .expect("errcode business registry poisoned");
    let map = guard.get_or_insert_with(HashMap::new);
    match map.get(&code) {
        Some(existing)
            if existing.key == key && existing.message == message && existing.http == http =>
        {
            Ok(())
        }
        Some(existing) => Err(RegisterError::Conflict {
            code,
            existing_key: existing.key,
        }),
        None => {
            map.insert(code, ErrCode::new(code, key, message, http));
            Ok(())
        }
    }
}

/// rest-conventions.md 业务 API 状态码白名单（对齐 golang httpAllowed）
fn http_allowed(status: u16) -> bool {
    matches!(
        status,
        200 | 201 | 400 | 401 | 403 | 404 | 409 | 429 | 500 | 503 | 504
    )
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn ok_constant() {
        assert_eq!(
            (OK.code, OK.key, OK.message, OK.http),
            (0, "OK", "成功", 200)
        );
    }

    #[test]
    fn builtin_table_is_unique() {
        let mut codes: Vec<i32> = BUILTIN.iter().map(|c| c.code).collect();
        let total = codes.len();
        codes.sort_unstable();
        codes.dedup();
        assert_eq!(codes.len(), total, "内建码表存在重复 code");
        let mut keys: Vec<&str> = BUILTIN.iter().map(|c| c.key).collect();
        let ktotal = keys.len();
        keys.sort_unstable();
        keys.dedup();
        assert_eq!(keys.len(), ktotal, "内建码表存在重复 key");
    }

    #[test]
    fn unregistered_falls_back_to_internal_error() {
        assert!(!is_registered(9999));
        let c = lookup(9999);
        assert_eq!((c.code, c.message, c.http), (1000, "内部错误", 500));
    }

    #[test]
    fn register_business_code() {
        // 测试共享全局表：各测试用不同 code 避免相互污染
        assert!(register(3998, "DEMO_BUSY", "示例繁忙", 409).is_ok());
        // 幂等重登放行
        assert!(register(3998, "DEMO_BUSY", "示例繁忙", 409).is_ok());
        // 元信息冲突拒绝
        assert_eq!(
            register(3998, "OTHER", "其他", 409),
            Err(RegisterError::Conflict {
                code: 3998,
                existing_key: "DEMO_BUSY"
            })
        );
        let c = lookup(3998);
        assert_eq!(
            (c.code, c.key, c.message, c.http),
            (3998, "DEMO_BUSY", "示例繁忙", 409)
        );
        assert!(is_registered(3998));
    }

    #[test]
    fn register_rejects_out_of_range() {
        assert_eq!(
            register(2999, "X", "x", 400),
            Err(RegisterError::OutOfRange(2999))
        );
        assert_eq!(
            register(9000, "X", "x", 400),
            Err(RegisterError::OutOfRange(9000))
        );
    }

    #[test]
    fn register_rejects_http_outside_whitelist() {
        assert_eq!(
            register(3997, "X", "x", 204),
            Err(RegisterError::HttpNotAllowed(204))
        );
    }
}
