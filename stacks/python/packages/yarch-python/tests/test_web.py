# stacks/python/packages/yarch-python/tests/test_web.py
import io

from fastapi import FastAPI, Request
from fastapi.testclient import TestClient
from pydantic import BaseModel
from yarch_python import logx
from yarch_python.web import ok, page, page_query, setup
from yarch_python.xerror import BizError

TP = "00-0af7651916cd43dd8448eb211c80319c-fedc1341d3a7415e-01"


class CreateUser(BaseModel):
    username: str
    age: int


def make_app() -> FastAPI:
    logx.setup("ysaas-scan", "local", sink=io.StringIO())
    app = FastAPI()
    setup(app, service="ysaas-scan", env="local")

    @app.post("/api/v1/users", status_code=201)
    def create_user(body: CreateUser):
        return ok({"id": "u1", "username": body.username}, status_code=201)

    @app.get("/api/v1/users")
    def list_users(request: Request):
        pq = page_query(request)
        return page([{"id": "u1"}], total=41, page=pq.page, page_size=pq.page_size)

    @app.get("/api/v1/boom-biz")
    def boom_biz():
        raise BizError(1004, detail="user u9")

    return app


def test_ok_envelope_with_trace_header():
    r = TestClient(make_app()).post("/api/v1/users", json={"username": "a", "age": 1})
    assert r.status_code == 201
    assert list(r.json().keys()) == ["code", "message", "data", "traceId"]
    assert r.json()["code"] == 0 and r.json()["data"]["id"] == "u1"
    assert r.json()["traceId"] == r.headers["x-trace-id"] != ""


def test_validation_error_1001():
    r = TestClient(make_app()).post("/api/v1/users", json={"username": "a", "age": "not-int"})
    assert r.status_code == 400 and r.json()["code"] == 1001
    assert r.json()["message"].startswith("参数校验失败")


def test_malformed_body_1002():
    r = TestClient(make_app()).post(
        "/api/v1/users", content=b"{broken", headers={"content-type": "application/json"}
    )
    assert r.status_code == 400 and r.json()["code"] == 1002
    assert r.json()["message"].startswith("请求体格式错误")


def test_biz_error_maps_http():
    r = TestClient(make_app()).get("/api/v1/boom-biz")
    assert r.status_code == 404
    assert r.json()["code"] == 1004 and r.json()["message"] == "资源不存在：user u9"


def test_unknown_route_404_envelope():
    r = TestClient(make_app()).get("/api/v1/nope")
    assert r.status_code == 404 and r.json()["code"] == 1004


def test_page_defaults_and_limits():
    c = TestClient(make_app())
    d = c.get("/api/v1/users").json()["data"]
    assert d == {"list": [{"id": "u1"}], "total": 41, "page": 1, "pageSize": 20}
    assert c.get("/api/v1/users?pageSize=100").json()["data"]["pageSize"] == 100
    assert c.get("/api/v1/users?pageSize=101").json()["code"] == 1001
    assert c.get("/api/v1/users?page=0").json()["code"] == 1001
    assert c.get("/api/v1/users?page=abc").json()["code"] == 1001


def test_traceparent_wins_over_x_trace_id():
    # Task 6 审查移交建议：双头同发时 traceparent 段值优先。
    r = TestClient(make_app()).get(
        "/api/v1/users", headers={"traceparent": TP, "X-Trace-Id": "mytrace123"}
    )
    assert r.json()["traceId"] == r.headers["x-trace-id"] == "0af7651916cd43dd8448eb211c80319c"
