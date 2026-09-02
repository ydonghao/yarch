package io.github.yuandonghao.yarch.common.code;

import io.github.yuandonghao.yarch.common.trace.TraceIds;

/**
 * 业务可预期异常：业务工程与 yarch starter 抛出的唯一业务异常形态。 message 规则（契约）：默认文案 + "：" + 细节（如「参数校验失败：pageSize 必须 ≤
 * 100」），默认文案部分不得改写。
 */
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.defaultMessage());
        this.errorCode = errorCode;
    }

    public BusinessException(ErrorCode errorCode, String detail) {
        super(errorCode.defaultMessage() + "：" + detail);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    /** 组装 RestResponse 失败信封：code != 0 时 data 必须为 null（契约） */
    public Object[] envelope() {
        return new Object[] {errorCode.code(), getMessage()};
    }

    /** 快捷抛出：带当前 traceId 的场景由 web 层统一封装，这里仅提供异常本体 */
    public static BusinessException of(ErrorCode errorCode) {
        return new BusinessException(errorCode);
    }

    public static BusinessException of(ErrorCode errorCode, String detail) {
        return new BusinessException(errorCode, detail);
    }

    /** 排障提示：报障凭证即 traceId（契约 logging-trace.md） */
    public String traceIdForSupport() {
        return TraceIds.currentOrEmpty();
    }
}
