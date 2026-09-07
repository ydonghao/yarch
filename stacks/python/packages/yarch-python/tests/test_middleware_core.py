# stacks/python/packages/yarch-python/tests/test_middleware_core.py
import io
import json

from fastapi import FastAPI
from fastapi.testclient import TestClient
from yarch_python import logx
from yarch_python.middleware import AccessLogMiddleware, RecoveryMiddleware, TraceMiddleware

TP = "00-0af7651916cd43dd8448eb211c80319c-fedc1341d3a7415e-01"


def make_app(capture: io.StringIO) -> FastAPI:
    logx.setup("ysaas-scan", "local", sink=capture)
    app = FastAPI()
    app.add_middleware(RecoveryMiddleware)
    app.add_middleware(TraceMiddleware)
    app.add_middleware(AccessLogMiddleware)  # 最后 add = 最外层
    return app


def test_trace_three_level_entry_and_echo():
    sink = io.StringIO()
    app = make_app(sink)

    @app.get("/boom")
    def boom():
        raise RuntimeError("x")

    c = TestClient(app, raise_server_exceptions=False)
    r1 = c.get("/anything")  # 404 路径也走中间件
    assert r1.headers["x-trace-id"] != ""
    r2 = c.get("/boom", headers={"traceparent": TP})
    assert r2.headers["x-trace-id"] == "0af7651916cd43dd8448eb211c80319c"
    r3 = c.get("/boom", headers={"X-Trace-Id": "mytrace123"})
    assert r3.headers["x-trace-id"] == "mytrace123"


def test_recovery_envelope_1000():
    sink = io.StringIO()
    app = make_app(sink)

    @app.get("/boom")
    def boom():
        raise RuntimeError("x")

    r = TestClient(app, raise_server_exceptions=False).get("/boom", headers={"traceparent": TP})
    assert r.status_code == 500
    assert r.json() == {
        "code": 1000,
        "message": "内部错误",
        "data": None,
        "traceId": "0af7651916cd43dd8448eb211c80319c",
    }


def test_accesslog_ndjson_line():
    sink = io.StringIO()
    app = make_app(sink)

    @app.get("/api/v1/users")
    def users():
        return {"raw": 1}

    TestClient(app).get("/api/v1/users")
    line = sink.getvalue().strip().splitlines()[-1]
    d = json.loads(line)
    assert d["msg"] == "request completed"
    assert d["method"] == "GET" and d["path"] == "/api/v1/users" and d["status"] == 200
    assert isinstance(d["costMs"], int) and d["traceId"] != ""
