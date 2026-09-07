package io.github.ydonghao.yarch.redis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.github.ydonghao.yarch.test.containers.RedisTestDb;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** Redis 规约行为级验收：JSON 序列化（三-1）、锁互斥与持有者校验（四-7）、幂等存储语义 */
@SpringBootTest(
        classes = RedisContractTest.App.class,
        properties = "yarch.redis.json-trusted-packages=io.github.ydonghao")
class RedisContractTest {

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class App {}

    static final RedisTestDb redis = RedisTestDb.dockerAvailable() ? RedisTestDb.start() : null;

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        if (redis != null) {
            redis.register(registry);
        }
    }

    @BeforeAll
    static void requireDocker() {
        assumeTrue(redis != null, "本机无 Docker，跳过 Redis 契约测试");
    }

    @Autowired StringRedisTemplate stringRedis;

    @Autowired LockClient lockClient;

    @Autowired io.github.ydonghao.yarch.web.idempotency.IdempotencyStore idempotencyStore;

    @Autowired org.springframework.data.redis.core.RedisTemplate<String, Object> yarchRedisTemplate;

    record SamplePayload(String name, long balance) {}

    @Test
    void valueIsLanguageNeutralJsonNotJdkSerialization() {
        String key = RedisKeys.of("yarch-test").parts("sample", "obj", 1);
        yarchRedisTemplate
                .opsForValue()
                .set(key, new SamplePayload("alice", 199), Duration.ofSeconds(60));

        String raw = stringRedis.opsForValue().get(key);
        assertTrue(
                raw.contains("\"name\":\"alice\"") && raw.contains("\"balance\":199"),
                "value 必须是可读 JSON（redis.md 三-1 禁 JDK 序列化）: " + raw);
        SamplePayload back = (SamplePayload) yarchRedisTemplate.opsForValue().get(key);
        assertEquals(new SamplePayload("alice", 199), back);
    }

    @Test
    void poisonedValueTypeIdIsRejected() {
        // 安全回归：Redis 值被注入白名单外 @class（经典反序列化 gadget 形态）时，
        // 回读必须失败而非实例化任意类（白名单默认只信 JDK 基础类型 + 显式信任包）
        String key = RedisKeys.of("yarch-test").parts("sample", "poison", 1);
        stringRedis
                .opsForValue()
                .set(
                        key,
                        "{\"@class\":\"javax.naming.InitialContext\",\"prop\":\"x\"}",
                        Duration.ofSeconds(60));
        assertThrows(
                Exception.class,
                () -> yarchRedisTemplate.opsForValue().get(key),
                "白名单外 @class 不得被实例化");
    }

    @Test
    void lockIsMutuallyExclusiveAndOwnerChecked() throws InterruptedException {
        String key = RedisKeys.of("yarch-test").parts("lock", "demo");
        Optional<String> t1 = lockClient.acquire(key, Duration.ofSeconds(30));
        assertTrue(t1.isPresent());

        assertFalse(lockClient.acquire(key, Duration.ofSeconds(30)).isPresent(), "持有期内他人不得获取");

        assertFalse(lockClient.release(key, "wrong-token"), "错误 token 不得释放他人锁");
        assertTrue(lockClient.release(key, t1.get()));

        Optional<String> t2 = lockClient.acquire(key, Duration.ofSeconds(30));
        assertTrue(t2.isPresent(), "释放后可再获取");
        assertTrue(lockClient.release(key, t2.get()));
    }

    @Test
    void concurrentAcquireHasExactlyOneWinner() throws InterruptedException {
        String key = RedisKeys.of("yarch-test").parts("lock", "race");
        CountDownLatch ready = new CountDownLatch(4);
        CountDownLatch done = new CountDownLatch(4);
        AtomicInteger wins = new AtomicInteger();
        for (int i = 0; i < 4; i++) {
            new Thread(
                            () -> {
                                ready.countDown();
                                try {
                                    ready.await();
                                    if (lockClient
                                            .acquire(key, Duration.ofSeconds(5))
                                            .isPresent()) {
                                        wins.incrementAndGet();
                                    }
                                } catch (InterruptedException ignored) {
                                    Thread.currentThread().interrupt();
                                } finally {
                                    done.countDown();
                                }
                            })
                    .start();
        }
        done.await();
        assertEquals(1, wins.get(), "并发同键恰好一个持有者");
    }

    @Test
    void idempotencyStoreReservesSavesAndDiscards() {
        String key = RedisKeys.of("yarch-test").parts("idem", "demo", "k1");
        stringRedis.delete(key);

        assertTrue(idempotencyStore.reserve(key, "hash-a", Duration.ofSeconds(60)));
        assertFalse(idempotencyStore.reserve(key, "hash-b", Duration.ofSeconds(60)), "同键占位互斥");

        Optional<io.github.ydonghao.yarch.web.idempotency.IdempotencyStore.StoredResponse> pending =
                idempotencyStore.find(key);
        assertTrue(pending.isPresent() && pending.get().pending(), "占位后为 PENDING");

        idempotencyStore.save(
                key, "hash-a", 201, "application/json", "{\"code\":0}", Duration.ofSeconds(60));
        Optional<io.github.ydonghao.yarch.web.idempotency.IdempotencyStore.StoredResponse> done =
                idempotencyStore.find(key);
        assertTrue(done.isPresent() && !done.get().pending());
        assertEquals("hash-a", done.get().requestHash());
        assertEquals(201, done.get().status());
        assertEquals("{\"code\":0}", done.get().body());

        idempotencyStore.discard(key);
        assertTrue(idempotencyStore.find(key).isEmpty());
        assertNull(stringRedis.opsForValue().get(key));
    }
}
