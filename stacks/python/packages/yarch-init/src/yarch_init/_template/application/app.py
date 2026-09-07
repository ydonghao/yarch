"""组装根（手写分阶段：basic 配置 → primary 数据/中间件 → complex 路由）。"""
from fastapi import FastAPI
from yarch_python import logx, persist, redix
from yarch_python.web import ok, setup

from api.router import api_router
from application.settings import settings
from infrastructure.database import models  # noqa: F401  注册 ORM 映射
from infrastructure.database.user_repo import SqlAlchemyUserRepository


def create_app() -> FastAPI:
    # stage 1 basic
    logx.setup(settings.service, settings.env)
    if not settings.skip_migrations:
        persist.ensure_database(settings.database_url)
        persist.alembic_upgrade("infrastructure/database/migrations", settings.database_url)
    # stage 2 primary
    session_factory = persist.sessionmaker_for(settings.database_url)
    import redis as redis_lib

    redis_client = redis_lib.Redis.from_url(settings.redis_url)
    keys = redix.Keys(settings.service)
    app = FastAPI(title=settings.service)
    setup(
        app,
        service=settings.service,
        env=settings.env,
        idempotency_store=redix.IdempotencyStore(redis_client),
        rate_limit=(redix.FixedWindowLimiter(redis_client), 100, 1),
    )
    app.state.session_factory = session_factory
    app.state.user_repo = SqlAlchemyUserRepository(session_factory)
    app.state.redis = redis_client
    app.state.keys = keys
    # stage 3 complex
    app.include_router(api_router)

    @app.get("/healthz")
    def healthz():
        return ok(dict(service=settings.service, status="up"))

    return app
