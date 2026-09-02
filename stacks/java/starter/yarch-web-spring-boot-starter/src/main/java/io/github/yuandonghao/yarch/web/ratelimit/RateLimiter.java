package io.github.yuandonghao.yarch.web.ratelimit;

/** 限流存储 SPI：默认进程内；redis starter 提供跨实例实现（INCR+EXPIRE） */
public interface RateLimiter {

    boolean tryAcquire(String key, int permits, int windowSeconds);
}
