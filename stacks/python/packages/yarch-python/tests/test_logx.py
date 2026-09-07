# stacks/python/packages/yarch-python/tests/test_logx.py
import io
import json
import re

from yarch_python import logx

TS_RE = re.compile(r"^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d{3}Z$")


def setup_sink():
    sink = io.StringIO()
    logx.setup("ysaas-scan", "local", sink=sink)
    return sink


def test_ndjson_field_set_and_order():
    sink = setup_sink()
    logx.get_logger("mymod").info("request completed", method="GET", path="/api/v1/users", status=200, costMs=12)
    line = sink.getvalue().strip()
    d = json.loads(line)
    assert list(d.keys())[:7] == ["ts", "level", "service", "env", "traceId", "logger", "msg"]
    assert TS_RE.match(d["ts"])
    assert d["level"] == "INFO" and d["service"] == "ysaas-scan" and d["env"] == "local"
    assert d["logger"] == "mymod" and d["msg"] == "request completed"
    assert d["method"] == "GET" and d["costMs"] == 12


def test_trace_id_from_contextvars():
    sink = setup_sink()
    token = logx.bind_trace("0af7651916cd43dd8448eb211c80319c")
    logx.get_logger("x").warning("boom")
    logx.reset_trace(token)
    d = json.loads(sink.getvalue().strip())
    assert d["traceId"] == "0af7651916cd43dd8448eb211c80319c"
    assert d["level"] == "WARN"


def test_new_trace_id_shape():
    assert re.fullmatch(r"[0-9a-f]{32}", logx.new_trace_id())


def test_stack_folded_single_field():
    sink = setup_sink()
    try:
        raise ValueError("inner")
    except ValueError:
        import traceback
        logx.get_logger("e").error("internal error", stack=traceback.format_exc())
    d = json.loads(sink.getvalue().strip())
    assert "stack" in d and "\n" not in sink.getvalue().strip()
