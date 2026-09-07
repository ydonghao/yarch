"""redix：Redis 规约件（redis.md v1.0）——key 首段=服务名、JSON 值、锁、幂等存储、固定窗口限流。"""
import json
from typing import Any

from redis import Redis
from redis.lock import Lock

IDEMPOTENCY_TTL_S = 24 * 3600  # 幂等 TTL ≥ 24h（rest-conventions 幂等总则-1）

_FIXED_WINDOW_LUA = """
local c = redis.call('INCR', KEYS[1])
if c == 1 then redis.call('PEXPIRE', KEYS[1], ARGV[1]) end
return c
"""


class Keys:
    def __init__(self, service: str):
        if not service:
            raise ValueError("service 必填（key 首段=服务名，registry 租户边界）")
        self.service = service

    def of(self, *parts: str) -> str:
        if any(not p for p in parts):
            raise ValueError("key 段不得为空")
        return ":".join([self.service, *parts])


class Cache:
    def __init__(self, redis: Redis):
        self.redis = redis

    def get_json(self, key: str) -> Any:
        raw = self.redis.get(key)
        return json.loads(raw) if raw is not None else None

    def set_json(self, key: str, value: Any, ttl_s: int) -> None:
        self.redis.set(key, json.dumps(value, ensure_ascii=False), ex=ttl_s)


class IdempotencyStore:
    _DONE = "done"
    _PENDING = "pending"

    def __init__(self, redis: Redis):
        self.redis = redis

    def _dump(self, digest: str, status: str, resp: tuple[int, str] | None = None) -> str:
        d: dict[str, Any] = {"digest": digest, "status": status}
        if resp:
            d["statusCode"], d["body"] = resp
        return json.dumps(d, ensure_ascii=False)

    def acquire(self, key: str, digest: str) -> str:
        got = self.redis.set(key, self._dump(digest, self._PENDING), nx=True, ex=IDEMPOTENCY_TTL_S)
        if got:
            return "acquired"
        cur = json.loads(self.redis.get(key) or "{}")
        if cur.get("digest") != digest:
            return "mismatch"
        return "replay" if cur.get("status") == self._DONE else "pending"

    def store_response(self, key: str, status_code: int, body: str) -> None:
        cur = json.loads(self.redis.get(key) or "{}")
        self.redis.set(key, self._dump(cur.get("digest", ""), self._DONE, (status_code, body)),
                       ex=IDEMPOTENCY_TTL_S)

    def load(self, key: str) -> tuple[int, str] | None:
        cur = json.loads(self.redis.get(key) or "{}")
        if cur.get("status") != self._DONE:
            return None
        return int(cur["statusCode"]), cur["body"]


class FixedWindowLimiter:
    def __init__(self, redis: Redis):
        self.redis = redis

    def hit(self, key: str, window_s: int, limit: int) -> bool:
        c = self.redis.eval(_FIXED_WINDOW_LUA, 1, key, window_s * 1000)
        return int(c) <= limit


def lock(redis: Redis, name: str, *, timeout_s: float = 10) -> Lock:
    return redis.lock(name, timeout=timeout_s)
