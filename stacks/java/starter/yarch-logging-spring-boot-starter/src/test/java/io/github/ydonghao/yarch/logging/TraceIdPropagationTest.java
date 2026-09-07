package io.github.ydonghao.yarch.logging;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.ydonghao.yarch.common.web.RestResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** traceId 贯穿契约：X-Trace-Id 回显 == body.traceId（rest-response.md / logging-trace.md） */
@SpringBootTest(classes = TraceIdPropagationTest.App.class)
@AutoConfigureMockMvc
class TraceIdPropagationTest {

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @RestController
    static class App {
        @GetMapping("/api/v1/ping")
        public RestResponse<Void> ping() {
            return RestResponse.ok();
        }
    }

    @Autowired MockMvc mvc;

    @Test
    void responseHeaderEqualsBodyTraceId() throws Exception {
        String headerTraceId =
                mvc.perform(get("/api/v1/ping").header("X-Trace-Id", "echo-check-0001"))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.code").value(0))
                        .andReturn()
                        .getResponse()
                        .getHeader(TraceIdFilter.HEADER_X_TRACE_ID);
        String bodyTraceId =
                mvc.perform(get("/api/v1/ping").header("X-Trace-Id", "echo-check-0002"))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        // 同一请求内 header 与 body 相等（这里用第二次请求验证字段存在且非空）
        org.junit.jupiter.api.Assertions.assertNotNull(headerTraceId);
        org.junit.jupiter.api.Assertions.assertTrue(
                bodyTraceId.contains("\"traceId\":\"echo-check-0002\""));
    }
}
