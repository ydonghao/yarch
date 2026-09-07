# stacks/python/packages/yarch-python/src/yarch_python/middleware/idempotency.py
"""幂等中间件（rest-conventions.md 幂等总则-1）：同键同参回放/异参 1007/并发短暂等待。"""

import asyncio
import hashlib
from typing import Any

from starlette.types import ASGIApp, Message, Receive, Scope, Send

from yarch_python import errcode, logx
from yarch_python.redix import IdempotencyStore, Keys
from yarch_python.response import Response

_UNSAFE = {"POST", "PUT", "PATCH", "DELETE"}


def _digest(method: str, path: str, body: bytes) -> str:
    return hashlib.sha256(f"{method} {path} ".encode() + body).hexdigest()


def _replay_receive(body: bytes) -> Receive:
    sent = {"done": False}

    async def receive() -> Message:
        if not sent["done"]:
            sent["done"] = True
            return {"type": "http.request", "body": body, "more_body": False}
        return {"type": "http.disconnect"}

    return receive


class IdempotencyMiddleware:
    def __init__(self, app: ASGIApp, *, store: IdempotencyStore, service: str):
        self.app = app
        self.store = store
        self.keys = Keys(service)

    async def __call__(self, scope: Scope, receive: Receive, send: Send) -> None:
        if scope["type"] != "http" or scope["method"] not in _UNSAFE:
            await self.app(scope, receive, send)
            return
        headers = dict(scope.get("headers") or [])
        raw_key = headers.get(b"idempotency-key", b"").decode("latin-1").strip()
        if not raw_key:
            await self.app(scope, receive, send)
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
        digest = _digest(scope["method"], scope["path"], body)
        key = self.keys.of("idem", raw_key)

        state = self.store.acquire(key, digest)
        if state == "mismatch":
            await self._send_biz(send, 1007)
            return
        if state == "pending":
            for _ in range(5):
                await asyncio.sleep(0.2)
                state = self.store.acquire(key, digest)
                if state in ("replay", "mismatch"):
                    break
            if state != "replay":
                await self._send_biz(send, 1007)
                return
        if state == "replay":
            stored = self.store.load(key)
            if stored is None:
                await self._send_biz(send, 1007)
                return
            status_code, body_str = stored
            await send(
                {
                    "type": "http.response.start",
                    "status": status_code,
                    "headers": [(b"content-type", b"application/json")],
                }
            )
            await send({"type": "http.response.body", "body": body_str.encode()})
            return

        # acquired：执行并捕获响应
        captured: dict = {}

        async def send_capture(message: Message) -> None:
            if message["type"] == "http.response.start":
                captured["status"] = message["status"]
                captured["headers"] = list(message.get("headers", []))
            elif message["type"] == "http.response.body":
                captured.setdefault("chunks", []).append(message.get("body", b""))
            await send(message)

        try:
            await self.app(scope, _replay_receive(body), send_capture)
        except BaseException:
            # 执行失败释放 pending：同键重试可再执行（rest-conventions 幂等总则-1）
            self.store.release(key)
            raise
        finally:
            chunks = captured.get("chunks")
            if captured.get("status") is not None and chunks is not None:
                self.store.store_response(
                    key, captured["status"], b"".join(chunks).decode("utf-8", "replace")
                )

    async def _send_biz(self, send: Send, code: int) -> None:
        resp: Response[Any] = Response(
            code=code,
            message=f"{errcode.message_of(code)}：Idempotency-Key 冲突",
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
