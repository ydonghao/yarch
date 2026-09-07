package io.github.ydonghao.yarch.redis;

import io.github.ydonghao.yarch.web.security.NonceStore;
import java.time.Duration;
import org.springframework.data.redis.core.StringRedisTemplate;

/** 签名防重放 nonce 的 Redis 实现（SET NX PX，跨实例——rest-conventions.md 签名总则）。 */
public class RedisNonceStore implements NonceStore {

    private final StringRedisTemplate redis;

    public RedisNonceStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public boolean consume(String key, Duration ttl) {
        try {
            return Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(key, "1", ttl));
        } catch (Exception e) {
            // Redis 故障时放行（可用性优先）：签名与时间窗校验仍在，仅重放窗口暂失防守
            return true;
        }
    }
}
