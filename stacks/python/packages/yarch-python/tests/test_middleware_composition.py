# stacks/python/packages/yarch-python/tests/test_middleware_composition.py
"""中间件组合语义回归：Rate×Idem 顺序 / 异常释放 / 5xx 不落 done。

跨栈 conformance 的「组合语义」维度——码表/信封形状之外的中间件协同行为（终审缺口，2026-09-07）。
"""

import io

from fastapi import FastAPI
from fastapi.testclient import TestClient
from starlette.responses import Response as RawResponse
from yarch_python import logx
from yarch_python.redix import Keys
from yarch_python.web import ok, setup


class FakeStore:
    """幂等存储假件：三态行为对齐 redix.IdempotencyStore，条目可直接观测"""

    def __init__(self):
        # key -> (digest, status, body)；status None = pending
        self.entries: dict[str, tuple[str, int | None, str | None]] = {}
        self.releases: list[str] = []

    def acquire(self, key: str, digest: str) -> str:
        entry = self.entries.get(key)
        if entry is None:
            self.entries[key] = (digest, None, None)
            return "acquired"
        if entry[0] != digest:
            return "mismatch"
        return "replay" if entry[1] is not None else "pending"

    def load(self, key: str) -> tuple[int, str] | None:
        entry = self.entries.get(key)
        return None if entry is None or entry[1] is None else (entry[1], entry[2] or "")

    def store_response(self, key: str, status: int, body: str) -> None:
        digest = self.entries[key][0]
        self.entries[key] = (digest, status, body)

    def release(self, key: str) -> None:
        self.entries.pop(key, None)
        self.releases.append(key)


class ToggleLimiter:
    """可控限流假件：deny_next=True 时下一次 hit 拒绝"""

    def __init__(self):
        self.deny_next = False

    def hit(self, key: str, window_s: int, limit: int) -> bool:
        if self.deny_next:
            self.deny_next = False
            return False
        return True


def make_app(store: FakeStore, limiter: ToggleLimiter) -> tuple[FastAPI, dict]:
    logx.setup("ysaas-scan", "local", sink=io.StringIO())
    app = FastAPI()
    setup(
        app,
        service="ysaas-scan",
        env="local",
        idempotency_store=store,
        rate_limit=(limiter, 10, 60),
    )
    state = {"count": 0}

    @app.post("/api/v1/jobs", status_code=201)
    def create_job():
        state["count"] += 1
        return ok({"seq": state["count"]}, status_code=201)

    @app.post("/api/v1/flaky")
    def flaky():
        state["count"] += 1
        return RawResponse(status_code=500, content=b'{"code":1000,"message":"x"}')

    @app.post("/api/v1/boom")
    def boom():
        state["count"] += 1
        raise RuntimeError("x")

    return app, state


def test_rate_limited_request_must_not_poison_idempotency():
    """限流拒绝不得被幂等层捕获落库：限流恢复后同键重试必须真正执行（而非 24h 内回放 429）"""
    store, limiter = FakeStore(), ToggleLimiter()
    app, state = make_app(store, limiter)
    c = TestClient(app, raise_server_exceptions=False)

    limiter.deny_next = True
    r1 = c.post("/api/v1/jobs", json={"a": 1}, headers={"Idempotency-Key": "job-1"})
    assert r1.status_code == 429 and r1.json()["code"] == 1006
    assert state["count"] == 0, "被限流的请求不得触达业务"
    key = Keys("ysaas-scan").of("idem", "job-1")
    assert key not in store.entries, "限流拒绝不得在幂等存储留下任何条目"

    r2 = c.post("/api/v1/jobs", json={"a": 1}, headers={"Idempotency-Key": "job-1"})
    assert r2.status_code == 201 and r2.json()["data"]["seq"] == 1
    assert store.load(key) is not None, "正常执行后应落 done 供后续回放"


def test_exception_releases_pending_without_partial_done():
    """执行异常：占位释放、残缺响应不落库——同键重试可再执行"""
    store, limiter = FakeStore(), ToggleLimiter()
    app, state = make_app(store, limiter)
    c = TestClient(app, raise_server_exceptions=False)

    r1 = c.post("/api/v1/boom", json={"a": 1}, headers={"Idempotency-Key": "boom-1"})
    assert r1.status_code == 500 and r1.json()["code"] == 1000
    key = Keys("ysaas-scan").of("idem", "boom-1")
    assert key not in store.entries, "异常后不得残留 pending 或残缺 done"
    assert key in store.releases

    r2 = c.post("/api/v1/boom", json={"a": 1}, headers={"Idempotency-Key": "boom-1"})
    assert r2.status_code == 500 and state["count"] == 2, "同键重试必须重新执行"


def test_5xx_response_not_stored_as_done():
    """5xx 响应不落 done：释放占位允许重试（与 java 栈 IdempotencyInterceptor 口径一致）"""
    store, limiter = FakeStore(), ToggleLimiter()
    app, state = make_app(store, limiter)
    c = TestClient(app, raise_server_exceptions=False)

    r1 = c.post("/api/v1/flaky", json={"a": 1}, headers={"Idempotency-Key": "flaky-1"})
    assert r1.status_code == 500 and state["count"] == 1
    key = Keys("ysaas-scan").of("idem", "flaky-1")
    assert key not in store.entries, "5xx 不得作为可回放 done 存储"

    r2 = c.post("/api/v1/flaky", json={"a": 1}, headers={"Idempotency-Key": "flaky-1"})
    assert r2.status_code == 500 and state["count"] == 2, "同键重试必须重新执行"


def test_2xx_still_replays_normally():
    """回归护栏：正常 2xx 语义不变——同键同参回放、不重复执行"""
    store, limiter = FakeStore(), ToggleLimiter()
    app, state = make_app(store, limiter)
    c = TestClient(app, raise_server_exceptions=False)

    r1 = c.post("/api/v1/jobs", json={"a": 1}, headers={"Idempotency-Key": "job-9"})
    r2 = c.post("/api/v1/jobs", json={"a": 1}, headers={"Idempotency-Key": "job-9"})
    assert r1.status_code == r2.status_code == 201
    assert r1.json() == r2.json() and state["count"] == 1, "同键同参必须回放原响应且只执行一次"
