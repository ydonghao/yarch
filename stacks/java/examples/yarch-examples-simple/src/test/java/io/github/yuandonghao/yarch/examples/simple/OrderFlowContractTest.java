package io.github.yuandonghao.yarch.examples.simple;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.jayway.jsonpath.JsonPath;
import io.github.yuandonghao.yarch.redis.RedisKeys;
import io.github.yuandonghao.yarch.test.containers.PgTestDb;
import io.github.yuandonghao.yarch.test.containers.RedisTestDb;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** 案例端到端（simple 档同套验收语义）：幂等下单、3002、cache-aside、keyset */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OrderFlowContractTest {

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
        assumeTrue(pg != null && redis != null, "本机无 Docker，跳过案例端到端测试");
    }

    @org.springframework.beans.factory.annotation.Value("${local.server.port}")
    int port;

    TestRestTemplate rest = new TestRestTemplate();

    @Autowired StringRedisTemplate stringRedis;

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private ResponseEntity<String> post(String path, String json, String idemKey) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (idemKey != null) {
            headers.set("Idempotency-Key", idemKey);
        }
        return rest.exchange(
                url(path), HttpMethod.POST, new HttpEntity<>(json, headers), String.class);
    }

    @Test
    void fullOrderFlowWithIdempotencyStockAndCache() {
        ResponseEntity<String> created =
                post(
                        "/api/v1/products",
                        "{\"name\":\"keyboard\",\"priceCents\":19900,\"stock\":10}",
                        null);
        assertEquals(201, created.getStatusCode().value());
        long productId = ((Number) JsonPath.read(created.getBody(), "$.data.id")).longValue();

        String cacheKey =
                RedisKeys.of("yarch-examples-simple").parts("product", "detail", productId);
        stringRedis.delete(cacheKey);
        assertEquals(
                200,
                rest.getForEntity(url("/api/v1/products/" + productId), String.class)
                        .getStatusCode()
                        .value());
        assertNotNull(stringRedis.opsForValue().get(cacheKey), "miss 后必须回填缓存");
        assertTrue(stringRedis.getExpire(cacheKey) > 0);

        String orderJson =
                "{\"productId\":" + productId + ",\"buyerEmail\":\"a@b.c\",\"quantity\":3}";
        ResponseEntity<String> first = post("/api/v1/orders", orderJson, "idem-flow-1");
        assertEquals(201, first.getStatusCode().value());
        long orderId = ((Number) JsonPath.read(first.getBody(), "$.data.id")).longValue();
        ResponseEntity<String> replay = post("/api/v1/orders", orderJson, "idem-flow-1");
        assertEquals(orderId, ((Number) JsonPath.read(replay.getBody(), "$.data.id")).longValue());

        assertNull(stringRedis.opsForValue().get(cacheKey), "写路径必须删缓存");
        ResponseEntity<String> after =
                rest.getForEntity(url("/api/v1/products/" + productId), String.class);
        assertEquals(7, (int) JsonPath.read(after.getBody(), "$.data.stock"), "幂等回放不得二次扣减");

        ResponseEntity<String> insufficient =
                post(
                        "/api/v1/orders",
                        "{\"productId\":" + productId + ",\"buyerEmail\":\"a@b.c\",\"quantity\":8}",
                        null);
        assertEquals(409, insufficient.getStatusCode().value());
        assertEquals(3002, (int) JsonPath.read(insufficient.getBody(), "$.code"));
    }

    @Test
    void keysetPaginationWalksWindowsInOrder() {
        long productId =
                ((Number)
                                JsonPath.read(
                                        post(
                                                        "/api/v1/products",
                                                        "{\"name\":\"mouse\",\"priceCents\":4900,\"stock\":50}",
                                                        null)
                                                .getBody(),
                                        "$.data.id"))
                        .longValue();
        for (int i = 0; i < 7; i++) {
            assertEquals(
                    201,
                    post(
                                    "/api/v1/orders",
                                    "{\"productId\":"
                                            + productId
                                            + ",\"buyerEmail\":\"u"
                                            + i
                                            + "@b.c\",\"quantity\":1}",
                                    null)
                            .getStatusCode()
                            .value());
        }

        com.jayway.jsonpath.Configuration lenient =
                com.jayway.jsonpath.Configuration.builder()
                        .options(com.jayway.jsonpath.Option.SUPPRESS_EXCEPTIONS)
                        .build();
        String cursor = null;
        int[] windowSizes = {3, 3, 1};
        long lastId = 0;
        for (int expected : windowSizes) {
            String uri = "/api/v1/orders?pageSize=3" + (cursor == null ? "" : "&cursor=" + cursor);
            ResponseEntity<String> page = rest.getForEntity(url(uri), String.class);
            assertEquals(200, page.getStatusCode().value());
            List<Number> ids = JsonPath.read(page.getBody(), "$.data.list[*].id");
            assertEquals(expected, ids.size());
            for (Number id : ids) {
                assertTrue(id.longValue() > lastId, "游标窗口必须严格递增");
                lastId = id.longValue();
            }
            cursor =
                    com.jayway.jsonpath.JsonPath.using(lenient)
                            .parse(page.getBody())
                            .read("$.data.nextCursor", String.class);
        }
        assertNull(cursor, "最后一窗 nextCursor 必须为空/缺失");
    }
}
