package io.github.ydonghao.yarch.examples.ddd;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.jayway.jsonpath.JsonPath;
import io.github.ydonghao.yarch.auth.JwtCodec;
import io.github.ydonghao.yarch.test.containers.PgTestDb;
import io.github.ydonghao.yarch.test.containers.RedisTestDb;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** 第二批平台特性在案例里的端到端验收：状态机(E5)/限流(G1)/鉴权(E3)/验证码(E2) */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "yarch.auth.secret=examples-demo-secret-please-rotate")
class PlatformFeaturesTest {

    static final PgTestDb pg = PgTestDb.dockerAvailable() ? PgTestDb.start() : null;
    static final RedisTestDb redis =
            (pg != null && RedisTestDb.dockerAvailable()) ? RedisTestDb.start() : null;

    @DynamicPropertySource
    static void infra(DynamicPropertyRegistry registry) {
        if (pg != null) {
            pg.register(registry);
        }
        if (redis != null) {
            redis.register(registry);
        }
    }

    @BeforeAll
    static void requireDocker() {
        assumeTrue(pg != null && redis != null, "本机无 Docker，跳过");
    }

    @org.springframework.beans.factory.annotation.Value("${local.server.port}")
    int port;

    TestRestTemplate rest = new TestRestTemplate();

    @Autowired JwtCodec jwtCodec;

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private long createOrder() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        long productId =
                ((Number)
                                JsonPath.read(
                                        rest.exchange(
                                                        url("/api/v1/products"),
                                                        HttpMethod.POST,
                                                        new HttpEntity<>(
                                                                "{\"name\":\"s\",\"priceCents\":100,\"stock\":99}",
                                                                headers),
                                                        String.class)
                                                .getBody(),
                                        "$.data.id"))
                        .longValue();
        String body = "{\"productId\":" + productId + ",\"buyerEmail\":\"a@b.c\",\"quantity\":1}";
        return ((Number)
                        JsonPath.read(
                                rest.exchange(
                                                url("/api/v1/orders"),
                                                HttpMethod.POST,
                                                new HttpEntity<>(body, headers),
                                                String.class)
                                        .getBody(),
                                "$.data.id"))
                .longValue();
    }

    @Test
    void orderStateMachineAllowsPendingTransitionsAndRejectsIllegal() {
        long orderId = createOrder();
        // pending → paid 合法
        ResponseEntity<String> paid =
                rest.postForEntity(
                        url("/api/v1/orders/" + orderId + "/pay"), HttpEntity.EMPTY, String.class);
        assertEquals(200, paid.getStatusCode().value());
        assertEquals("paid", JsonPath.read(paid.getBody(), "$.data.status"));
        // paid → cancel 非法（paid 无出边）
        ResponseEntity<String> illegal =
                rest.postForEntity(
                        url("/api/v1/orders/" + orderId + "/cancel"),
                        HttpEntity.EMPTY,
                        String.class);
        assertEquals(409, illegal.getStatusCode().value());
        assertEquals(3004, (int) JsonPath.read(illegal.getBody(), "$.code"));
    }

    @Test
    void pendingOrderCanBeCancelled() {
        long orderId = createOrder();
        ResponseEntity<String> cancelled =
                rest.postForEntity(
                        url("/api/v1/orders/" + orderId + "/cancel"),
                        HttpEntity.EMPTY,
                        String.class);
        assertEquals(200, cancelled.getStatusCode().value());
        assertEquals("cancelled", JsonPath.read(cancelled.getBody(), "$.data.status"));
    }

    @Test
    void rateLimitReturns429BeyondPermits() {
        for (int i = 0; i < 5; i++) {
            assertEquals(
                    200,
                    rest.postForEntity(url("/api/v1/orders/probe"), HttpEntity.EMPTY, String.class)
                            .getStatusCode()
                            .value());
        }
        ResponseEntity<String> limited =
                rest.postForEntity(url("/api/v1/orders/probe"), HttpEntity.EMPTY, String.class);
        assertEquals(429, limited.getStatusCode().value());
        assertEquals(1006, (int) JsonPath.read(limited.getBody(), "$.code"));
    }

    @Test
    void authMatrixOnAdminEndpoint() {
        // 无 token → 2001
        ResponseEntity<String> noToken =
                rest.getForEntity(url("/api/v1/admin/whoami"), String.class);
        assertEquals(401, noToken.getStatusCode().value());
        assertEquals(2001, (int) JsonPath.read(noToken.getBody(), "$.code"));
        // admin 角色 → 200 + subject
        String token = jwtCodec.issue("alice", List.of("admin"), Duration.ofMinutes(5));
        HttpHeaders auth = new HttpHeaders();
        auth.setBearerAuth(token);
        ResponseEntity<String> ok =
                rest.exchange(
                        url("/api/v1/admin/whoami"),
                        HttpMethod.GET,
                        new HttpEntity<>(auth),
                        String.class);
        assertEquals(200, ok.getStatusCode().value());
        assertEquals("alice", JsonPath.read(ok.getBody(), "$.data.subject"));
        // 普通角色 → 2003
        String user = jwtCodec.issue("bob", List.of("user"), Duration.ofMinutes(5));
        HttpHeaders userHeaders = new HttpHeaders();
        userHeaders.setBearerAuth(user);
        ResponseEntity<String> forbidden =
                rest.exchange(
                        url("/api/v1/admin/whoami"),
                        HttpMethod.GET,
                        new HttpEntity<>(userHeaders),
                        String.class);
        assertEquals(403, forbidden.getStatusCode().value());
        assertEquals(2003, (int) JsonPath.read(forbidden.getBody(), "$.code"));
    }

    @Test
    void captchaEndpointServesKeyAndImage() {
        ResponseEntity<String> captcha = rest.getForEntity(url("/api/v1/captcha"), String.class);
        assertEquals(200, captcha.getStatusCode().value());
        String key = JsonPath.read(captcha.getBody(), "$.data.key");
        String image = JsonPath.read(captcha.getBody(), "$.data.imageBase64");
        assertTrue(key != null && !key.isBlank());
        assertTrue(image != null && image.length() > 100);
        assertNotEquals("", image);
    }
}
