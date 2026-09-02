package io.github.yuandonghao.yarch.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.yuandonghao.yarch.common.code.ErrorCode;
import io.github.yuandonghao.yarch.common.code.GlobalErrorCode;
import io.github.yuandonghao.yarch.common.web.PageData;
import io.github.yuandonghao.yarch.common.web.RestResponse;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** 契约断言工具：业务工程测试里直接调用，把「实现与契约一致」从 CR 自觉变成 assert。 对应 J7 机检三件套之「契约断言」；错误码表在此以字面量二次编码，任何一侧漂移都会失败。 */
public final class ContractAsserts {

    private ContractAsserts() {}

    /** contract/api/error-codes.md v1.0 全表：code → identifier|message|http */
    private static final Map<Integer, String> CONTRACT_TABLE =
            Map.ofEntries(
                    Map.entry(1000, "INTERNAL_ERROR|内部错误|500"),
                    Map.entry(1001, "INVALID_ARGUMENT|参数校验失败|400"),
                    Map.entry(1002, "MALFORMED_BODY|请求体格式错误|400"),
                    Map.entry(1004, "NOT_FOUND|资源不存在|404"),
                    Map.entry(1005, "CONFLICT|资源冲突|409"),
                    Map.entry(1006, "RATE_LIMITED|触发限流|429"),
                    Map.entry(1007, "IDEMPOTENCY_CONFLICT|幂等冲突：重复提交|409"),
                    Map.entry(1008, "UPSTREAM_TIMEOUT|上游依赖超时|504"),
                    Map.entry(1009, "UNAVAILABLE|服务暂不可用|503"),
                    Map.entry(2001, "UNAUTHORIZED|未认证|401"),
                    Map.entry(2002, "CREDENTIALS_EXPIRED|凭证已过期|401"),
                    Map.entry(2003, "FORBIDDEN|权限不足|403"),
                    Map.entry(2004, "ACCOUNT_DISABLED|账号已禁用|403"));

    /** 全表核对 GlobalErrorCode 与契约字面量（13 码 + HTTP 映射 + 默认文案） */
    public static void assertErrorCodeTable() {
        assertEquals(
                CONTRACT_TABLE.size(),
                GlobalErrorCode.values().length,
                () -> "yarch 错误码表条数与契约不一致（契约 " + CONTRACT_TABLE.size() + " 条）");
        for (GlobalErrorCode e : GlobalErrorCode.values()) {
            String expected = CONTRACT_TABLE.get(e.code());
            assertNotNull(expected, "契约外多出的码: " + e.code());
            String[] parts = expected.split("\\|");
            assertEquals(parts[0], e.identifier(), e.code() + " 标识符漂移");
            assertEquals(parts[1], e.defaultMessage(), e.code() + " 默认文案漂移");
            assertEquals(Integer.parseInt(parts[2]), e.httpStatus(), e.code() + " HTTP 映射漂移");
        }
    }

    /** 成功信封形状：code=0、message/traceId 非 null */
    public static void assertSuccessEnvelope(RestResponse<?> response) {
        assertNotNull(response);
        assertEquals(0, response.getCode());
        assertNotNull(response.getMessage());
        assertNotNull(response.getTraceId());
        assertTrue(response.isSuccess());
    }

    /** 失败信封形状：code 匹配、data 必须为 null（契约铁律） */
    public static void assertErrorEnvelope(RestResponse<?> response, ErrorCode expected) {
        assertNotNull(response);
        assertEquals(expected.code(), response.getCode());
        if (response.getCode() != 0) {
            if (response.getData() != null) {
                throw new AssertionError("code != 0 时 data 必须为 null: " + response.getData());
            }
        }
        assertTrue(
                response.getMessage().startsWith(expected.defaultMessage()),
                "message 默认文案不得改写，只允许追加细节: " + response.getMessage());
    }

    /** 分页负载形状：list 非 null、page/pageSize 为正、total ≥ 0 */
    public static void assertPageDataShape(PageData<?> pageData) {
        assertNotNull(pageData);
        assertNotNull(pageData.getList(), "list 可为空数组，不得为 null");
        assertTrue(pageData.getPage() >= 1);
        assertTrue(pageData.getPageSize() >= 1);
        assertTrue(pageData.getTotal() >= 0);
    }

    /** D4：时间一律 ISO-8601 UTC（RFC3339，恒 Z 结尾） */
    public static void assertIso8601Utc(String value) {
        assertNotNull(value);
        assertTrue(
                value.matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?Z"),
                "非 ISO-8601 UTC: " + value);
    }

    /** traceId 形状：32 位小写 hex（logging-trace.md） */
    public static void assertTraceIdFormat(String traceId) {
        assertNotNull(traceId);
        assertTrue(traceId.matches("^[0-9a-f]{32}$"), "非 32 位小写 hex: " + traceId);
    }

    /** 业务工程自检辅助：3xxx+ 业务码必须在业务仓登记（登记处文件由业务仓维护） */
    public static void assertBusinessCodesRegistered(
            List<? extends ErrorCode> codes, List<Integer> registered) {
        List<Integer> unregistered =
                codes.stream()
                        .map(ErrorCode::code)
                        .filter(c -> c >= 3000 && c <= 8999)
                        .filter(c -> !registered.contains(c))
                        .collect(Collectors.toList());
        assertTrue(unregistered.isEmpty(), () -> "未登记的业务码（须在业务仓 docs 登记）: " + unregistered);
    }
}
