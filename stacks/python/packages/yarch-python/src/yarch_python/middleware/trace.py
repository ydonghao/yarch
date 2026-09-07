# stacks/python/packages/yarch-python/src/yarch_python/middleware/trace.py
"""traceId 三级入口（traceparent→X-Trace-Id→生成）+ 响应头回显（logging-trace.md 三）。"""
import re

from starlette.types import ASGIApp, Receive, Scope, Send

from yarch_python import logx

_TRACEPARENT = re.compile(r"^00-([0-9a-f]{32})-[0-9a-f]{16}-[0-9a-f]{2}$")


def resolve_trace_id(headers: dict[bytes, bytes]) -> str:
    tp = headers.get(b"traceparent", b"").decode("latin-1")
    m = _TRACEPARENT.match(tp)
    if m:
        return m.group(1)
    raw = headers.get(b"x-trace-id", b"").decode("latin-1").strip()
    if raw:
        return raw[:64]
    return logx.new_trace_id()


class TraceMiddleware:
    def __init__(self, app: ASGIApp):
        self.app = app

    async def __call__(self, scope: Scope, receive: Receive, send: Send) -> None:
        if scope["type"] != "http":
            await self.app(scope, receive, send)
            return
        headers = dict(scope.get("headers") or [])
        trace_id = resolve_trace_id(headers)
        token = logx.bind_trace(trace_id)

        async def send_with_header(message):
            if message["type"] == "http.response.start":
                message.setdefault("headers", []).append((b"x-trace-id", trace_id.encode()))
            await send(message)

        try:
            await self.app(scope, receive, send_with_header)
        finally:
            logx.reset_trace(token)
