package io.github.ydonghao.yarch.common.web;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.github.ydonghao.yarch.common.code.ErrorCode;
import io.github.ydonghao.yarch.common.trace.TraceIds;

/**
 * 统一响应信封（契约唯一权威：contract/api/rest-response.md v1.0）。 四字段 camelCase，任何栈不得增删改名；字段顺序建议
 * code,message,data,traceId（利于人读 diff）。
 */
@JsonPropertyOrder({"code", "message", "data", "traceId"})
public class RestResponse<T> {

    public static final int CODE_SUCCESS = 0;
    public static final String MESSAGE_SUCCESS = "成功";

    private int code;
    private String message;
    private T data;
    private String traceId;

    public static <T> RestResponse<T> ok() {
        return build(CODE_SUCCESS, MESSAGE_SUCCESS, null);
    }

    public static <T> RestResponse<T> ok(T data) {
        return build(CODE_SUCCESS, MESSAGE_SUCCESS, data);
    }

    public static <T> RestResponse<T> fail(ErrorCode errorCode) {
        return fail(errorCode, null);
    }

    /**
     * 失败信封：code != 0 时 data 必须为 null（契约）；message 由 BusinessException 的拼装规则保证
     * 「默认文案：细节」，这里只接收已拼好的完整文案。
     */
    public static <T> RestResponse<T> fail(ErrorCode errorCode, String fullMessage) {
        return build(
                errorCode.code(),
                fullMessage == null ? errorCode.defaultMessage() : fullMessage,
                null);
    }

    private static <T> RestResponse<T> build(int code, String message, T data) {
        RestResponse<T> r = new RestResponse<>();
        r.code = code;
        r.message = message;
        r.data = data;
        r.traceId = TraceIds.currentOrEmpty();
        return r;
    }

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }

    /** 恒等于响应头 X-Trace-Id；取不到时为空串（契约） */
    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public boolean isSuccess() {
        return code == CODE_SUCCESS;
    }
}
