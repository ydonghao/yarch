# stacks/python/packages/yarch-python/src/yarch_python/middleware/accesslog.py
"""访问日志：ndjson request completed（method/path/status/costMs）。"""
import time

from starlette.types import ASGIApp, Receive, Scope, Send

from yarch_python import logx


class AccessLogMiddleware:
    def __init__(self, app: ASGIApp):
        self.app = app

    async def __call__(self, scope: Scope, receive: Receive, send: Send) -> None:
        if scope["type"] != "http":
            await self.app(scope, receive, send)
            return
        start = time.monotonic()
        status = 0
        trace_id = ""

        async def send_wrap(message):
            nonlocal status, trace_id
            if message["type"] == "http.response.start":
                status = message["status"]
                # 此刻仍在内层 Trace 的 bind 有效期内，快照请求级 traceId。
                trace_id = logx.current_trace()
            await send(message)

        try:
            await self.app(scope, receive, send_wrap)
        finally:
            # 偏离任务书逐字实现的必要修正：AccessLog 在最外层，与内层 Trace 共享同一
            # task context——Trace 的 finally reset_trace 先于本层 finally 的日志调用
            # 执行，此刻 contextvar 已复位为 ""；且 logx 的 add_trace 处理器无条件以
            # contextvar 覆盖 traceId 字段（kwarg 传不入），故将响应期快照的 traceId
            # 重新 bind 后再落日志，随后复位，不影响外层上下文。
            token = logx.bind_trace(trace_id)
            try:
                logx.get_logger("yarch_python.middleware.accesslog").info(
                    "request completed",
                    method=scope.get("method", ""),
                    path=scope.get("path", ""),
                    status=status,
                    costMs=int((time.monotonic() - start) * 1000),
                )
            finally:
                logx.reset_trace(token)
