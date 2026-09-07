# stacks/python/packages/yarch-python/src/yarch_python/middleware/recovery.py
"""未捕获异常 → 500 信封 code=1000（message 固定「内部错误」，细节只进日志）。"""

import traceback

from starlette.types import ASGIApp, Receive, Scope, Send

from yarch_python import errcode, logx
from yarch_python.response import Response


class RecoveryMiddleware:
    def __init__(self, app: ASGIApp):
        self.app = app

    async def __call__(self, scope: Scope, receive: Receive, send: Send) -> None:
        if scope["type"] != "http":
            await self.app(scope, receive, send)
            return
        try:
            await self.app(scope, receive, send)
        except Exception:
            logx.get_logger("yarch_python.middleware.recovery").error(
                "internal error", stack=traceback.format_exc()
            )
            body = (
                Response(
                    code=1000,
                    message=errcode.message_of(1000),
                    data=None,
                    trace_id=logx.current_trace(),
                )
                .model_dump_json(by_alias=True)
                .encode()
            )
            await send(
                {
                    "type": "http.response.start",
                    "status": 500,
                    "headers": [(b"content-type", b"application/json")],
                }
            )
            await send({"type": "http.response.body", "body": body})
