package io.github.ydonghao.yarch.web.ratelimit;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 接口限流（契约 1006/RATE_LIMITED/429 的实现，G1）：固定窗口计数， 超限抛 BusinessException(RATE_LIMITED)。存储默认进程内（单实例），
 * 引入 yarch-redis starter 后自动升级跨实例实现。
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface RateLimited {

    /** 限流域（拼入 key：服务名:rl:{key}） */
    String key();

    /** 窗口内允许的请求数 */
    int permits();

    /** 窗口长度（秒） */
    int windowSeconds();
}
