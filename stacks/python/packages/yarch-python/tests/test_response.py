# stacks/python/packages/yarch-python/tests/test_response.py
import json

from yarch_python.response import PageData, error, ok


def test_ok_envelope_shape_and_order():
    raw = ok({"any": "thing"}, trace_id="t1").model_dump_json(by_alias=True)
    assert list(json.loads(raw).keys()) == ["code", "message", "data", "traceId"]
    assert (
        raw.index('"code"') < raw.index('"message"') < raw.index('"data"') < raw.index('"traceId"')
    )
    body = json.loads(raw)
    assert body == {"code": 0, "message": "成功", "data": {"any": "thing"}, "traceId": "t1"}


def test_error_data_must_be_null():
    body = json.loads(
        error(1001, message="参数校验失败", trace_id="t2").model_dump_json(by_alias=True)
    )
    assert body["code"] == 1001 and body["data"] is None and body["traceId"] == "t2"


def test_ok_no_payload_data_is_null():
    assert json.loads(ok().model_dump_json(by_alias=True))["data"] is None


def test_page_data_shape_and_next_cursor_omitted():
    pd = PageData[list](list=[1, 2], total=5, page=1, page_size=2, next_cursor=None)
    d = pd.model_dump(by_alias=True, exclude_none=True)
    assert d == {"list": [1, 2], "total": 5, "page": 1, "pageSize": 2}
    pd2 = PageData[list](list=[], total=5, page=3, page_size=2, next_cursor="c9")
    assert pd2.model_dump(by_alias=True, exclude_none=True)["nextCursor"] == "c9"


def test_page_data_empty_list_not_null():
    assert PageData[list](list=[], total=0, page=1, page_size=20).model_dump()["list"] == []
