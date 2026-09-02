package io.github.yuandonghao.yarch.examples.ddd.types.errno;

import io.github.yuandonghao.yarch.common.code.ErrorCode;

/** 案例业务码（3xxx 段演示；真实工程在本仓 docs/errno.md 登记后方可使用） */
public enum ExampleErrorCode implements ErrorCode {
    PRODUCT_NOT_FOUND(3001, "商品不存在", 404),
    STOCK_INSUFFICIENT(3002, "库存不足", 409),
    ORDER_NOT_FOUND(3003, "订单不存在", 404),
    INVALID_STATE(3004, "非法状态迁移", 409);

    private final int code;
    private final String defaultMessage;
    private final int httpStatus;

    ExampleErrorCode(int code, String defaultMessage, int httpStatus) {
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
