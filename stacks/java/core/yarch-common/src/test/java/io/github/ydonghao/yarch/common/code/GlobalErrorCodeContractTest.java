package io.github.ydonghao.yarch.common.code;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

/** 错误码契约守护：本测试把 contract/api/error-codes.md v1.0 的表格以字面量再写一遍， 任何一侧漂移（契约改了实现没改，或实现被误改）都会在此失败。 */
class GlobalErrorCodeContractTest {

    /** code, identifier, defaultMessage, http —— 与 error-codes.md v1.0 逐行一致 */
    private static final List<Object[]> CONTRACT_TABLE =
            List.of(
                    new Object[] {1000, "INTERNAL_ERROR", "内部错误", 500},
                    new Object[] {1001, "INVALID_ARGUMENT", "参数校验失败", 400},
                    new Object[] {1002, "MALFORMED_BODY", "请求体格式错误", 400},
                    new Object[] {1004, "NOT_FOUND", "资源不存在", 404},
                    new Object[] {1005, "CONFLICT", "资源冲突", 409},
                    new Object[] {1006, "RATE_LIMITED", "触发限流", 429},
                    new Object[] {1007, "IDEMPOTENCY_CONFLICT", "幂等冲突：重复提交", 409},
                    new Object[] {1008, "UPSTREAM_TIMEOUT", "上游依赖超时", 504},
                    new Object[] {1009, "UNAVAILABLE", "服务暂不可用", 503},
                    new Object[] {2001, "UNAUTHORIZED", "未认证", 401},
                    new Object[] {2002, "CREDENTIALS_EXPIRED", "凭证已过期", 401},
                    new Object[] {2003, "FORBIDDEN", "权限不足", 403},
                    new Object[] {2004, "ACCOUNT_DISABLED", "账号已禁用", 403});

    @Test
    void enumMatchesContractTable() {
        assertEquals(CONTRACT_TABLE.size(), GlobalErrorCode.values().length, "码表条数与契约不一致");
        for (Object[] row : CONTRACT_TABLE) {
            int code = (int) row[0];
            GlobalErrorCode e =
                    GlobalErrorCode.ofCode(code)
                            .orElseThrow(() -> new AssertionError("契约码 " + code + " 在枚举中缺失"));
            assertEquals(row[1], e.identifier(), code + " 标识符漂移");
            assertEquals(row[2], e.defaultMessage(), code + " 默认文案漂移");
            assertEquals(row[3], e.httpStatus(), code + " HTTP 映射漂移");
        }
    }

    @Test
    void yarchOnlyOwnsReservedSegments() {
        for (GlobalErrorCode e : GlobalErrorCode.values()) {
            int c = e.code();
            boolean inYarchSegment = c == 0 || (c >= 1000 && c <= 2999);
            assert inYarchSegment : "yarch 只拥有 0/1xxx/2xxx，越界码 " + c;
        }
    }
}
