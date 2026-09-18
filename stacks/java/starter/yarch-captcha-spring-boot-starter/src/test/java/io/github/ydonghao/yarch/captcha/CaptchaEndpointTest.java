package io.github.ydonghao.yarch.captcha;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.ydonghao.yarch.test.containers.RedisTestDb;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 六-1 端点与 V11 限流：GET /api/v1/captcha 字段恒在（provider 增量 + key/imageBase64 存量形态）； 限流 1006（@RateLimited
 * 60s/10 次【参考】档）。单方法串行自控计数顺序（窗口内共享）。
 */
@SpringBootTest(
        classes = {CaptchaEndpointTest.App.class, CaptchaEndpointTest.Fakes.class},
        properties = "yarch.captcha.scenes.login=sms-otp")
@AutoConfigureMockMvc
class CaptchaEndpointTest {

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class App {}

    @TestConfiguration
    static class Fakes {
        @Bean
        SmsSender noopSmsSender() {
            return (destination, content) -> {};
        }
    }

    static final RedisTestDb redis = RedisTestDb.dockerAvailable() ? RedisTestDb.start() : null;

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        if (redis != null) {
            redis.register(registry);
        }
    }

    @BeforeAll
    static void requireDocker() {
        assumeTrue(redis != null, "本机无 Docker，跳过验证码端点测试");
    }

    @Autowired MockMvc mockMvc;

    @Test
    void endpointFieldsAndRateLimit() throws Exception {
        // 1) 非 image 档场景经图形端点 = 1001（六-1），消耗 1 个限流名额
        mockMvc.perform(get("/api/v1/captcha").param("scene", "login"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1001));

        // 2) 默认档：字段恒在（provider 增量；key/imageBase64 存量形态不动）
        mockMvc.perform(get("/api/v1/captcha"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.provider").value("image"))
                .andExpect(jsonPath("$.data.key").isNotEmpty())
                .andExpect(jsonPath("$.data.imageBase64").isNotEmpty());

        // 3) V11：60s 窗口 10 次名额耗尽后报 1006（五-1）——已消耗 2 次，再打 8 次满额
        for (int i = 0; i < 8; i++) {
            mockMvc.perform(get("/api/v1/captcha")).andExpect(status().isOk());
        }
        MvcResult limited = mockMvc.perform(get("/api/v1/captcha")).andReturn();
        int status = limited.getResponse().getStatus();
        String body = limited.getResponse().getContentAsString();
        assertTrue(
                status == 429 || body.contains("\"code\":1006"),
                "V11：超限应报 1006（429），实际 " + status + " " + body);
    }
}
