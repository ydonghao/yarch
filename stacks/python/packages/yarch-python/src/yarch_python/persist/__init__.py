"""persist：PG 规约件（postgresql.md v1.0）——审计/逻辑删除/分页下推/建库/Alembic。

禁 create_all 于业务（迁移版本化六-1）。
"""

import re
from datetime import datetime
from typing import Any
from urllib.parse import urlsplit, urlunsplit

from sqlalchemy import (
    ColumnElement,
    DateTime,
    Engine,
    Select,
    create_engine,
    func,
    select,
    text,
)
from sqlalchemy.orm import DeclarativeBase, Mapped, Session, mapped_column, sessionmaker


class Base(DeclarativeBase):
    pass


class AuditMixin:
    """created_at/updated_at：timestamptz，server_default now()，updated_at onupdate。"""

    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), server_default=func.now(), nullable=False
    )
    updated_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), server_default=func.now(), onupdate=func.now(), nullable=False
    )


class SoftDeleteMixin:
    is_deleted: Mapped[bool] = mapped_column(default=False, server_default="false", nullable=False)


def engine_for(url: str) -> Engine:
    return create_engine(url, pool_pre_ping=True)


def sessionmaker_for(url: str) -> sessionmaker:
    return sessionmaker(bind=engine_for(url), expire_on_commit=False)


_DBNAME_RE = re.compile(r"^[a-z][a-z0-9_]{0,62}$")


def ensure_database(url: str) -> None:
    """独立 database 自动建库（幂等）。url 形如 postgresql+psycopg://user:pass@host/db。"""
    parts = urlsplit(url)
    dbname = parts.path.lstrip("/")
    if not _DBNAME_RE.match(dbname):
        raise ValueError(f"库名不合规（^[a-z][a-z0-9_]{{0,62}}$）：{dbname!r}")
    admin = urlunsplit(parts._replace(path="/postgres"))
    # CREATE DATABASE 不能在事务内执行：引擎级 AUTOCOMMIT
    # （连接级 execution_options(autocommit=True) 在 SQLAlchemy 2.x 已弃用）
    eng = create_engine(admin, isolation_level="AUTOCOMMIT")
    try:
        with eng.connect() as conn:
            exists = conn.execute(
                text("SELECT 1 FROM pg_database WHERE datname = :d"), {"d": dbname}
            ).scalar()
            if not exists:
                conn.execute(text(f'CREATE DATABASE "{dbname}"'))
    finally:
        eng.dispose()


def page_of(session: Session, stmt: Select, page: int, page_size: int) -> tuple[list, int]:
    """count + offset/limit 双查下推；D6（越界空页 + 真实 total）天然成立。"""
    total = session.execute(
        select(func.count()).select_from(stmt.order_by(None).subquery())
    ).scalar_one()
    items = session.execute(stmt.offset((page - 1) * page_size).limit(page_size)).scalars().all()
    return list(items), int(total)


def not_deleted(model_cls: Any) -> ColumnElement[bool]:
    return model_cls.is_deleted.is_(False)


def alembic_upgrade(script_location: str, url: str) -> None:
    from alembic import command
    from alembic.config import Config

    cfg = Config()
    cfg.set_main_option("script_location", script_location)
    cfg.set_main_option("sqlalchemy.url", url)
    command.upgrade(cfg, "head")
