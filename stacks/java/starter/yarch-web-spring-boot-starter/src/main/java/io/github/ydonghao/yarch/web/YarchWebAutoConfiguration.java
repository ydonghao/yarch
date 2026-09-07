package io.github.ydonghao.yarch.web;

import io.github.ydonghao.yarch.web.idempotency.IdempotencyFilter;
import io.github.ydonghao.yarch.web.idempotency.IdempotencyInterceptor;
import io.github.ydonghao.yarch.web.idempotency.IdempotencyStore;
import io.github.ydonghao.yarch.web.idempotency.InMemoryIdempotencyStore;
import io.github.ydonghao.yarch.web.operlog.OperationLogAspect;
import io.github.ydonghao.yarch.web.operlog.OperationLogStore;
import io.github.ydonghao.yarch.web.ratelimit.InMemoryRateLimiter;
import io.github.ydonghao.yarch.web.ratelimit.RateLimitInterceptor;
import io.github.ydonghao.yarch.web.ratelimit.RateLimiter;
import io.github.ydonghao.yarch.web.security.SignatureInterceptor;
import io.github.ydonghao.yarch.web.security.SignatureProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * web 契约件装配：全局异常兜底 + 幂等 + 限流 + 操作日志 + 接口签名。 过滤器序位：traceId（logging
 * starter，HIGHEST_PRECEDENCE）之后、其余之前。
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class YarchWebAutoConfiguration {

    /** logging starter 未在 classpath 时幂等过滤器顶到最前，语义不受影响 */
    private static final int IDEMPOTENCY_FILTER_ORDER = Ordered.HIGHEST_PRECEDENCE + 20;

    @Bean
    @ConditionalOnMissingBean
    public GlobalExceptionHandler yarchGlobalExceptionHandler() {
        return new GlobalExceptionHandler();
    }

    @Bean
    public FilterRegistrationBean<IdempotencyFilter> idempotencyFilterRegistration() {
        FilterRegistrationBean<IdempotencyFilter> registration =
                new FilterRegistrationBean<>(new IdempotencyFilter());
        registration.setOrder(IDEMPOTENCY_FILTER_ORDER);
        return registration;
    }

    @Bean
    @ConditionalOnMissingBean
    public IdempotencyStore idempotencyStore() {
        // 进程内降级实现；引入 yarch-redis starter 后自动替换为 Redis 实现（跨实例）
        return new InMemoryIdempotencyStore();
    }

    @Bean
    @ConditionalOnMissingBean(RateLimiter.class)
    public RateLimiter inMemoryRateLimiter() {
        // 同上：redis starter 到位后升级为跨实例 INCR+EXPIRE
        return new InMemoryRateLimiter();
    }

    @Bean
    @ConditionalOnMissingBean
    public OperationLogAspect operationLogAspect(ObjectProvider<OperationLogStore> store) {
        // 存储 SPI 可选：缺省仅 ndjson，业务工程实现后自动入库
        return new OperationLogAspect(store.getIfAvailable());
    }

    @Bean
    @ConfigurationProperties("yarch.security.sign")
    public SignatureProperties signatureProperties() {
        return new SignatureProperties();
    }

    @Bean
    public WebMvcConfigurer yarchInterceptorsConfigurer(
            IdempotencyStore idempotencyStore,
            RateLimiter rateLimiter,
            SignatureProperties signatureProperties,
            @Value("${spring.application.name:unknown-service}") String serviceName) {
        return new WebMvcConfigurer() {
            @Override
            public void addInterceptors(InterceptorRegistry registry) {
                registry.addInterceptor(new IdempotencyInterceptor(idempotencyStore, serviceName));
                registry.addInterceptor(new RateLimitInterceptor(rateLimiter, serviceName));
                registry.addInterceptor(new SignatureInterceptor(signatureProperties.apps()));
            }
        };
    }
}
