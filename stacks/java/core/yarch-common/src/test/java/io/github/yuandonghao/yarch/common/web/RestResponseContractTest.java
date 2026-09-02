package io.github.yuandonghao.yarch.common.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.github.yuandonghao.yarch.common.code.BusinessException;
import io.github.yuandonghao.yarch.common.code.GlobalErrorCode;
import io.github.yuandonghao.yarch.common.trace.TraceIds;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

/** 信封契约守护：字段顺序注解、失败信封 data=null、traceId 取不到为空串 */
class RestResponseContractTest {

    @Test
    void fieldOrderAnnotationMatchesContract() {
        JsonPropertyOrder order = RestResponse.class.getAnnotation(JsonPropertyOrder.class);
        assertEquals(
                Arrays.toString(new String[] {"code", "message", "data", "traceId"}),
                Arrays.toString(order.value()));
    }

    @Test
    void failEnvelopeHasNullData() {
        BusinessException ex =
                new BusinessException(GlobalErrorCode.INVALID_ARGUMENT, "pageSize 必须 ≤ 100");
        RestResponse<Object> r = RestResponse.fail(ex.getErrorCode(), ex.getMessage());
        assertEquals(1001, r.getCode());
        assertNull(r.getData(), "code != 0 时 data 必须为 null");
        assertEquals("参数校验失败：pageSize 必须 ≤ 100", r.getMessage());
    }

    @Test
    void traceIdDefaultsToEmptyStringWithoutContext() {
        TraceIds.remove();
        RestResponse<Object> r = RestResponse.ok();
        assertEquals("", r.getTraceId(), "取不到 traceId 时必须为空串而非 null");
    }

    @Test
    void traceIdEchoesMdc() {
        TraceIds.put(TraceIds.newTraceId());
        try {
            RestResponse<Object> r = RestResponse.ok();
            assertEquals(TraceIds.currentOrNull(), r.getTraceId());
        } finally {
            TraceIds.remove();
        }
    }
}
