package io.github.yuandonghao.yarch.common.code;

/**
 * yarch 错误码契约（contract/api/error-codes.md v1.0）：int32 数字段位 + 标识符 + 默认文案 + 固定 HTTP 映射。 yarch 只拥有 0 /
 * 1xxx / 2xxx；3xxx-8xxx 由业务工程自行注册（实现本接口并在业务仓 docs 登记）。
 */
public interface ErrorCode {

    /** 业务码：0 成功；非 0 见段位表 */
    int code();

    /** 标识符（Java 方言固定为 UPPER_SNAKE_CASE，如 INTERNAL_ERROR） */
    String identifier();

    /** 默认文案：允许在其后追加冒号细节，默认文案部分不得改写 */
    String defaultMessage();

    /** HTTP 状态码映射：同一 code 全栈一致；白名单 200/201/400/401/403/404/409/429/500/503/504 */
    int httpStatus();
}
