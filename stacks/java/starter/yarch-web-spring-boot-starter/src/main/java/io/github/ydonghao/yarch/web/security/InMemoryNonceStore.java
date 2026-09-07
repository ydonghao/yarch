package io.github.ydonghao.yarch.web.security;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * nonce 一次性消费的进程内降级实现：到期惰性清理 + 容量硬上限（防 nonce 高基数打爆内存）。 多实例部署请引入 yarch-redis starter 自动升级为 Redis
 * 实现（跨实例防重放）。
 */
public class InMemoryNonceStore implements NonceStore {

    private static final int SWEEP_THRESHOLD = 8_192;
    private static final int HARD_CAP = 131_072;
    private static final int OVERFLOW_EVICT_BATCH = 1_024;

    private final Map<String, Long> expirations = new ConcurrentHashMap<>();

    @Override
    public boolean consume(String key, Duration ttl) {
        long now = System.currentTimeMillis();
        if (expirations.size() >= SWEEP_THRESHOLD) {
            sweep(now);
        }
        long deadline = now + ttl.toMillis();
        boolean[] firstSeen = new boolean[1];
        expirations.compute(
                key,
                (k, existing) -> {
                    if (existing != null && existing > now) {
                        firstSeen[0] = false;
                        return existing;
                    }
                    firstSeen[0] = true;
                    return deadline;
                });
        return firstSeen[0];
    }

    private void sweep(long now) {
        expirations.values().removeIf(deadline -> deadline <= now);
        if (expirations.size() >= HARD_CAP) {
            // 超过硬上限：丢弃一批任意条目换内存安全（默认实现的可接受降级）
            var it = expirations.values().iterator();
            for (int i = 0; i < OVERFLOW_EVICT_BATCH && it.hasNext(); i++) {
                it.next();
                it.remove();
            }
        }
    }
}
