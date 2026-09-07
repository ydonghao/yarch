"""celery 示例任务：{{ service }}.users.sync——traceId 继承 + run_once 幂等演示。"""

from celery import current_app
from yarch_python import celeryx, logx

from application.settings import settings


@current_app.task(
    base=celeryx.TraceTask,
    name=celeryx.task_name(settings.service, "users", "sync"),
    **celeryx.retry_options(),
)
def sync_user(user_id: str) -> dict:
    import redis as redis_lib

    r = redis_lib.Redis.from_url(settings.redis_url)
    if not celeryx.run_once(r, f"{settings.service}:once:users-sync:{user_id}", ttl_s=3600):
        logx.get_logger("{{ service }}.users").info("skip duplicated sync", userId=user_id)
        return dict(user=user_id, deduped=True)
    logx.get_logger("{{ service }}.users").info("sync user", userId=user_id)
    return dict(user=user_id, deduped=False)
