# stacks/python/packages/yarch-python/tests/test_signed_api.py
"""签名中间件契约断言（对偶 java SignatureInterceptor / golang TestSignedApi）：
HMAC 匹配 / 时间窗 ±300s / nonce 一次性消费 / 失败一律 401+2001。"""

import time

from fastapi import FastAPI
from fastapi.testclient import TestClient
from yarch_python.middleware import SignatureMiddleware, hmac_sha256_hex, sign_material

SECRET = "s3cret-key"
BODY = '{"sku":"a"}'


def make_app() -> FastAPI:
    app = FastAPI()
    app.add_middleware(SignatureMiddleware, apps={"web-client": SECRET}, service="demo-svc")

    @app.post("/api/v1/orders")
    def orders():
        return {"ok": True}

    return app


def signed_headers(
    nonce: str, *, ts_ms: int | None = None, sign_override: str | None = None
) -> dict:
    ts = str(ts_ms if ts_ms is not None else int(time.time() * 1000))
    material = sign_material("POST", "/api/v1/orders", ts, nonce, BODY)
    sign = sign_override if sign_override is not None else hmac_sha256_hex(SECRET, material)
    return {
        "X-App-Key": "web-client",
        "X-Timestamp": ts,
        "X-Nonce": nonce,
        "X-Sign": sign,
    }


def test_missing_headers_denied():
    r = TestClient(make_app()).post("/api/v1/orders", json={"sku": "a"})
    assert r.status_code == 401
    assert r.json()["code"] == 2001


def test_unknown_app_key_denied():
    h = signed_headers("n-1")
    h["X-App-Key"] = "other"
    r = TestClient(make_app()).post("/api/v1/orders", content=BODY, headers=h)
    assert r.status_code == 401 and r.json()["code"] == 2001


def test_valid_signature_passes_and_body_reaches_handler():
    r = TestClient(make_app()).post("/api/v1/orders", content=BODY, headers=signed_headers("n-2"))
    assert r.status_code == 200 and r.json() == {"ok": True}


def test_replay_same_nonce_denied():
    c = TestClient(make_app())
    h = signed_headers("n-3")
    assert c.post("/api/v1/orders", content=BODY, headers=h).status_code == 200
    r = c.post("/api/v1/orders", content=BODY, headers=h)
    assert r.status_code == 401 and r.json()["code"] == 2001


def test_stale_timestamp_denied():
    stale = int(time.time() * 1000) - 10 * 60 * 1000
    r = TestClient(make_app()).post(
        "/api/v1/orders", content=BODY, headers=signed_headers("n-4", ts_ms=stale)
    )
    assert r.status_code == 401 and r.json()["code"] == 2001


def test_tampered_signature_denied():
    r = TestClient(make_app()).post(
        "/api/v1/orders", content=BODY, headers=signed_headers("n-5", sign_override="00" * 32)
    )
    assert r.status_code == 401 and r.json()["code"] == 2001
