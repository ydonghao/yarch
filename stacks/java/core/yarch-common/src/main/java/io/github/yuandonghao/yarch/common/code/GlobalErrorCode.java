package io.github.yuandonghao.yarch.common.code;

import java.util.Optional;

/**
 * yarch 内置错误码全表（契约唯一权威：contract/api/error-codes.md v1.0）。 本枚举与契约表不一致即 bug——由
 * yarch-test-spring-boot-starter 的契约断言测试守护。
 */
public enum GlobalErrorCode implements ErrorCode {

    /** 未预期失败：message 固定「内部错误」，细节只进日志 */
    INTERNAL_ERROR(1000, "内部错误", 500),

    INVALID_ARGUMENT(1001, "参数校验失败", 400),
    MALFORMED_BODY(1002, "请求体格式错误", 400),
    // 1003 留空（契约保留位）
    NOT_FOUND(1004, "资源不存在", 404),
    CONFLICT(1005, "资源冲突", 409),
    RATE_LIMITED(1006, "触发限流", 429),
    IDEMPOTENCY_CONFLICT(1007, "幂等冲突：重复提交", 409),
    UPSTREAM_TIMEOUT(1008, "上游依赖超时", 504),
    /** 可由网关层代替后端发出（后端不可达/熔断/过载） */
    UNAVAILABLE(1009, "服务暂不可用", 503),

    UNAUTHORIZED(2001, "未认证", 401),
    CREDENTIALS_EXPIRED(2002, "凭证已过期", 401),
    FORBIDDEN(2003, "权限不足", 403),
    ACCOUNT_DISABLED(2004, "账号已禁用", 403);

    private final int code;
    private final String defaultMessage;
    private final int httpStatus;

    GlobalErrorCode(int code, String defaultMessage, int httpStatus) {
        this.code = code;
        this.defaultMessage = defaultMessage;
        this.httpStatus = httpStatus;
    }

    @Override
    public int code() {
        return code;
    }

    @Override
    public String identifier() {
        return name();
    }

    @Override
    public String defaultMessage() {
        return defaultMessage;
    }

    @Override
    public int httpStatus() {
        return httpStatus;
    }

    public static Optional<GlobalErrorCode> ofCode(int code) {
        for (GlobalErrorCode e : values()) {
            if (e.code == code) {
                return Optional.of(e);
            }
        }
        return Optional.empty();
    }
}
