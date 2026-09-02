package io.github.yuandonghao.yarch.web.ratelimit;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/** 进程内固定窗口（单实例降级实现；生产多实例用 redis starter 的跨实例实现） */
public class InMemoryRateLimiter implements RateLimiter {

    private record Window(long windowStartMillis, AtomicLong count) {}

    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    @Override
    public boolean tryAcquire(String key, int permits, int windowSeconds) {
        long now = System.currentTimeMillis();
        long windowMillis = windowSeconds * 1000L;
        long currentWindow = now / windowMillis;
        Window window =
                windows.compute(
                        key,
                        (k, existing) ->
                                existing == null || existing.windowStartMillis() != currentWindow
                                        ? new Window(currentWindow, new AtomicLong(0))
                                        : existing);
        return window.count().incrementAndGet() <= permits;
    }
}
