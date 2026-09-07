# stacks/python/packages/yarch-python/tests/test_testx.py
import pytest
from yarch_python.testx import CODE_TABLE, assert_envelope, assert_ndjson, assert_page_data

# 自证 fixtures 的文档化消费口径（业务工程 conftest 同款声明方式）
pytest_plugins = ("yarch_python.testx.fixtures",)


def test_code_table_has_14_rows():
    assert len(CODE_TABLE) == 14 and CODE_TABLE[0] == (0, "OK", "成功", 200)


def test_assert_envelope_ok():
    assert_envelope({"code": 0, "message": "成功", "data": None, "traceId": "t"}, code=0)


def test_assert_envelope_rejects_extra_or_missing_keys():
    with pytest.raises(AssertionError):
        assert_envelope({"code": 0, "message": "x", "data": None}, code=0)
    with pytest.raises(AssertionError):
        assert_envelope(
            {"code": 0, "message": "x", "data": None, "traceId": "t", "extra": 1}, code=0
        )


def test_assert_envelope_error_data_must_be_null():
    with pytest.raises(AssertionError):
        assert_envelope({"code": 1001, "message": "m", "data": {}, "traceId": "t"}, code=1001)


def test_assert_page_data():
    assert_page_data(
        {"list": [], "total": 5, "page": 3, "pageSize": 2}, total=5, page=3, page_size=2
    )
    with pytest.raises(AssertionError):
        assert_page_data(
            {"list": None, "total": 5, "page": 3, "pageSize": 2}, total=5, page=3, page_size=2
        )


def test_assert_ndjson():
    line = (
        '{"ts":"2026-09-07T02:45:07.123Z","level":"INFO","service":"s","env":"local",'
        '"traceId":"t","logger":"l","msg":"request completed"}'
    )
    assert_ndjson(line, service="s", msg="request completed")
    with pytest.raises(AssertionError):
        assert_ndjson(line.replace('"INFO"', '"info"'))
    with pytest.raises(AssertionError):
        assert_ndjson(line.replace(".123Z", ".123456Z"))


@pytest.mark.integration
def test_tc_fixtures_boot_real_containers(pg_url, redis_url):
    # 自证：session 级 fixtures 能起真容器并给出可用 DSN/url（需 docker，OrbStack）
    from redis import Redis
    from sqlalchemy import create_engine, text

    assert pg_url.startswith("postgresql+psycopg://")
    with create_engine(pg_url).connect() as conn:
        assert conn.execute(text("SELECT 1")).scalar() == 1
    assert redis_url.startswith("redis://")
    assert Redis.from_url(redis_url).ping() is True
