# stacks/python/packages/yarch-python/tests/test_captcha_tc.py
"""验证码 Redis 行为级（captcha.md 三）：GETDEL 原子一次性 + TTL + key 形状 + V6 并发恰一过。"""

import concurrent.futures

import pytest
from yarch_python.captcha import CaptchaService, CaptchaStore, Options
from yarch_python.redix import Keys

pytestmark = pytest.mark.integration


@pytest.fixture(scope="module")
def redis():
    # testcontainers 4.15：community.redis 的 get_client() 直取客户端（module 级共享容器）
    from testcontainers.community.redis import RedisContainer

    with RedisContainer() as c:
        yield c.get_client()


@pytest.fixture()
def svc(redis):
    return CaptchaService(CaptchaStore(redis), Keys("svc"), Options())


def test_v7_ttl_and_key_shape(redis, svc):
    issued = svc.issue()
    key = f"svc:captcha:image:{issued.key}"  # 三-5：{服务名}:captcha:{provider}:{key}
    assert redis.exists(key) == 1
    ttl = redis.ttl(key)
    assert 0 < ttl <= 120  # V7：默认档 TTL 窗口


def test_v6_concurrent_verify_exactly_one_wins(redis, svc):
    issued = svc.issue()
    key = f"svc:captcha:image:{issued.key}"
    answer = redis.get(key)

    with concurrent.futures.ThreadPoolExecutor(max_workers=8) as pool:
        results = list(pool.map(lambda _: svc.verify(None, issued.key, answer.lower()), range(8)))
    assert sum(results) == 1  # V6：GETDEL 原子——并发校验恰一过
    assert redis.get(key) is None  # V2：校验后 key 已消费


def test_v3_wrong_answer_consumes_key(redis, svc):
    issued = svc.issue()
    key = f"svc:captcha:image:{issued.key}"
    answer = redis.get(key)
    assert not svc.verify(None, issued.key, "XXXX" if answer != "XXXX" else "YYYY")
    assert redis.get(key) is None  # V3：错答案同样消费（防重放枚举）
