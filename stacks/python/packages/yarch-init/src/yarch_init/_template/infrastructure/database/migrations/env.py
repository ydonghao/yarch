from alembic import context
from sqlalchemy import create_engine, pool

from infrastructure.database.models import Base

config = context.config
target_metadata = Base.metadata


def run_migrations_online() -> None:
    url = config.get_main_option("sqlalchemy.url")
    assert url is not None  # persist.alembic_upgrade 必然注入 sqlalchemy.url
    engine = create_engine(url, poolclass=pool.NullPool)
    with engine.connect() as connection:
        context.configure(connection=connection, target_metadata=target_metadata)
        with context.begin_transaction():
            context.run_migrations()


run_migrations_online()
