# stacks/python/packages/yarch-python/tests/test_errcode.py
"""错误码契约断言：13 码全表唯一权威 = contract/dist/error-codes.json（由 error-codes.md
派生，CI 拒双向漂移）——四栈读同一份 json 断言，不再各养手抄表（P1 契约机器可读出口）。
dist 文件不在场（消费方独立环境）则整表跳过，CI 仓内必跑。"""

import json
from pathlib import Path

import pytest
from yarch_python import errcode

_DIST = Path(__file__).resolve().parents[5] / "contract" / "dist" / "error-codes.json"


def _dist_rows() -> list[tuple[int, str, str, int]]:
    if not _DIST.exists():
        return []
    data = json.loads(_DIST.read_text())
    return [(c["code"], c["key"], c["message"], c["http"]) for c in data["codes"]]


DIST_TABLE = _dist_rows()

# dist 缺场时给一个带 skip 标记的占位参数（parametrize 空表会静默全过，占位保证显式跳过）
_SKIP = pytest.mark.skip(reason="contract dist json 不在场")
_FALLBACK = [pytest.param(0, None, None, None, marks=_SKIP)]


@pytest.mark.parametrize("code,identifier,message,http", DIST_TABLE or _FALLBACK)
def test_full_table(code, identifier, message, http):
    assert errcode.identifier_of(code) == identifier
    assert errcode.message_of(code) == message
    assert errcode.http_of(code) == http


def test_ok_success_constant():
    """0 成功常量恒在（不依赖 dist 在场）"""
    assert errcode.identifier_of(0) == "OK"
    assert errcode.message_of(0) == "成功"


def test_dist_table_complete_when_present():
    if not _DIST.exists():
        pytest.skip("contract dist json 不在场")
    assert len(DIST_TABLE) == 13


def test_1003_reserved_absent():
    assert not hasattr(errcode.Code, "_1003") and 1003 not in [c.value for c in errcode.Code]


def test_1003_unregistered_raises_lookup_error():
    with pytest.raises(LookupError):
        errcode.message_of(1003)
    with pytest.raises(LookupError):
        errcode.http_of(1003)


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
