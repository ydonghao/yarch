package io.github.ydonghao.yarch.web.ratelimit;

import io.github.ydonghao.yarch.common.code.BusinessException;
import io.github.ydonghao.yarch.common.code.GlobalErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

public class RateLimitInterceptor implements HandlerInterceptor {

    private final RateLimiter rateLimiter;
    private final String serviceName;

    public RateLimitInterceptor(RateLimiter rateLimiter, String serviceName) {
        this.rateLimiter = rateLimiter;
        this.serviceName = serviceName;
    }

    @Override
    public boolean preHandle(
            HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }
        RateLimited rateLimited = handlerMethod.getMethodAnnotation(RateLimited.class);
        if (rateLimited == null) {
            return true;
        }
        // key 首段=服务名（redis.md 二-1 租户边界口径）
        boolean allowed =
                rateLimiter.tryAcquire(
                        serviceName + ":rl:" + rateLimited.key(),
                        rateLimited.permits(),
                        rateLimited.windowSeconds());
        if (!allowed) {
            throw new BusinessException(GlobalErrorCode.RATE_LIMITED);
        }
        return true;
    }
}
