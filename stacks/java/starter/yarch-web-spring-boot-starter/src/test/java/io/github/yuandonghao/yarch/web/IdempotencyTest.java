package io.github.yuandonghao.yarch.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** 幂等总则-1 的服务端语义：同键同参回放；同键异参 1007；无键直通 */
@SpringBootTest(classes = TestApp.class)
@AutoConfigureMockMvc
class IdempotencyTest {

    @Autowired MockMvc mvc;

    @Test
    void sameKeySameBodyReplaysFirstResponse() throws Exception {
        MvcResult first =
                mvc.perform(
                                post("/api/v1/orders")
                                        .header("Idempotency-Key", "key-1")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{\"name\":\"o1\"}"))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.data.id").isNumber())
                        .andReturn();

        // 第二次同键同参：回放原响应（id 不变、不重新执行），而非重新分配
        MvcResult second =
                mvc.perform(
                                post("/api/v1/orders")
                                        .header("Idempotency-Key", "key-1")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{\"name\":\"o1\"}"))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.code").value(0))
                        .andReturn();

        assertEquals(
                stripTraceId(first.getResponse().getContentAsString()),
                stripTraceId(second.getResponse().getContentAsString()));
    }

    @Test
    void sameKeyDifferentBodyIs1007() throws Exception {
        mvc.perform(
                        post("/api/v1/orders")
                                .header("Idempotency-Key", "key-2")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"a\"}"))
                .andExpect(status().isCreated());

        mvc.perform(
                        post("/api/v1/orders")
                                .header("Idempotency-Key", "key-2")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"DIFFERENT\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(1007));
    }

    @Test
    void missingKeyExecutesNormally() throws Exception {
        mvc.perform(
                        post("/api/v1/orders")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"x\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(0));
        mvc.perform(
                        post("/api/v1/orders")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"x\"}"))
                .andExpect(status().isCreated());
    }

    /** 信封外比较：去掉 traceId 后比对（两次请求 traceId 必然不同） */
    private static String stripTraceId(String body) {
        return body.replaceAll("\"traceId\":\"[^\"]*\"", "\"traceId\":\"-\"}");
    }
}
