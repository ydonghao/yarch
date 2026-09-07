# stacks/python/packages/yarch-python/tests/test_redix_tc.py
import pytest
from yarch_python.redix import Cache, FixedWindowLimiter, IdempotencyStore, Keys, lock

pytestmark = pytest.mark.integration


@pytest.fixture(scope="module")
def redis():
    # testcontainers 4.15：旧 testcontainers.redis 已弃用且无 get_connection_url，
    # 用 community.redis 的 get_client() 直取客户端（module 级起一次容器共享全部用例）
    from testcontainers.community.redis import RedisContainer
    with RedisContainer() as c:
        yield c.get_client()


def test_json_value_and_keys_shape(redis):
    keys = Keys("ysaas-scan")
    Cache(redis).set_json(keys.of("cache", "u1"), {"a": 1}, ttl_s=60)
    assert Cache(redis).get_json(keys.of("cache", "u1")) == {"a": 1}
    assert redis.exists("ysaas-scan:cache:u1") == 1


def test_lock_mutual_exclusion_and_token_release(redis):
    import redis as redis_lib
    lk = lock(redis, "ysaas-scan:lock:job1", timeout_s=5)
    assert lk.acquire(blocking=False)
    lk2 = lock(redis, "ysaas-scan:lock:job1", timeout_s=5)
    assert not lk2.acquire(blocking=False)
    lk.release()  # 笔误修正：Lock.release() 返回 None（token 不匹配抛 LockError），非 bool
    with pytest.raises(redis_lib.exceptions.LockError):
        lk2.release()  # 未持有 token 者不得释放他人锁（token 释放语义内建）
    assert lock(redis, "ysaas-scan:lock:job1", timeout_s=5).acquire(blocking=False)


def test_idempotency_three_state_flow(redis):
    store = IdempotencyStore(redis)
    k = "ysaas-scan:idem:k1"
    assert store.acquire(k, "d1") == "acquired"
    assert store.acquire(k, "d1") == "pending"
    assert store.acquire(k, "d2") == "mismatch"
    store.store_response(k, 201, '{"code":0}')
    assert store.acquire(k, "d1") == "replay"
    assert store.load(k) == (201, '{"code":0}')


def test_rate_limiter_fixed_window(redis):
    limiter = FixedWindowLimiter(redis)
    k = "ysaas-scan:rl:t1"
    results = [limiter.hit(k, window_s=60, limit=3) for _ in range(5)]
    assert results == [True, True, True, False, False]
