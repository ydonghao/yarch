# stacks/python/packages/yarch-python/src/yarch_python/logx/__init__.py
"""logx：structlog ndjson 行协议（logging-trace.md v1.0）+ contextvars traceId 贯穿。"""
import contextvars
import json
import secrets
import sys
from datetime import UTC, datetime
from typing import Any, TextIO

import structlog

_trace_id: contextvars.ContextVar[str] = contextvars.ContextVar("trace_id", default="")


def bind_trace(trace_id: str):
    return _trace_id.set(trace_id)


def reset_trace(token) -> None:
    _trace_id.reset(token)


def current_trace() -> str:
    return _trace_id.get()


def new_trace_id() -> str:
    return secrets.token_hex(16)


def get_logger(name: str) -> Any:
    return structlog.get_logger().bind(logger=name)


def _make_processors(service: str, env: str):
    def add_ts(_, __, ed):
        now = datetime.now(UTC)
        ed["ts"] = now.strftime("%Y-%m-%dT%H:%M:%S.") + f"{now.microsecond // 1000:03d}Z"
        return ed

    def add_level(_, method, ed):
        # structlog 26.1 对 warning 级传入 method_name="warning"，行协议规定输出 WARN
        ed["level"] = "WARN" if method == "warning" else method.upper()
        return ed

    def add_static(_, __, ed):
        ed["service"] = service
        ed["env"] = env
        return ed

    def add_trace(_, __, ed):
        ed["traceId"] = _trace_id.get()
        return ed

    def rename_event(_, __, ed):
        ed["msg"] = ed.pop("event")
        return ed

    return [
        structlog.contextvars.merge_contextvars,
        add_level,
        add_static,
        add_trace,
        add_ts,
        rename_event,
    ]


def setup(service: str, env: str, *, level: str = "INFO", sink: TextIO | None = None) -> None:
    import logging as _logging

    out = sink if sink is not None else sys.stdout

    # structlog 的最终处理器必须返回 str/bytes/dict/tuple（返回 None 会 ValueError），
    # 且 bind 上下文与调用 kwargs 会在处理器链之前合入 event dict，破坏字段序；
    # 故此处按行协议重排字段后交由 WriteLogger 写行（写 out 并逐条 flush）。
    protocol_fields = ("ts", "level", "service", "env", "traceId", "logger", "msg")

    def render(_, __, ed):
        ordered = {k: ed.pop(k) for k in protocol_fields if k in ed}
        ordered.update(ed)
        return json.dumps(ordered, ensure_ascii=False, default=str)

    structlog.configure(
        processors=[*_make_processors(service, env), render],
        wrapper_class=structlog.make_filtering_bound_logger(
            getattr(_logging, level.upper()) if isinstance(level, str) else level
        ),
        logger_factory=structlog.WriteLoggerFactory(file=out),
        cache_logger_on_first_use=False,
    )
