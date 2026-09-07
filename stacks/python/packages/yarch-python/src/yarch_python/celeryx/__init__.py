# stacks/python/packages/yarch-python/src/yarch_python/celeryx/__init__.py
"""celeryx：celery.md v1.0 承接装配件。

broker 前缀/命名/json-only/超时重试显式/beat 单实例/traceId 任务头/失败终点 SPI。
"""

from collections.abc import Callable
from typing import Any

from celery import Celery, Task
from kombu import Queue
from redis import Redis
from redis.lock import Lock

from yarch_python import logx

_FAILURE_SINKS: list[Callable[[dict[str, Any]], None]] = []


def task_name(service: str, module: str, action: str) -> str:
    return f"{service}.{module}.{action}"


def queue_name(service: str, usage: str) -> str:
    return f"{service}.{usage}"


def retry_options() -> dict[str, Any]:
    """三-3：max_retries≤5 + 指数退避 + 抖动。"""
    return {"max_retries": 5, "retry_backoff": True, "retry_backoff_jitter": True}


def trace_headers() -> dict[str, str]:
    """四-4：HTTP 请求内派发继承调用方 traceId，无则新建。"""
    return {"traceId": logx.current_trace() or logx.new_trace_id()}


def on_failure_sink(fn: Callable[[dict[str, Any]], None]) -> None:
    _FAILURE_SINKS.append(fn)


def assert_json_only(app: Celery) -> None:
    if app.conf.task_serializer != "json" or set(app.conf.accept_content) != {"json"}:
        raise ValueError("序列化只允许 json（celery.md 三-5，pickle 全局禁用）")


class TraceTask(Task):
    """任务链路 traceId：headers 继承（HTTP 派发）/每轮新建（周期，天然新链路）。"""

    def __call__(self, *args: Any, **kwargs: Any) -> Any:
        tid = (self.request.headers or {}).get("traceId") or logx.new_trace_id()
        token = logx.bind_trace(str(tid))
        try:
            return super().__call__(*args, **kwargs)
        finally:
            logx.reset_trace(token)

    def on_failure(
        self,
        exc: BaseException,
        task_id: str | None,
        args: tuple,  # noqa: UP006
        kwargs: dict[str, Any],
        einfo: Any,
    ) -> None:
        record: dict[str, Any] = {
            "task": self.name,
            "taskId": task_id,
            "error": repr(exc),
            "traceId": logx.current_trace(),
        }
        for sink in _FAILURE_SINKS:
            try:
                sink(record)
            except Exception:  # 投递失败不阻断（对齐 oplog SPI 模式）
                logx.get_logger("yarch_python.celeryx").warning(
                    "failure sink error", task=self.name
                )
        super().on_failure(exc, task_id, args, kwargs, einfo)


def make_app(
    service: str,
    broker_url: str,
    *,
    soft_time_limit: float = 60,
    hard_time_limit: float | None = None,
    result_backend: str | None = None,
    result_expires: int = 86400,
    extra_queues: tuple[str, ...] = (),
) -> Celery:
    if soft_time_limit is None or hard_time_limit is None:
        raise ValueError("soft_time_limit / hard_time_limit 必配（celery.md 三-2，禁无限执行）")
    if hard_time_limit <= soft_time_limit:
        raise ValueError("hard_time_limit 须 > soft_time_limit")
    app = Celery(service, broker=broker_url, backend=result_backend)
    queues = [queue_name(service, "default"), *extra_queues]
    app.conf.update(
        broker_transport_options={"global_keyprefix": f"{service}:"},  # 二-1 共享实例租户纪律
        task_serializer="json",
        accept_content=["json"],
        result_serializer="json",
        task_default_queue=queue_name(service, "default"),
        task_create_missing_queues=False,  # 二-2 禁默认单队列裸奔
        task_soft_time_limit=soft_time_limit,
        task_hard_time_limit=hard_time_limit,
        result_expires=result_expires,  # 二-4 TTL 必设
        task_acks_late=True,  # 四-5
        worker_prefetch_multiplier=1,
    )
    app.conf.task_queues = [Queue(q) for q in queues]
    assert_json_only(app)
    return app


def beat_guard(redis: Redis, service: str, *, ttl_s: int = 60) -> Lock:
    """四-3：beat 单实例守卫——锁获取失败即拒绝启动（多实例 beat = 周期任务全量双跑）。"""
    lock = redis.lock(f"{service}:beat:single", timeout=ttl_s)
    if not lock.acquire(blocking=False):
        raise RuntimeError("beat 须单实例（celery.md 四-3）：锁被占用，本实例拒绝启动")
    return lock


def run_once(redis: Redis, key: str, ttl_s: int = 3600) -> bool:
    """三-1 幂等键助手：业务键去重（至少一次交付下重复必然发生）。"""
    return bool(redis.set(key, "1", nx=True, ex=ttl_s))
