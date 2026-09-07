package io.github.ydonghao.yarch.web.idempotency;

import io.github.ydonghao.yarch.common.code.BusinessException;
import io.github.ydonghao.yarch.common.code.GlobalErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Optional;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.util.ContentCachingResponseWrapper;

/** 幂等拦截器（幂等总则-1 的服务端语义）： 同键同参回放原响应；同键异参 1007；并发同键短暂等待后回放或 1007； 执行失败（异常或 5xx）释放占位允许重试。 */
public class IdempotencyInterceptor implements HandlerInterceptor {

    static final String HEADER_REPLAYED = "Idempotency-Replayed";

    private static final long POLL_WAIT_MILLIS = 200;
    private static final int POLL_ROUNDS = 10;

    private final IdempotencyStore store;
    private final String serviceName;

    public IdempotencyInterceptor(IdempotencyStore store, String serviceName) {
        this.store = store;
        this.serviceName = serviceName;
    }

    @Override
    public boolean preHandle(
            HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }
        Idempotent idempotent = handlerMethod.getMethodAnnotation(Idempotent.class);
        if (idempotent == null) {
            return true;
        }
        String key = request.getHeader(IdempotencyFilter.HEADER);
        if (key == null || key.isBlank()) {
            return true; // 契约：可带
        }
        // redis.md 二-1：key 首段必须是服务名（租户边界）
        String fullKey = serviceName + ":idem:" + idempotent.resource() + ":" + key.trim();
        String requestHash = hash(request);

        Optional<IdempotencyStore.StoredResponse> stored = store.find(fullKey);
        if (stored.isPresent()) {
            return resolveExisting(response, fullKey, requestHash, stored.get());
        }
        if (store.reserve(fullKey, requestHash, Duration.ofSeconds(idempotent.ttlSeconds()))) {
            request.setAttribute(IdempotencyFilter.ATTR_OWNED_KEY, fullKey);
            return true; // 本请求负责执行
        }
        // 并发同键：短暂等待先到者写回，仍无则 1007（契约口径）
        for (int i = 0; i < POLL_ROUNDS; i++) {
            Thread.sleep(POLL_WAIT_MILLIS);
            Optional<IdempotencyStore.StoredResponse> again = store.find(fullKey);
            if (again.isPresent()) {
                return resolveExisting(response, fullKey, requestHash, again.get());
            }
        }
        throw new BusinessException(GlobalErrorCode.IDEMPOTENCY_CONFLICT, "并发重复提交");
    }

    @Override
    public void afterCompletion(
            HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex)
            throws IOException {
        String ownedKey = (String) request.getAttribute(IdempotencyFilter.ATTR_OWNED_KEY);
        if (ownedKey == null) {
            return;
        }
        Idempotent idempotent = ((HandlerMethod) handler).getMethodAnnotation(Idempotent.class);
        Duration ttl = Duration.ofSeconds(idempotent.ttlSeconds());
        if (ex != null || response.getStatus() >= 500) {
            store.discard(ownedKey); // 5xx/异常不存储，允许重试
            return;
        }
        if (response instanceof ContentCachingResponseWrapper cached) {
            store.save(
                    ownedKey,
                    hash(request),
                    cached.getStatus(),
                    cached.getContentType(),
                    new String(cached.getContentAsByteArray(), StandardCharsets.UTF_8),
                    ttl);
        }
    }

    private boolean resolveExisting(
            HttpServletResponse response,
            String fullKey,
            String requestHash,
            IdempotencyStore.StoredResponse stored)
            throws IOException {
        if (!stored.requestHash().equals(requestHash)) {
            throw new BusinessException(GlobalErrorCode.IDEMPOTENCY_CONFLICT, "同键异参");
        }
        if (stored.pending()) {
            throw new BusinessException(GlobalErrorCode.IDEMPOTENCY_CONFLICT, "重复提交：处理中");
        }
        response.setStatus(stored.status());
        if (stored.contentType() != null) {
            response.setContentType(stored.contentType());
        }
        response.setHeader(HEADER_REPLAYED, "true");
        response.getWriter().write(stored.body());
        response.getWriter().flush();
        return false;
    }

    private String hash(HttpServletRequest request) {
        EagerBodyRequestWrapper eager =
                (EagerBodyRequestWrapper) request.getAttribute(IdempotencyFilter.ATTR_EAGER_BODY);
        String material =
                request.getMethod()
                        + "\n"
                        + request.getRequestURI()
                        + "\n"
                        + String.valueOf(request.getQueryString())
                        + "\n"
                        + (eager == null ? "" : new String(eager.body(), StandardCharsets.UTF_8));
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of()
                    .formatHex(digest.digest(material.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
