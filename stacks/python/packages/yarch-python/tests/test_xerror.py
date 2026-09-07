# stacks/python/packages/yarch-python/tests/test_xerror.py
from yarch_python.xerror import BizError


def test_message_append_rule_with_fullwidth_colon():
    e = BizError(1001, detail="pageSize 必须 ≤ 100")
    assert e.message == "参数校验失败：pageSize 必须 ≤ 100"


def test_message_no_detail_is_default():
    assert BizError(1004).message == "资源不存在"


def test_default_part_never_rewritten():
    e = BizError(1006, detail="qps>100")
    assert e.message.startswith("触发限流：")


def test_http_mapping():
    assert BizError(1007).http_status == 409
    assert BizError(2003).http_status == 403


def test_explicit_message_passthrough():
    e = BizError(3004, message="订单状态不允许该操作")
    assert e.message == "订单状态不允许该操作"


def test_raisable():
    import pytest

    with pytest.raises(BizError) as ei:
        raise BizError(1002, detail="bad json")
    assert ei.value.code == 1002
