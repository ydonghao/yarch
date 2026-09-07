# stacks/python/packages/yarch-python/tests/test_httpx.py
import httpx
import pytest
from yarch_python import logx
from yarch_python.httpx import YarchHttpClient
from yarch_python.xerror import BizError


def env(mocker_routes) -> YarchHttpClient:
    transport = httpx.MockTransport(mocker_routes)
    return YarchHttpClient("http://upstream", client=httpx.Client(transport=transport))


def test_timeout_over_30_rejected():
    with pytest.raises(ValueError):
        YarchHttpClient("http://upstream", timeout=31)


def test_envelope_success_returns_data():
    def handler(request):
        return httpx.Response(
            200, json={"code": 0, "message": "成功", "data": {"x": 1}, "traceId": "t"}
        )

    assert env(handler).get("/api/v1/things") == {"x": 1}


def test_downstream_code_passthrough():
    def handler(request):
        return httpx.Response(
            400,
            json={"code": 3004, "message": "订单状态不允许该操作", "data": None, "traceId": "t"},
        )

    with pytest.raises(BizError) as ei:
        env(handler).post("/api/v1/orders", json={"a": 1})
    assert ei.value.code == 3004 and ei.value.message == "订单状态不允许该操作"


def test_timeout_maps_1008():
    def handler(request):
        raise httpx.ReadTimeout("slow")

    with pytest.raises(BizError) as ei:
        env(handler).get("/slow")
    assert ei.value.code == 1008 and ei.value.http_status == 504


def test_transport_error_and_non_envelope_map_1009():
    def conn_error(request):
        raise httpx.ConnectError("refused")

    with pytest.raises(BizError) as ei:
        env(conn_error).get("/x")
    assert ei.value.code == 1009

    def not_envelope(request):
        return httpx.Response(200, text="<html>hi</html>")

    with pytest.raises(BizError) as ei2:
        env(not_envelope).get("/y")
    assert ei2.value.code == 1009


def test_traceparent_injected_from_contextvar():
    captured = {}

    def handler(request):
        captured.update(dict(request.headers))
        return httpx.Response(
            200, json={"code": 0, "message": "成功", "data": None, "traceId": "t"}
        )

    token = logx.bind_trace("0af7651916cd43dd8448eb211c80319c")
    try:
        env(handler).get("/z")
    finally:
        logx.reset_trace(token)
    import re

    assert re.fullmatch(
        r"00-0af7651916cd43dd8448eb211c80319c-[0-9a-f]{16}-01", captured["traceparent"]
    )
    assert captured["x-trace-id"] == "0af7651916cd43dd8448eb211c80319c"
