package io.github.yuandonghao.yarch.logging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.yuandonghao.yarch.common.trace.TraceIds;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class TraceIdFilterTest {

    final TraceIdFilter filter = new TraceIdFilter();

    String capturedTraceId;

    private MockFilterChain chainCapturing() {
        HttpServlet capture =
                new HttpServlet() {
                    @Override
                    protected void service(HttpServletRequest req, HttpServletResponse res) {
                        capturedTraceId = TraceIds.currentOrNull();
                    }
                };
        return new MockFilterChain(capture);
    }

    @Test
    void parsesW3cTraceParentFirst() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/x");
        request.addHeader(
                TraceIdFilter.HEADER_TRACE_PARENT,
                "00-0af7651916cd43dd8448eb211c80319c-00f067aa0ba902b7-01");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, chainCapturing());
        assertEquals("0af7651916cd43dd8448eb211c80319c", capturedTraceId);
        assertEquals(
                "0af7651916cd43dd8448eb211c80319c",
                response.getHeader(TraceIdFilter.HEADER_X_TRACE_ID));
    }

    @Test
    void fallsBackToXTraceIdWhenTraceParentInvalid() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/");
        request.addHeader(TraceIdFilter.HEADER_TRACE_PARENT, "not-a-traceparent");
        request.addHeader(TraceIdFilter.HEADER_X_TRACE_ID, "external-trace-1234");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, chainCapturing());
        assertEquals("external-trace-1234", capturedTraceId);
    }

    @Test
    void generates32HexWhenAbsent() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, chainCapturing());
        assertTrue(TraceIds.isValid(capturedTraceId), "生成的必须是 32 位小写 hex: " + capturedTraceId);
        assertEquals(capturedTraceId, response.getHeader(TraceIdFilter.HEADER_X_TRACE_ID));
    }

    @Test
    void mdcCleanedAfterRequest() throws ServletException, IOException {
        filter.doFilter(
                new MockHttpServletRequest("GET", "/"),
                new MockHttpServletResponse(),
                new MockFilterChain());
        assertNull(TraceIds.currentOrNull(), "请求结束后 MDC 必须清理（线程复用不得串号）");
    }

    @Test
    void resolveRejectsHeaderInjection() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/");
        request.addHeader(TraceIdFilter.HEADER_X_TRACE_ID, "bad value \r\n injected");
        assertTrue(TraceIds.isValid(TraceIdFilter.resolve(request)), "非法头值必须丢弃并自行生成");
    }
}
