package ${package}.types.errno;

import io.github.ydonghao.yarch.common.code.ErrorCode;

/** 业务错误码（3xxx-8xxx 段，业务仓自治——error-codes.md 段位分配）。 登记：在本仓 docs/errno.md 登记后方可使用（CI 阶段未登记即违规）。 */
public enum UserErrorCode implements ErrorCode {
    EMAIL_EXISTS(3001, "邮箱已存在", 409);

    private final int code;
    private final String defaultMessage;
    private final int httpStatus;

    UserErrorCode(int code, String defaultMessage, int httpStatus) {
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
}
