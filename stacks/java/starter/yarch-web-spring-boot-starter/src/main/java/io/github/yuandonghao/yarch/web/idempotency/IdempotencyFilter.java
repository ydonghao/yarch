package io.github.yuandonghao.yarch.web.idempotency;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

/** 幂等过滤器：仅当请求头带 Idempotency-Key 时生效—— 包装请求体缓存（摘要判据）与响应体缓存（执行后存储），其余请求零开销直通。 */
public class IdempotencyFilter extends OncePerRequestFilter {

    public static final String HEADER = "Idempotency-Key";

    public static final String ATTR_EAGER_BODY = "yarch.idem.eagerBody";
    static final String ATTR_OWNED_KEY = "yarch.idem.ownedKey";

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // 幂等（Idempotency-Key）与接口签名（X-App-Key）都需要请求体缓存
        return request.getHeader(HEADER) == null && request.getHeader("X-App-Key") == null;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        EagerBodyRequestWrapper wrapped;
        try {
            wrapped = new EagerBodyRequestWrapper(request);
        } catch (IOException e) {
            // 超出缓存上限等场景：不拦截请求，放弃幂等语义直通
            chain.doFilter(request, response);
            return;
        }
        request.setAttribute(ATTR_EAGER_BODY, wrapped);
        ContentCachingResponseWrapper responseWrapper = new ContentCachingResponseWrapper(response);
        try {
            chain.doFilter(wrapped, responseWrapper);
        } finally {
            responseWrapper.copyBodyToResponse();
        }
    }
}
