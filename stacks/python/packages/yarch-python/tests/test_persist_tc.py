# stacks/python/packages/yarch-python/tests/test_persist_tc.py
import textwrap
from datetime import datetime

import pytest
from sqlalchemy import create_engine, select, text
from sqlalchemy.orm import Mapped, mapped_column
from yarch_python.persist import (
    AuditMixin,
    Base,
    SoftDeleteMixin,
    alembic_upgrade,
    ensure_database,
    not_deleted,
    page_of,
    sessionmaker_for,
)

pytestmark = pytest.mark.integration


@pytest.fixture(scope="module")
def pg_url():
    # testcontainers 4.15 社区口径（Task 8/9 已验证）：community.postgres；
    # driver 必须显式 psycopg（容器默认拼 psycopg2），get_connection_url 直接产出
    # postgresql+psycopg://… DSN（替代任务书 self_url 手工剥前缀的别扭拼法）
    from testcontainers.community.postgres import PostgresContainer

    with PostgresContainer("postgres:17-alpine", driver="psycopg") as c:
        url = c.get_connection_url()
        ensure_database(url)  # 幂等：已存在不炸
        yield url


class Row(Base, AuditMixin, SoftDeleteMixin):
    __tablename__ = "t_rows"
    id: Mapped[int] = mapped_column(primary_key=True, autoincrement=True)


def setup_table(url):
    Base.metadata.create_all(create_engine(url))  # 仅测试建表；业务工程用 Alembic
    return sessionmaker_for(url)()


def test_audit_and_soft_delete_and_d6(pg_url):
    session = setup_table(pg_url)
    rows = [Row() for _ in range(5)]
    session.add_all(rows)
    session.commit()
    r1 = session.get(Row, 1)
    assert isinstance(r1.created_at, datetime) and isinstance(r1.updated_at, datetime)

    r1.is_deleted = True
    session.commit()
    alive = session.scalars(select(Row).where(not_deleted(Row))).all()
    assert len(alive) == 4  # 逻辑删除不物理删
    assert session.get(Row, 1) is not None

    items, total = page_of(session, select(Row).where(not_deleted(Row)), page=3, page_size=2)
    assert items == [] and total == 4  # D6：越界空页 + 真实 total


def test_ensure_database_idempotent_and_identifier_checked(pg_url):
    ensure_database(pg_url)  # 再跑一次不炸
    # 仅替换 path 上的库名段（replace("/test", …) 会误伤 userinfo 里的 test:test）
    bad = pg_url.rsplit("/", 1)[0] + '/Bad"Name'
    with pytest.raises(ValueError):
        ensure_database(bad)


def test_ensure_database_creates_missing_db(pg_url):
    # 真实命中 CREATE DATABASE 分支（引擎级 AUTOCOMMIT）且建后幂等
    new_url = pg_url + "_created"
    ensure_database(new_url)
    with create_engine(new_url).connect() as conn:
        assert conn.execute(text("SELECT 1")).scalar() == 1
    ensure_database(new_url)  # 建过再跑不炸


def test_alembic_upgrade_runs_migration(pg_url, tmp_path):
    (tmp_path / "versions").mkdir()
    (tmp_path / "env.py").write_text(
        textwrap.dedent(
            """
            from alembic import context
            from sqlalchemy import create_engine, pool
            cfg = context.config
            target_metadata = None
            engine = create_engine(cfg.get_main_option("sqlalchemy.url"), poolclass=pool.NullPool)
            with engine.connect() as connection:
                context.configure(connection=connection, target_metadata=target_metadata)
                with context.begin_transaction():
                    context.run_migrations()
            """
        )
    )
    (tmp_path / "script.py.mako").write_text(
        "from alembic import op\n\n"
        "revision = ${repr(up_revision)}\ndown_revision = ${repr(down_revision)}\n"
        "def upgrade():\n    op.execute('CREATE TABLE IF NOT EXISTS alembic_probe (id int)')\n"
    )
    (tmp_path / "versions" / "0001.py").write_text(
        "revision = '0001'\ndown_revision = None\n\n"
        "from alembic import op\n\n"
        "def upgrade():\n"
        "    op.execute('CREATE TABLE IF NOT EXISTS alembic_probe (id int)')\n"
    )
    alembic_upgrade(str(tmp_path), pg_url)
    with create_engine(pg_url).connect() as conn:
        assert conn.execute(text("SELECT count(*) FROM alembic_probe")).scalar() == 0
