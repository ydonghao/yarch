"""TC fixtures：业务工程 conftest 里 pytest_plugins = ("yarch_python.testx.fixtures",)。"""

from collections.abc import Iterator

import pytest


@pytest.fixture(scope="session")
def pg_url() -> Iterator[str]:
    # testcontainers 4.15 社区口径（Task 8/10 已验证）：community.postgres + driver=psycopg，
    # get_connection_url 直接产出 postgresql+psycopg://… DSN（不剥前缀重拼）
    from testcontainers.community.postgres import PostgresContainer

    with PostgresContainer("postgres:17-alpine", driver="psycopg") as c:
        yield c.get_connection_url()


@pytest.fixture(scope="session")
def redis_url() -> Iterator[str]:
    # community.redis 实测无 get_connection_url()：按 get_client 同款 host/port 拼 redis://
    from testcontainers.community.redis import RedisContainer

    with RedisContainer() as c:
        yield f"redis://{c.get_container_host_ip()}:{c.get_exposed_port(c.port)}"
