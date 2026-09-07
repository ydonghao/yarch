package io.github.ydonghao.yarch.web.idempotency;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** 进程内幂等存储（降级实现）：无 Redis 时的默认 bean，仅单实例语义。 生产多实例必须引入 yarch-redis-spring-boot-starter 获得跨实例实现。 */
public class InMemoryIdempotencyStore implements IdempotencyStore {

    private record Entry(
            String requestHash,
            Integer status,
            String contentType,
            String body,
            long expireAtMillis) {}

    private final Map<String, Entry> store = new ConcurrentHashMap<>();

    @Override
    public Optional<StoredResponse> find(String key) {
        Entry e = store.get(key);
        if (e == null) {
            return Optional.empty();
        }
        if (e.expireAtMillis() < System.currentTimeMillis()) {
            store.remove(key);
            return Optional.empty();
        }
        return Optional.of(
                new StoredResponse(e.requestHash(), e.status(), e.contentType(), e.body()));
    }

    @Override
    public boolean reserve(String key, String requestHash, Duration ttl) {
        purge(key);
        Entry pending =
                new Entry(
                        requestHash, null, null, null, System.currentTimeMillis() + ttl.toMillis());
        return store.putIfAbsent(key, pending) == null;
    }

    @Override
    public void save(
            String key,
            String requestHash,
            int status,
            String contentType,
            String body,
            Duration ttl) {
        store.put(
                key,
                new Entry(
                        requestHash,
                        status,
                        contentType,
                        body,
                        System.currentTimeMillis() + ttl.toMillis()));
    }

    @Override
    public void discard(String key) {
        store.remove(key);
    }

    private void purge(String key) {
        Entry e = store.get(key);
        if (e != null && e.expireAtMillis() < System.currentTimeMillis()) {
            store.remove(key);
        }
    }
}
