# stacks/python/packages/yarch-python/src/yarch_python/middleware/ratelimit.py
"""限流中间件（固定窗口，跨实例口径）：超限 1006/429 信封。"""
from typing import Any

from starlette.types import ASGIApp, Receive, Scope, Send

from yarch_python import errcode, logx
from yarch_python.redix import FixedWindowLimiter, Keys
from yarch_python.response import Response


class RateLimitMiddleware:
    def __init__(self, app: ASGIApp, *, limiter: FixedWindowLimiter, limit: int, window_s: int,
                 service: str):
        self.app = app
        self.limiter = limiter
        self.limit = limit
        self.window_s = window_s
        self.keys = Keys(service)

    async def __call__(self, scope: Scope, receive: Receive, send: Send) -> None:
        if scope["type"] != "http":
            await self.app(scope, receive, send)
            return
        key = self.keys.of("rl", scope.get("path", ""))
        if not self.limiter.hit(key, self.window_s, self.limit):
            resp: Response[Any] = Response(
                code=1006, message=errcode.message_of(1006), data=None,
                trace_id=logx.current_trace(),
            )
            await send({"type": "http.response.start", "status": 429,
                        "headers": [(b"content-type", b"application/json")]})
            await send({"type": "http.response.body",
                        "body": resp.model_dump_json(by_alias=True).encode()})
            return
        await self.app(scope, receive, send)
