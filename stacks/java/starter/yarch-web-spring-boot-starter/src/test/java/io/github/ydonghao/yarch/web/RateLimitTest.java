package io.github.ydonghao.yarch.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/** G1：限流——固定窗口超限 → 429 + 1006（契约码位落地） */
@SpringBootTest(classes = TestApp.class)
@AutoConfigureMockMvc
class RateLimitTest {

    @Autowired MockMvc mvc;

    @Test
    void burstBeyondPermitsIs429With1006() throws Exception {
        for (int i = 0; i < 3; i++) {
            mvc.perform(post("/api/v1/burst"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0));
        }
        mvc.perform(post("/api/v1/burst"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value(1006));
    }
}
