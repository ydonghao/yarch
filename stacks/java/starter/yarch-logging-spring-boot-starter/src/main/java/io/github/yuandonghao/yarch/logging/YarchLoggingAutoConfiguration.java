package io.github.yuandonghao.yarch.logging;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;

/** 日志契约件装配：traceId 过滤器序列最前（web starter 的幂等过滤器排在 +20）， 请求完成日志紧随其后。 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class YarchLoggingAutoConfiguration {

    @Bean
    public FilterRegistrationBean<TraceIdFilter> yarchTraceIdFilter() {
        FilterRegistrationBean<TraceIdFilter> registration =
                new FilterRegistrationBean<>(new TraceIdFilter());
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }

    @Bean
    public FilterRegistrationBean<RequestCompletedFilter> yarchRequestCompletedFilter() {
        FilterRegistrationBean<RequestCompletedFilter> registration =
                new FilterRegistrationBean<>(new RequestCompletedFilter());
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 1);
        return registration;
    }
}
