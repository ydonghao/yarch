package io.github.ydonghao.yarch.common.code;

/**
 * 异常断言（对标 COLA ExceptionAssert）：失败即抛 BusinessException， 让前置校验从 if-throw 样板收敛为声明式一行（BCDE 的 Border
 * 用例友好）。
 */
public final class Asserts {

    private Asserts() {}

    public static void notNull(Object value, ErrorCode errorCode, String detail) {
        if (value == null) {
            throw new BusinessException(errorCode, detail);
        }
    }

    public static void notBlank(String value, ErrorCode errorCode, String detail) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(errorCode, detail);
        }
    }

    public static void isTrue(boolean condition, ErrorCode errorCode, String detail) {
        if (!condition) {
            throw new BusinessException(errorCode, detail);
        }
    }

    public static void isFalse(boolean condition, ErrorCode errorCode, String detail) {
        isTrue(!condition, errorCode, detail);
    }
}
