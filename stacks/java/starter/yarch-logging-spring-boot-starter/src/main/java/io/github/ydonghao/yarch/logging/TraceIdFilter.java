package io.github.ydonghao.yarch.logging;

import io.github.ydonghao.yarch.common.trace.TraceIds;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * traceId 入口（契约 logging-trace.md v1.0「traceId 贯穿」）： 优先解析 W3C
 * traceparent（00-{traceId}-{spanId}-{flag}，取 trace-id 段）；无则看 X-Trace-Id； 再无则生成 32 位小写 hex。出口：响应头
 * X-Trace-Id 恒回显；处理期间值进 MDC。 铁律：traceId 只在链路入口无值时生成一次，之后不改变。
 */
public class TraceIdFilter extends OncePerRequestFilter {

    public static final String HEADER_TRACE_PARENT = "traceparent";
    public static final String HEADER_X_TRACE_ID = "X-Trace-Id";

    /** W3C traceparent：version-traceid(32hex)-spanid(16hex)-flags */
    private static final Pattern TRACE_PARENT =
            Pattern.compile("^[0-9a-f]{2}-([0-9a-f]{32})-[0-9a-f]{16}-[0-9a-f]{2}$");

    /** 外部 X-Trace-Id 宽容口径：可打印短横线字母数字，8~128，防注入头值 */
    private static final Pattern X_TRACE_ID = Pattern.compile("^[A-Za-z0-9-]{8,128}$");

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String traceId = resolve(request);
        TraceIds.put(traceId);
        try {
            response.setHeader(HEADER_X_TRACE_ID, traceId);
            chain.doFilter(request, response);
        } finally {
            TraceIds.remove();
        }
    }

    static String resolve(HttpServletRequest request) {
        String traceParent = request.getHeader(HEADER_TRACE_PARENT);
        if (traceParent != null) {
            Matcher matcher = TRACE_PARENT.matcher(traceParent.trim());
            if (matcher.matches()) {
                return matcher.group(1);
            }
        }
        String xTraceId = request.getHeader(HEADER_X_TRACE_ID);
        if (xTraceId != null && X_TRACE_ID.matcher(xTraceId.trim()).matches()) {
            return xTraceId.trim();
        }
        return TraceIds.newTraceId();
    }
}
