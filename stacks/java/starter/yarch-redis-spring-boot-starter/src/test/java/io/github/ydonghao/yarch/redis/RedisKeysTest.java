package io.github.ydonghao.yarch.redis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class RedisKeysTest {

    @Test
    void buildsColonSeparatedKeyWithServiceFirst() {
        assertEquals("ysaas:iam:user:10001", RedisKeys.of("ysaas").parts("iam", "user", 10001));
        assertEquals("yagent:session:token", RedisKeys.of("yagent").parts("session", "token"));
    }

    @Test
    void rejectsIllegalServiceName() {
        assertThrows(IllegalArgumentException.class, () -> RedisKeys.of("User"));
        assertThrows(IllegalArgumentException.class, () -> RedisKeys.of("user_1"));
        assertThrows(IllegalArgumentException.class, () -> RedisKeys.of(null));
    }

    @Test
    void rejectsBareGenericServiceName() {
        assertThrows(IllegalArgumentException.class, () -> RedisKeys.of("user"));
        assertThrows(IllegalArgumentException.class, () -> RedisKeys.of("gateway"));
    }

    @Test
    void rejectsIllegalSegmentAndOversize() {
        assertThrows(
                IllegalArgumentException.class, () -> RedisKeys.of("ysaas").parts("iam", "张三"));
        assertThrows(
                IllegalArgumentException.class, () -> RedisKeys.of("ysaas").parts("iam", "a b"));
        assertThrows(
                IllegalArgumentException.class,
                () -> RedisKeys.of("ysaas").parts("iam", "x".repeat(120)));
    }
}
