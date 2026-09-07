# stacks/python/packages/yarch-python/tests/test_celeryx_tc.py
import pytest
from yarch_python import celeryx

pytestmark = pytest.mark.integration


@pytest.fixture(scope="module")
def redis():
    # fixture 口径与 test_redix_tc 一致：community.redis 的 get_client()（module 级起一次容器）
    from testcontainers.community.redis import RedisContainer

    with RedisContainer() as c:
        yield c.get_client()


def test_beat_guard_single_instance(redis):
    celeryx.beat_guard(redis, "ysaas-scan", ttl_s=30)
    with pytest.raises(RuntimeError):
        celeryx.beat_guard(redis, "ysaas-scan", ttl_s=30)


def test_run_once_dedup(redis):
    assert celeryx.run_once(redis, "ysaas-scan:once:job1") is True
    assert celeryx.run_once(redis, "ysaas-scan:once:job1") is False
