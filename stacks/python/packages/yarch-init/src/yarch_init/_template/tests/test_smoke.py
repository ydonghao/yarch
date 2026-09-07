"""冒烟：信封/分页/1001/1002/1004 全链路（TestClient + TC PG/Redis）。"""
import io
import os

import pytest
from fastapi.testclient import TestClient
from yarch_python import logx
from yarch_python.testx import assert_envelope, assert_page_data

os.environ.setdefault("YARCH_SKIP_MIGRATIONS", "true")

pytestmark = pytest.mark.integration


@pytest.fixture(scope="module")
def client(pg_url, redis_url):
    os.environ["YARCH_DATABASE_URL"] = pg_url
    os.environ["YARCH_REDIS_URL"] = redis_url
    os.environ["YARCH_SKIP_MIGRATIONS"] = "false"
    # env 就绪后才 import application.app：settings 单例于构造时读 env
    from yarch_python.persist import alembic_upgrade  # noqa: E402

    from application.app import create_app  # noqa: E402

    logx.setup("{{ service }}", "local", sink=io.StringIO())
    alembic_upgrade("infrastructure/database/migrations", pg_url)
    return TestClient(create_app())


def test_healthz_envelope(client):
    r = client.get("/healthz")
    assert r.status_code == 200
    assert_envelope(r.json(), code=0)
    assert r.json()["traceId"] == r.headers["x-trace-id"]


def test_users_crud_and_page(client):
    r = client.post("/api/v1/users", json={"username": "alice", "email": "a@b.c"})
    assert r.status_code == 201
    assert_envelope(r.json(), code=0)
    uid = r.json()["data"]["id"]
    assert client.get(f"/api/v1/users/{uid}").json()["data"]["username"] == "alice"
    d = client.get("/api/v1/users").json()["data"]
    assert_page_data(d, total=1, page=1, page_size=20)


def test_1001_1002_1004(client):
    assert client.post("/api/v1/users", json={"username": "x"}).json()["code"] == 1001
    r = client.post("/api/v1/users", content=b"{bad",
                    headers={"content-type": "application/json"})
    assert r.json()["code"] == 1002
    assert client.get("/api/v1/users/00000000-0000-0000-0000-000000000000").json()["code"] == 1004


def test_worker_task_trace_and_dedup(redis_url):
    from tasks import users_task

    users_task.sync_user.apply(args=["u1"], headers={"traceId": "0af7651916cd43dd8448eb211c80319c"})
    import redis as redis_lib

    r = redis_lib.Redis.from_url(redis_url)
    r.delete("{{ service }}:once:users-sync:u1")
