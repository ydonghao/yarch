package io.github.ydonghao.yarch.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.ydonghao.yarch.web.operlog.OperationLogRecord;
import io.github.ydonghao.yarch.web.operlog.OperationLogStore;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.web.servlet.MockMvc;

/** G2：操作日志——切面记录动作/耗时/成败，存储 SPI 收到记录 */
@SpringBootTest(classes = {TestApp.class, OperationLogTest.SpyStoreConfig.class})
@AutoConfigureMockMvc
class OperationLogTest {

    static final List<OperationLogRecord> RECORDED = new CopyOnWriteArrayList<>();

    @Configuration
    static class SpyStoreConfig {

        @Bean
        OperationLogStore spyStore() {
            return RECORDED::add;
        }
    }

    @Autowired MockMvc mvc;

    @Test
    void recordsSuccessAndFailure() throws Exception {
        RECORDED.clear();
        mvc.perform(post("/api/v1/audit")).andExpect(status().isOk());
        mvc.perform(post("/api/v1/audit?fail=true")).andExpect(status().isInternalServerError());

        assertEquals(2, RECORDED.size());
        assertEquals("test.audit", RECORDED.get(0).action());
        assertTrue(RECORDED.get(0).success());
        assertTrue(RECORDED.get(0).costMs() >= 0);
        assertTrue(!RECORDED.get(1).success());
        assertTrue(RECORDED.get(1).error().startsWith("IllegalStateException"));
    }
}
