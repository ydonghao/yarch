"""配置：.env 单文件（pydantic-settings）；env 值域 local/dev/staging/prod（logging-trace.md）。"""
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    service: str = "{{ service }}"
    env: str = "local"
    database_url: str = "postgresql+psycopg://postgres:postgres@localhost/{{ package }}"
    redis_url: str = "redis://localhost:6379/0"
    celery_broker_url: str = "redis://localhost:6379/0"
    celery_result_backend: str | None = None
    skip_migrations: bool = False  # 测试态跳过建库迁移

    # 真实环境变量优先于 .env（testx 即靠注入 os.environ 覆盖）；
    # extra=ignore：.env 允许混放第三方变量不炸启动
    model_config = SettingsConfigDict(env_prefix="YARCH_", env_file=".env", extra="ignore")


settings = Settings()
