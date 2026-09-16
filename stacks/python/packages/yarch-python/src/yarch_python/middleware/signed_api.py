# stacks/python/packages/yarch-python/src/yarch_python/middleware/signed_api.py
"""接口签名中间件（E4 反扒基线，对偶 java @SignedApi / golang middleware.SignedApi）：
HMAC-SHA256(method + "\\n" + path + "\\n" + timestamp + "\\n" + nonce + "\\n" + body)，
头部 X-App-Key / X-Timestamp(毫秒) / X-Nonce / X-Sign（hex 小写）；
时间窗 ±300s + nonce 一次性消费双防线防重放。失败 → 401+2001。"""

import hashlib
import hmac
import time
from typing import Any

from starlette.types import ASGIApp, Message, Receive, Scope, Send

from yarch_python import errcode, logx
from yarch_python.response import Response

CLOCK_SKEW_S = 300  # 签名时间窗 ±300s
NONCE_TTL_S = 2 * CLOCK_SKEW_S  # 覆盖整个时间窗（窗口外重放已被时间戳拦截）


def sign_material(method: str, path: str, timestamp: str, nonce: str, body: str) -> str:
    """签名原文（与 java SignatureInterceptor / golang middleware.SignedApi 逐字节一致）。"""
    return "\n".join([method, path, timestamp, nonce, body])


def hmac_sha256_hex(secret: str, material: str) -> str:
    """签名原语（hex 小写），供客户端与测试复用。"""
    return hmac.new(secret.encode("utf-8"), material.encode("utf-8"), hashlib.sha256).hexdigest()


def nonce_key(service: str, app_key: str, nonce: str) -> str:
    """key 摘要化：app_key/nonce 均客户端可控，防注入任意字符进 key（redis.md 二-1 口径）。"""
    digest = hashlib.sha256(f"{app_key}:{nonce}".encode("utf-8")).hexdigest()
    return f"{service}:signedapi:nonce:{digest}"


class InMemoryNonceStore:
    """进程内 nonce 存储（单实例档）；跨实例部署换 redix.RedisNonceStore。"""

    def __init__(self) -> None:
        self._seen: dict[str, float] = {}

    def consume(self, key: str, ttl_s: float) -> bool:
        now = time.monotonic()
        exp = self._seen.get(key)
        if exp is not None and exp > now:
            return False
        if len(self._seen) >= 65536:  # 容量护栏：惰性清过期（TTL 10min 自然回落）
            self._seen = {k: v for k, v in self._seen.items() if v > now}
        self._seen[key] = now + ttl_s
        return True


def _replay_receive(body: bytes) -> Receive:
    sent = {"done": False}

    async def receive() -> Message:
        if not sent["done"]:
            sent["done"] = True
            return {"type": "http.request", "body": body, "more_body": False}
        return {"type": "http.disconnect"}

    return receive


class SignatureMiddleware:
    def __init__(
        self,
        app: ASGIApp,
        *,
        apps: dict[str, str],
        service: str,
        store: InMemoryNonceStore | Any = None,
    ):
        self.app = app
        self.apps = apps
        self.service = service
        self.store = store if store is not None else InMemoryNonceStore()

    async def __call__(self, scope: Scope, receive: Receive, send: Send) -> None:
        if scope["type"] != "http":
            await self.app(scope, receive, send)
            return
        headers = {k.decode("latin-1"): v.decode("latin-1").strip() for k, v in (scope.get("headers") or [])}
        app_key = headers.get("x-app-key", "")
        timestamp = headers.get("x-timestamp", "")
        nonce = headers.get("x-nonce", "")
        sign = headers.get("x-sign", "")
        if not (app_key and timestamp and nonce and sign):
            await self._deny(send, "missing signature headers")
            return
        secret = self.apps.get(app_key)
        if secret is None:
            await self._deny(send, "unknown app key")
            return
        try:
            ts_ms = int(timestamp)
        except ValueError:
            await self._deny(send, "bad timestamp")
            return
        if abs(time.time() * 1000 - ts_ms) > CLOCK_SKEW_S * 1000:
            await self._deny(send, "timestamp expired")
            return

        body = b""
        while True:
            msg = await receive()
            if msg["type"] == "http.request":
                body += msg.get("body", b"")
                if not msg.get("more_body"):
                    break
            else:
                break
        material = sign_material(
            scope["method"], scope["path"], timestamp, nonce, body.decode("utf-8", "replace")
        )
        if not hmac.compare_digest(hmac_sha256_hex(secret, material), sign):
            await self._deny(send, "signature mismatch")
            return
        # 签名校验通过后消费 nonce（一次性）：原样重放 → 已消费 → 2001。
        if not self.store.consume(nonce_key(self.service, app_key, nonce), NONCE_TTL_S):
            await self._deny(send, "nonce replayed")
            return
        await self.app(scope, _replay_receive(body), send)

    async def _deny(self, send: Send, detail: str) -> None:
        code = 2001
        resp: Response[Any] = Response(
            code=code,
            message=f"{errcode.message_of(code)}：{detail}",
            data=None,
            trace_id=logx.current_trace(),
        )
        await send(
            {
                "type": "http.response.start",
                "status": errcode.http_of(code),
                "headers": [(b"content-type", b"application/json")],
            }
        )
        await send(
            {"type": "http.response.body", "body": resp.model_dump_json(by_alias=True).encode()}
        )
