package io.github.ydonghao.yarch.web.security;

import io.github.ydonghao.yarch.common.code.BusinessException;
import io.github.ydonghao.yarch.common.code.GlobalErrorCode;
import io.github.ydonghao.yarch.web.idempotency.EagerBodyRequestWrapper;
import io.github.ydonghao.yarch.web.idempotency.IdempotencyFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/** E4 接口签名：HMAC 匹配 + 时间窗 ±300s + nonce 一次性消费（防重放）。失败 → 2001（未认证）。 */
public class SignatureInterceptor implements HandlerInterceptor {

    private static final long CLOCK_SKEW_MILLIS = 300_000;

    /** nonce 消费 TTL 覆盖整个签名时间窗（±300s），窗口外重放已被时间戳校验拦截 */
    private static final Duration NONCE_TTL = Duration.ofMillis(2 * CLOCK_SKEW_MILLIS);

    private final Map<String, String> appSecrets;
    private final String serviceName;
    private final NonceStore nonceStore;

    public SignatureInterceptor(
            Map<String, String> appSecrets, String serviceName, NonceStore nonceStore) {
        this.appSecrets = appSecrets;
        this.serviceName = serviceName;
        this.nonceStore = nonceStore;
    }

    @Override
    public boolean preHandle(
            HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)
                || handlerMethod.getMethodAnnotation(SignedApi.class) == null) {
            return true;
        }
        String appKey = header(request, "X-App-Key");
        String timestamp = header(request, "X-Timestamp");
        String nonce = header(request, "X-Nonce");
        String sign = header(request, "X-Sign");
        String secret = appSecrets.get(appKey);
        if (secret == null) {
            throw new BusinessException(GlobalErrorCode.UNAUTHORIZED, "unknown app key");
        }
        long ts;
        try {
            ts = Long.parseLong(timestamp);
        } catch (NumberFormatException e) {
            throw new BusinessException(GlobalErrorCode.UNAUTHORIZED, "bad timestamp");
        }
        if (Math.abs(System.currentTimeMillis() - ts) > CLOCK_SKEW_MILLIS) {
            throw new BusinessException(GlobalErrorCode.UNAUTHORIZED, "timestamp expired");
        }
        String body = bodyOf(request);
        String material =
                request.getMethod()
                        + "\n"
                        + request.getRequestURI()
                        + "\n"
                        + timestamp
                        + "\n"
                        + nonce
                        + "\n"
                        + body;
        String expected = hmacSha256(secret, material);
        if (!constantTimeEquals(expected, sign)) {
            throw new BusinessException(GlobalErrorCode.UNAUTHORIZED, "signature mismatch");
        }
        // 签名校验通过后消费 nonce（一次性）：原样重放 → 已消费 → 2001。
        // key 摘要化：appKey/nonce 均客户端可控，防止注入任意字符进 key
        String nonceKey = serviceName + ":signedapi:nonce:" + sha256Hex(appKey + ":" + nonce);
        if (!nonceStore.consume(nonceKey, NONCE_TTL)) {
            throw new BusinessException(GlobalErrorCode.UNAUTHORIZED, "nonce replayed");
        }
        return true;
    }

    private static String sha256Hex(String value) {
        try {
            byte[] bytes =
                    MessageDigest.getInstance("SHA-256")
                            .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private String bodyOf(HttpServletRequest request) {
        EagerBodyRequestWrapper eager =
                (EagerBodyRequestWrapper) request.getAttribute(IdempotencyFilter.ATTR_EAGER_BODY);
        return eager == null ? "" : new String(eager.body(), StandardCharsets.UTF_8);
    }

    private static String header(HttpServletRequest request, String name) {
        String value = request.getHeader(name);
        if (value == null || value.isBlank()) {
            throw new BusinessException(GlobalErrorCode.UNAUTHORIZED, "missing " + name);
        }
        return value.trim();
    }

    public static String hmacSha256(String secret, String material) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] bytes = mac.doFinal(material.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("HMAC unavailable", e);
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        return java.security.MessageDigest.isEqual(
                a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }
}
