"""Worker/Beat 入口（分离部署，celery.md 四-1/五-4）：
uv run celery -A celery_app worker -Q {{ service }}.default
uv run celery -A celery_app beat   # 单实例！celeryx.beat_guard 守卫
"""
import redis as redis_lib
from application.settings import settings
from yarch_python import celeryx

celery_app = celeryx.make_app(
    settings.service,
    settings.celery_broker_url,
    soft_time_limit=60,
    hard_time_limit=90,
    result_backend=settings.celery_result_backend,
)
# 模块级任务在 import 时注册到 current_app，须在 make_app 之后 import
# （celery.autodiscover_tasks 只认 <pkg>.tasks 子模块，不吃本布局，故显式 import）
import tasks.users_task  # noqa: E402, F401

celeryx.assert_json_only(celery_app)  # 启动即自检 json-only


def beat_single_instance() -> None:
    r = redis_lib.Redis.from_url(settings.redis_url)
    celeryx.beat_guard(r, settings.service)
