"""testx：契约断言（码表/信封形状/ndjson 字段级）——跨栈 conformance 同表，防方言漂移。"""
import json
import re

# error-codes.md v1.0 逐行誊写（0 成功 + 13 码）；与 tests/test_errcode.py 的 CONTRACT_TABLE
# 同源同值（14 行），业务工程直接 import 本表做跨栈 conformance
CODE_TABLE: list[tuple[int, str, str, int]] = [
    (0, "OK", "成功", 200),
    (1000, "INTERNAL_ERROR", "内部错误", 500),
    (1001, "INVALID_ARGUMENT", "参数校验失败", 400),
    (1002, "MALFORMED_BODY", "请求体格式错误", 400),
    (1004, "NOT_FOUND", "资源不存在", 404),
    (1005, "CONFLICT", "资源冲突", 409),
    (1006, "RATE_LIMITED", "触发限流", 429),
    (1007, "IDEMPOTENCY_CONFLICT", "幂等冲突：重复提交", 409),
    (1008, "UPSTREAM_TIMEOUT", "上游依赖超时", 504),
    (1009, "UNAVAILABLE", "服务暂不可用", 503),
    (2001, "UNAUTHORIZED", "未认证", 401),
    (2002, "CREDENTIALS_EXPIRED", "凭证已过期", 401),
    (2003, "FORBIDDEN", "权限不足", 403),
    (2004, "ACCOUNT_DISABLED", "账号已禁用", 403),
]

# 与 logx 行协议同口径：毫秒精度 UTC ts + 大写 level（protocol_fields 全集）
_TS_RE = re.compile(r"^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d{3}Z$")
_LEVELS = {"TRACE", "DEBUG", "INFO", "WARN", "ERROR"}
_REQUIRED_KEYS = ["ts", "level", "service", "env", "traceId", "logger", "msg"]


def assert_envelope(payload: dict, *, code: int, message_prefix: str | None = None) -> None:
    assert list(payload.keys()) == ["code", "message", "data", "traceId"], payload
    assert payload["code"] == code, payload
    if code != 0:
        assert payload["data"] is None, payload
    if message_prefix is not None:
        assert payload["message"].startswith(message_prefix), payload


def assert_page_data(data: dict, *, total: int, page: int, page_size: int) -> None:
    assert data["list"] is not None and isinstance(data["list"], list)
    assert data["total"] == total and data["page"] == page and data["pageSize"] == page_size


def assert_ndjson(line: str, *, service: str | None = None, msg: str | None = None) -> None:
    d = json.loads(line)
    for k in _REQUIRED_KEYS:
        assert k in d, f"ndjson 缺字段 {k}: {line}"
    assert _TS_RE.match(d["ts"]), d["ts"]
    assert d["level"] in _LEVELS, d["level"]
    if service is not None:
        assert d["service"] == service
    if msg is not None:
        assert d["msg"] == msg
