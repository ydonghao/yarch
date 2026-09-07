package io.github.ydonghao.yarch.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import net.logstash.logback.argument.StructuredArguments;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 请求完成日志（契约 logging-trace.md 行协议示例的「request completed」行）： msg 固定，method/path/status/costMs
 * 为自由键值对（camelCase），traceId 由 MDC 带出。
 */
public class RequestCompletedFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RequestCompletedFilter.class);

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long start = System.nanoTime();
        try {
            chain.doFilter(request, response);
        } finally {
            long costMs = (System.nanoTime() - start) / 1_000_000;
            // 注意：kv 参数不占位 msg——logstash StructuredArguments 作为结构化字段附加，消息保持纯净
            log.info(
                    "request completed",
                    StructuredArguments.kv("method", request.getMethod()),
                    StructuredArguments.kv("path", request.getRequestURI()),
                    StructuredArguments.kv("status", response.getStatus()),
                    StructuredArguments.kv("costMs", costMs));
        }
    }
}
