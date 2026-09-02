package io.github.yuandonghao.yarch.examples.ddd;

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

/** 案例端到端：幂等下单（库存只扣一次）、3002 库存不足、cache-aside 回填、keyset 游标流转 */
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
        // 建商品（库存 10）
        ResponseEntity<String> created =
                post(
                        "/api/v1/products",
                        "{\"name\":\"keyboard\",\"priceCents\":19900,\"stock\":10}",
                        null);
        assertEquals(201, created.getStatusCode().value());
        long productId = ((Number) JsonPath.read(created.getBody(), "$.data.id")).longValue();

        // cache-aside：首次读回源并回填（Redis key 出现且带 TTL）
        String cacheKey = RedisKeys.of("yarch-examples-ddd").parts("product", "detail", productId);
        stringRedis.delete(cacheKey);
        assertEquals(
                200,
                rest.getForEntity(url("/api/v1/products/" + productId), String.class)
                        .getStatusCode()
                        .value());
        assertNotNull(stringRedis.opsForValue().get(cacheKey), "miss 后必须回填缓存");
        assertTrue(stringRedis.getExpire(cacheKey) > 0, "缓存必须带 TTL");

        // 幂等下单 ×2 同键同参：回放，库存只扣一次
        String orderJson =
                "{\"productId\":" + productId + ",\"buyerEmail\":\"a@b.c\",\"quantity\":3}";
        ResponseEntity<String> first = post("/api/v1/orders", orderJson, "idem-flow-1");
        assertEquals(201, first.getStatusCode().value());
        long orderId = ((Number) JsonPath.read(first.getBody(), "$.data.id")).longValue();
        ResponseEntity<String> replay = post("/api/v1/orders", orderJson, "idem-flow-1");
        assertEquals(orderId, ((Number) JsonPath.read(replay.getBody(), "$.data.id")).longValue());

        // 扣减后缓存被清除（写路径删缓存），再读得到新库存 7
        assertNull(stringRedis.opsForValue().get(cacheKey), "写路径必须删缓存");
        ResponseEntity<String> after =
                rest.getForEntity(url("/api/v1/products/" + productId), String.class);
        assertEquals(7, (int) JsonPath.read(after.getBody(), "$.data.stock"), "幂等回放不得二次扣减");

        // 库存不足 → 3002/409（再买 8 个）
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

        // pageSize=3 三窗：3 + 3 + 1，游标严格递增，无重叠无遗漏
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
            // 契约：nextCursor 缺失或空串表示没有下一页（末窗按 NON_NULL 省略）
            cursor =
                    com.jayway.jsonpath.JsonPath.using(lenient)
                            .parse(page.getBody())
                            .read("$.data.nextCursor", String.class);
        }
        assertNull(cursor, "最后一窗 nextCursor 必须为空/缺失");
    }
}
