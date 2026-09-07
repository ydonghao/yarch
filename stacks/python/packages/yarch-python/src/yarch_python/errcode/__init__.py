# stacks/python/packages/yarch-python/src/yarch_python/errcode/__init__.py
"""契约内核②：错误码全局段位表（error-codes.md v1.0，13 码全表 + HTTP 映射 + 业务码注册）。"""
from enum import IntEnum


class Code(IntEnum):
    OK = 0
    INTERNAL_ERROR = 1000
    INVALID_ARGUMENT = 1001
    MALFORMED_BODY = 1002
    NOT_FOUND = 1004
    CONFLICT = 1005
    RATE_LIMITED = 1006
    IDEMPOTENCY_CONFLICT = 1007
    UPSTREAM_TIMEOUT = 1008
    UNAVAILABLE = 1009
    UNAUTHORIZED = 2001
    CREDENTIALS_EXPIRED = 2002
    FORBIDDEN = 2003
    ACCOUNT_DISABLED = 2004


_MESSAGES = {
    0: "成功",
    1000: "内部错误",
    1001: "参数校验失败",
    1002: "请求体格式错误",
    1004: "资源不存在",
    1005: "资源冲突",
    1006: "触发限流",
    1007: "幂等冲突：重复提交",
    1008: "上游依赖超时",
    1009: "服务暂不可用",
    2001: "未认证",
    2002: "凭证已过期",
    2003: "权限不足",
    2004: "账号已禁用",
}
_HTTP = {
    0: 200,
    1000: 500,
    1001: 400,
    1002: 400,
    1004: 404,
    1005: 409,
    1006: 429,
    1007: 409,
    1008: 504,
    1009: 503,
    2001: 401,
    2002: 401,
    2003: 403,
    2004: 403,
}
_IDENTIFIERS = {c.value: c.name for c in Code}
# 业务码登记表（3xxx-8xxx，业务仓 docs 登记后 register）
_BUSINESS: dict[int, tuple[str, str, int]] = {}


def identifier_of(code: int) -> str:
    if code in _IDENTIFIERS:
        return _IDENTIFIERS[code]
    if code in _BUSINESS:
        return _BUSINESS[code][0]
    raise LookupError(f"未注册的错误码：{code}（业务码 3xxx-8xxx 须先 register 并在业务仓 docs 登记）")


def message_of(code: int) -> str:
    if code in _MESSAGES:
        return _MESSAGES[code]
    if code in _BUSINESS:
        return _BUSINESS[code][1]
    raise LookupError(f"未注册的错误码：{code}")


def http_of(code: int) -> int:
    if code in _HTTP:
        return _HTTP[code]
    if code in _BUSINESS:
        return _BUSINESS[code][2]
    raise LookupError(f"未注册的错误码：{code}")


def register(code: int, identifier: str, message: str, http_status: int) -> None:
    if not 3000 <= code <= 8999:
        raise ValueError(f"yarch 只拥有 0/1xxx/2xxx；业务码须在 3000-8999，got {code}")
    if code in _BUSINESS or code in _IDENTIFIERS:
        raise ValueError(f"错误码 {code} 已注册：{identifier_of(code)}")
    if not identifier.isupper() or not identifier.replace("_", "").isalpha():
        raise ValueError(f"标识符须为 SCREAMING_SNAKE：{identifier}")
    _BUSINESS[code] = (identifier, message, http_status)
