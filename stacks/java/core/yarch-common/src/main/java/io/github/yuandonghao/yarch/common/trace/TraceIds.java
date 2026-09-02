package io.github.yuandonghao.yarch.common.trace;

import java.security.SecureRandom;
import org.slf4j.MDC;

/**
 * traceId 契约（logging-trace.md v1.0）：128-bit 随机数的 32 位小写 hex； 全链路不改变、不重新生成（除入口为空时）；请求上下文内必有。 Java
 * 方言的上下文载体是 MDC key {@code traceId}（由 yarch-logging starter 的 TraceIdFilter 注入）。
 */
public final class TraceIds {

    public static final String MDC_KEY = "traceId";
    static final String TRACE_ID_PATTERN = "^[0-9a-f]{32}$";

    private static final SecureRandom RANDOM = new SecureRandom();

    private TraceIds() {}

    /** 入口无值时生成一次：128-bit 随机数的 32 位小写 hex */
    public static String newTraceId() {
        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        StringBuilder sb = new StringBuilder(32);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    public static boolean isValid(String traceId) {
        return traceId != null && traceId.matches(TRACE_ID_PATTERN);
    }

    /** 当前上下文 traceId；无上下文返回 null */
    public static String currentOrNull() {
        return MDC.get(MDC_KEY);
    }

    /** 当前上下文 traceId；无上下文返回空串（信封字段口径：取不到为空串） */
    public static String currentOrEmpty() {
        String v = MDC.get(MDC_KEY);
        return v == null ? "" : v;
    }

    public static void put(String traceId) {
        MDC.put(MDC_KEY, traceId);
    }

    public static void remove() {
        MDC.remove(MDC_KEY);
    }
}
