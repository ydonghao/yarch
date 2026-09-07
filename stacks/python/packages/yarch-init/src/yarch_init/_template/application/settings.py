"""配置：.env 单文件（pydantic-settings）；env 值域 local/dev/staging/prod（logging-trace.md）。"""
from pydantic_settings import BaseSettings


class Settings(BaseSettings):
    service: str = "{{ service }}"
    env: str = "local"
    database_url: str = "postgresql+psycopg://postgres:postgres@localhost/{{ service }}"
    redis_url: str = "redis://localhost:6379/0"
    celery_broker_url: str = "redis://localhost:6379/0"
    celery_result_backend: str | None = None
    skip_migrations: bool = False  # 测试态跳过建库迁移

    model_config = {"env_prefix": "YARCH_"}


settings = Settings()
