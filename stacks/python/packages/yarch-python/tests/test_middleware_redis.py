# stacks/python/packages/yarch-python/tests/test_middleware_redis.py
import io

import pytest
from fastapi import FastAPI
from fastapi.testclient import TestClient
from pydantic import BaseModel
from yarch_python import logx
from yarch_python.redix import FixedWindowLimiter, IdempotencyStore
from yarch_python.web import ok, setup

pytestmark = pytest.mark.integration


class Body(BaseModel):
    n: int


def make_app(redis) -> FastAPI:
    logx.setup("ysaas-scan", "local", sink=io.StringIO())
    app = FastAPI()
    setup(app, service="ysaas-scan", env="local",
          idempotency_store=IdempotencyStore(redis),
          rate_limit=(FixedWindowLimiter(redis), 100, 60))

    @app.post("/api/v1/orders")
    def create(body: Body):
        return ok({"order": body.n}, status_code=201)

    return app


@pytest.fixture(scope="module")
def redis():
    # testcontainers 4.15 community 口径（Task 8 已验证）：get_client() 直取客户端
    from testcontainers.community.redis import RedisContainer
    with RedisContainer() as c:
        yield c.get_client()


def test_same_key_same_body_replays(redis):
    c = TestClient(make_app(redis))
    h = {"Idempotency-Key": "k-1"}
    r1 = c.post("/api/v1/orders", json={"n": 1}, headers=h)
    r2 = c.post("/api/v1/orders", json={"n": 1}, headers=h)
    assert r1.status_code == r2.status_code == 201
    assert r1.json() == r2.json() and r1.json()["data"] == {"order": 1}


def test_same_key_diff_body_1007(redis):
    c = TestClient(make_app(redis))
    h = {"Idempotency-Key": "k-2"}
    c.post("/api/v1/orders", json={"n": 1}, headers=h)
    r = c.post("/api/v1/orders", json={"n": 2}, headers=h)
    assert r.status_code == 409 and r.json()["code"] == 1007
    assert r.json()["message"].startswith("幂等冲突：重复提交")


def test_no_key_passthrough(redis):
    c = TestClient(make_app(redis))
    r1 = c.post("/api/v1/orders", json={"n": 1})
    r2 = c.post("/api/v1/orders", json={"n": 1})
    assert r1.json()["data"] == {"order": 1} and r2.json()["data"] == {"order": 1}


def test_rate_limit_1006(redis):
    # 笔误整理：原稿 import time 未用、`setup as _s` 别名冗余、先 make_app 再 app2 的
    # 重复装配段删去；行为断言不变——两次 200 后第三次 429 且信封 code=1006
    logx.setup("ysaas-scan", "local", sink=io.StringIO())
    app = FastAPI()
    setup(app, service="ysaas-scan", env="local",
          rate_limit=(FixedWindowLimiter(redis), 2, 60))

    @app.get("/api/v1/ping")
    def ping():
        return ok("pong")

    c = TestClient(app)
    assert c.get("/api/v1/ping").status_code == 200
    assert c.get("/api/v1/ping").status_code == 200
    r = c.get("/api/v1/ping")
    assert r.status_code == 429 and r.json()["code"] == 1006
