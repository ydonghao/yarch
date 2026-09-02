package io.github.yuandonghao.yarch.redis;

import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Redis key 构建器（redis.md v1.0「Key 设计规约」的编译期防线）： 格式
 * 服务名:业务域:实体[:字段][:ID]——首段必须是服务名（共享实例的租户边界，registry.md 一-1 校验）； 仅小写字母/数字/冒号/下划线/短横线，总长 ≤ 128 字节。
 */
public final class RedisKeys {

    private static final Pattern SERVICE = Pattern.compile("^[a-z][a-z0-9-]{1,31}$");
    private static final Pattern PART = Pattern.compile("^[A-Za-z0-9_-]{1,64}$");
    private static final int MAX_KEY_BYTES = 128;

    /** registry.md 一-2：禁裸通用词单独成名 */
    private static final Set<String> BANNED_SERVICES =
            Set.of("user", "api", "admin", "gateway", "common");

    private final String service;

    private RedisKeys(String service) {
        this.service = service;
    }

    public static RedisKeys of(String service) {
        if (service == null || !SERVICE.matcher(service).matches()) {
            throw new IllegalArgumentException(
                    "服务名非法（redis.md 二-1 / registry.md 一-1）：须 ^[a-z][a-z0-9-]{1,31}$，实际 " + service);
        }
        if (BANNED_SERVICES.contains(service)) {
            throw new IllegalArgumentException("禁裸通用词成名（registry.md 一-2）：" + service);
        }
        return new RedisKeys(service);
    }

    /** 追加业务段：业务域/实体/字段/ID，如 parts("iam", "user", 10001) */
    public String parts(Object... segments) {
        StringBuilder sb = new StringBuilder(service);
        for (Object segment : segments) {
            String s = String.valueOf(segment);
            if (!PART.matcher(s).matches()) {
                throw new IllegalArgumentException("key 段非法（仅字母数字下划线短横线，1~64 字符）：" + s);
            }
            sb.append(':').append(s);
        }
        String key = sb.toString();
        if (key.getBytes(StandardCharsets.UTF_8).length > MAX_KEY_BYTES) {
            throw new IllegalArgumentException("key 超 128 字节（redis.md 二-3）：" + key);
        }
        return key;
    }
}
