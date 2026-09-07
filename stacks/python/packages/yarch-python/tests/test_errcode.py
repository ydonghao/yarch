# stacks/python/packages/yarch-python/tests/test_errcode.py
import pytest
from yarch_python import errcode

CONTRACT_TABLE = [  # error-codes.md v1.0 逐行誊写（含 0 成功）
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


@pytest.mark.parametrize("code,identifier,message,http", CONTRACT_TABLE)
def test_full_table(code, identifier, message, http):
    assert errcode.identifier_of(code) == identifier
    assert errcode.message_of(code) == message
    assert errcode.http_of(code) == http


def test_1003_reserved_absent():
    assert not hasattr(errcode.Code, "_1003") and 1003 not in [c.value for c in errcode.Code]


def test_register_business_code():
    errcode.register(3001, "USER_EXISTS", "用户已存在", 409)
    assert errcode.message_of(3001) == "用户已存在"
    assert errcode.http_of(3001) == 409
    assert errcode.identifier_of(3001) == "USER_EXISTS"


def test_register_rejects_yarch_range_and_duplicates():
    with pytest.raises(ValueError):
        errcode.register(1001, "X", "x", 400)
    errcode.register(3002, "DUP", "d", 400)
    with pytest.raises(ValueError):
        errcode.register(3002, "DUP", "d", 400)
