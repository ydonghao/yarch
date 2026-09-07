package io.github.ydonghao.yarch.web.idempotency;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 外部幂等（契约 rest-conventions.md「幂等总则」-1）：unsafe 方法可带请求头 {@code Idempotency-Key} （客户端 UUID，作用域 =
 * 单服务单资源类型）。
 *
 * <p>语义：首次执行后存储 (key, 请求摘要, 响应)，TTL 默认 24h（存储用 Redis SET NX PX，由 yarch-redis-spring-boot-starter
 * 提供实现；无 Redis 时降级为进程内实现，仅单实例语义）。 同键同参回放原响应；同键异参 1007/409；并发同键等待后回放或 1007。
 *
 * <p>请求头缺失时按普通请求执行（契约口径：可带）。
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Idempotent {

    /** 资源类型（幂等作用域的一部分），如 "orders" */
    String resource();

    /** 存储时长（秒），契约要求 ≥ 24h */
    long ttlSeconds() default 86_400L;
}
