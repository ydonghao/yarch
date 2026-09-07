package io.github.ydonghao.yarch.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.ydonghao.yarch.web.security.SignatureInterceptor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** E4：接口签名——HMAC 匹配放行，错签/过期时间戳 → 2001（未认证） */
@SpringBootTest(classes = TestApp.class, properties = "yarch.security.sign.apps.test-app=secret123")
@AutoConfigureMockMvc
class SignatureTest {

    @Autowired MockMvc mvc;

    private org.springframework.test.web.servlet.RequestBuilder signed(
            String sign, long timestamp, String nonce) {
        return post("/api/v1/secure-data")
                .header("X-App-Key", "test-app")
                .header("X-Timestamp", timestamp)
                .header("X-Nonce", nonce)
                .header("X-Sign", sign)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"keyboard\"}");
    }

    @Test
    void validSignaturePasses() throws Exception {
        long ts = System.currentTimeMillis();
        String nonce = "n-" + ts;
        String sign =
                SignatureInterceptor.hmacSha256(
                        "secret123",
                        "POST\n/api/v1/secure-data\n"
                                + ts
                                + "\n"
                                + nonce
                                + "\n"
                                + "{\"name\":\"keyboard\"}");
        mvc.perform(signed(sign, ts, nonce))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("keyboard"));
    }

    @Test
    void badSignatureIs2001() throws Exception {
        long ts = System.currentTimeMillis();
        mvc.perform(signed("deadbeef", ts, "n2"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(2001));
    }

    @Test
    void staleTimestampIs2001() throws Exception {
        long stale = System.currentTimeMillis() - 600_000;
        String nonce = "n3";
        String sign =
                SignatureInterceptor.hmacSha256(
                        "secret123",
                        "POST\n/api/v1/secure-data\n"
                                + stale
                                + "\n"
                                + nonce
                                + "\n"
                                + "{\"name\":\"keyboard\"}");
        mvc.perform(signed(sign, stale, nonce))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(2001));
    }
}
