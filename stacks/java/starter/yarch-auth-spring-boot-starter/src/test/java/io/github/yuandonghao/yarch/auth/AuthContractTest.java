package io.github.yuandonghao.yarch.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.yuandonghao.yarch.common.web.RestResponse;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** E3 验收：Bearer 链路 2001/2002/2003 + 放行 */
@SpringBootTest(classes = AuthContractTest.App.class, properties = "yarch.auth.secret=test-secret")
@AutoConfigureMockMvc
class AuthContractTest {

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @RestController
    static class App {

        @GetMapping("/api/v1/admin-only")
        @RequireRoles({"admin"})
        public RestResponse<String> adminOnly() {
            return RestResponse.ok("welcome:" + AuthContext.currentSubject());
        }
    }

    @Autowired MockMvc mvc;

    @Autowired JwtCodec jwtCodec;

    @Test
    void authMatrix() throws Exception {
        // 无 token → 2001
        mvc.perform(get("/api/v1/admin-only"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(2001));
        // 伪造 token → 2001
        mvc.perform(get("/api/v1/admin-only").header("Authorization", "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(2001));
        // 过期 → 2002（客户端应引导重登录，禁无脑重试——契约）
        String expired = jwtCodec.issue("alice", List.of("admin"), Duration.ofMillis(-1000));
        mvc.perform(get("/api/v1/admin-only").header("Authorization", "Bearer " + expired))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(2002));
        // 角色不足 → 2003
        String user = jwtCodec.issue("bob", List.of("user"), Duration.ofMinutes(5));
        mvc.perform(get("/api/v1/admin-only").header("Authorization", "Bearer " + user))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(2003));
        // 放行
        String admin = jwtCodec.issue("alice", List.of("admin"), Duration.ofMinutes(5));
        mvc.perform(get("/api/v1/admin-only").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("welcome:alice"));
    }
}
