# stacks/python/packages/yarch-python/tests/test_celeryx.py
import pytest
from yarch_python import celeryx


def test_naming():
    assert celeryx.task_name("ysaas-scan", "users", "sync") == "ysaas-scan.users.sync"
    assert celeryx.queue_name("ysaas-scan", "embed-scan") == "ysaas-scan.embed-scan"


def test_retry_options_and_trace_headers():
    assert celeryx.retry_options() == {
        "max_retries": 5,
        "retry_backoff": True,
        "retry_backoff_jitter": True,
    }
    from yarch_python import logx

    token = logx.bind_trace("0af7651916cd43dd8448eb211c80319c")
    try:
        assert celeryx.trace_headers() == {"traceId": "0af7651916cd43dd8448eb211c80319c"}
    finally:
        logx.reset_trace(token)


def test_make_app_locks_contract_defaults():
    app = celeryx.make_app("ysaas-scan", "redis://localhost:6379/0", hard_time_limit=90)
    assert app.conf.broker_transport_options["global_keyprefix"] == "ysaas-scan:"
    assert app.conf.task_serializer == "json"
    assert app.conf.accept_content == ["json"]
    assert app.conf.task_default_queue == "ysaas-scan.default"
    assert app.conf.task_create_missing_queues is False
    assert app.conf.task_soft_time_limit == 60 and app.conf.task_hard_time_limit == 90
    assert app.conf.task_acks_late is True and app.conf.worker_prefetch_multiplier == 1
    assert app.conf.result_expires == 86400


def test_time_limits_required_and_ordered():
    with pytest.raises(ValueError):
        celeryx.make_app("ysaas-scan", "redis://x", hard_time_limit=None)
    with pytest.raises(ValueError):
        celeryx.make_app("ysaas-scan", "redis://x", soft_time_limit=90, hard_time_limit=60)


def test_pickle_rejected_after_configure():
    app = celeryx.make_app("ysaas-scan", "redis://x", hard_time_limit=90)
    app.conf.accept_content = ["json", "pickle"]
    with pytest.raises(ValueError):
        celeryx.assert_json_only(app)


def test_trace_task_inherits_header_trace_id():
    # 笔误修正：原 hard_time_limit=30 配默认 soft=60 违反 hard>soft 契约，补 soft_time_limit=20
    app = celeryx.make_app("ysaas-scan", "memory://", soft_time_limit=20, hard_time_limit=30)
    app.conf.task_always_eager = True
    app.conf.task_eager_propagates = True
    seen = {}
    from yarch_python import logx

    @app.task(base=celeryx.TraceTask, name="ysaas-scan.users.sync")
    def sync_user(user_id: str):
        seen["trace"] = logx.current_trace()
        return user_id

    # 笔误修正：删去未使用的 `from yarch_python import celeryx as cx`（ruff F401）
    sync_user.apply(args=["u1"], headers={"traceId": "0af7651916cd43dd8448eb211c80319c"})
    assert seen["trace"] == "0af7651916cd43dd8448eb211c80319c"


def test_failure_sink_records():
    # 笔误修正：同上，补 soft_time_limit=20 以满足 hard>soft 契约
    app = celeryx.make_app("ysaas-scan", "memory://", soft_time_limit=20, hard_time_limit=30)
    app.conf.task_always_eager = True
    app.conf.task_eager_propagates = False
    records: list[dict] = []
    celeryx.on_failure_sink(records.append)

    @app.task(base=celeryx.TraceTask, name="ysaas-scan.users.bad")
    def bad():
        raise RuntimeError("x")

    bad.apply()
    assert len(records) == 1 and records[0]["task"] == "ysaas-scan.users.bad"
