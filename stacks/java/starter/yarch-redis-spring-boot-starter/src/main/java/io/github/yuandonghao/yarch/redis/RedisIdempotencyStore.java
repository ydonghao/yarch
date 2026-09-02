package io.github.yuandonghao.yarch.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.yuandonghao.yarch.web.idempotency.IdempotencyStore;
import java.time.Duration;
import java.util.Optional;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * 幂等存储的 Redis 实现（rest-conventions.md 幂等总则-1：Redis SET NX PX、TTL ≥ 24h）。 条目
 * JSON：{"hash":...,"status":...,"contentType":...,"body":...}；status 为 null 表示 PENDING。
 */
public class RedisIdempotencyStore implements IdempotencyStore {

    private final StringRedisTemplate redis;
    private final ObjectMapper mapper = new ObjectMapper();

    public RedisIdempotencyStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public Optional<StoredResponse> find(String key) {
        String json = redis.opsForValue().get(key);
        if (json == null) {
            return Optional.empty();
        }
        try {
            Entry entry = mapper.readValue(json, Entry.class);
            return Optional.of(
                    new StoredResponse(entry.hash, entry.status, entry.contentType, entry.body));
        } catch (Exception e) {
            return Optional.empty(); // 损坏条目视为不存在，走重新执行路径
        }
    }

    @Override
    public boolean reserve(String key, String requestHash, Duration ttl) {
        Entry pending = new Entry(requestHash, null, null, null);
        try {
            return Boolean.TRUE.equals(
                    redis.opsForValue().setIfAbsent(key, mapper.writeValueAsString(pending), ttl));
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public void save(
            String key,
            String requestHash,
            int status,
            String contentType,
            String body,
            Duration ttl) {
        Entry done = new Entry(requestHash, status, contentType, body);
        try {
            redis.opsForValue().set(key, mapper.writeValueAsString(done), ttl);
        } catch (Exception e) {
            throw new IllegalStateException("幂等条目写入失败: " + key, e);
        }
    }

    @Override
    public void discard(String key) {
        redis.delete(key);
    }

    /** 存储条目形状（value 为 JSON，语言无关——redis.md 三-1 禁语言原生序列化） */
    static final class Entry {
        public String hash;
        public Integer status;
        public String contentType;
        public String body;

        public Entry() {}

        public Entry(String hash, Integer status, String contentType, String body) {
            this.hash = hash;
            this.status = status;
            this.contentType = contentType;
            this.body = body;
        }
    }
}
