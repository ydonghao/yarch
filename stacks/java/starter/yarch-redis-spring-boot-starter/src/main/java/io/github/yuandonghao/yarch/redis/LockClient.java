package io.github.yuandonghao.yarch.redis;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.redis.connection.RedisStringCommands;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.types.Expiration;

/**
 * 分布式锁标准实现（redis.md 四-7）：SET key uniqueValue NX PX + Lua 比对持有者后释放； 锁粒度小、TTL
 * 覆盖业务时长；默认路径超时放弃（看门狗续期属显式增强，不默认提供）。
 */
public class LockClient {

    private static final DefaultRedisScript<Long> RELEASE_SCRIPT =
            new DefaultRedisScript<>(
                    "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del',"
                            + " KEYS[1]) else return 0 end",
                    Long.class);

    private final StringRedisTemplate redis;

    public LockClient(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /** 尝试获取：成功返回持有者 token（释放时校验），失败返回 empty */
    public Optional<String> acquire(String key, Duration ttl) {
        String token = UUID.randomUUID().toString();
        Boolean ok =
                redis.execute(
                        (RedisCallback<Boolean>)
                                connection ->
                                        connection
                                                .stringCommands()
                                                .set(
                                                        key.getBytes(StandardCharsets.UTF_8),
                                                        token.getBytes(StandardCharsets.UTF_8),
                                                        Expiration.from(ttl),
                                                        RedisStringCommands.SetOption
                                                                .SET_IF_ABSENT));
        return Boolean.TRUE.equals(ok) ? Optional.of(token) : Optional.empty();
    }

    /** 释放：必须校验持有者（Lua 比对后 DEL，防误解他人锁） */
    public boolean release(String key, String token) {
        Long released = redis.execute(RELEASE_SCRIPT, List.of(key), token);
        return released != null && released == 1L;
    }

    /** 带等待窗口的获取：50ms 轮询，超时放弃（redis.md 四-3：有限等待 + 退避纪律） */
    public Optional<String> tryAcquire(String key, Duration ttl, Duration wait) {
        long deadline = System.nanoTime() + wait.toNanos();
        while (true) {
            Optional<String> token = acquire(key, ttl);
            if (token.isPresent()) {
                return token;
            }
            if (System.nanoTime() >= deadline) {
                return Optional.empty();
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return Optional.empty();
            }
        }
    }
}
