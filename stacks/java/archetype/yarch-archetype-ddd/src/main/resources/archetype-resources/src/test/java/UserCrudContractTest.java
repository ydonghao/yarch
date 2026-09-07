package ${package};

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.jayway.jsonpath.JsonPath;
import io.github.ydonghao.yarch.test.containers.PgTestDb;
import io.github.ydonghao.yarch.test.containers.RedisTestDb;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * 生成工程的契约验收（真实 PG + Redis 容器，RANDOM_PORT 全链路）： 信封/201/traceId 回显/分页 D6/参数校验 1001/逻辑删除/幂等回放/业务码
 * 3001。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class UserCrudContractTest {

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
        assumeTrue(pg != null && redis != null, "本机无 Docker，跳过全链路契约测试");
    }

    /** Boot 4 拆分后 TestRestTemplate 自动装配有坑（条件推断缺陷），直接实例化 + 绝对地址 */
    @org.springframework.beans.factory.annotation.Value("${local.server.port}")
    int port;

    TestRestTemplate rest = new TestRestTemplate();

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    static long firstUserId;
    static final String FIRST_EMAIL = "alice@example.com";

    private ResponseEntity<String> postUser(String json, String idemKey) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (idemKey != null) {
            headers.set("Idempotency-Key", idemKey);
        }
        return rest.exchange(
                url("/api/v1/users"),
                HttpMethod.POST,
                new HttpEntity<>(json, headers),
                String.class);
    }

    @Test
    @Order(1)
    void createReturns201EnvelopeWithIsoTimeAndTraceEcho() {
        ResponseEntity<String> resp =
                postUser("{\"email\":\"" + FIRST_EMAIL + "\",\"name\":\"alice\"}", "key-create-1");
        assertEquals(201, resp.getStatusCode().value());
        String body = resp.getBody();
        assertEquals(0, (int) JsonPath.read(body, "$.code"));
        assertEquals("成功", JsonPath.read(body, "$.message"));
        firstUserId = ((Number) JsonPath.read(body, "$.data.id")).longValue();
        assertTrue(
                ((String) JsonPath.read(body, "$.data.createdAt"))
                        .matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?Z"),
                "D4：ISO-8601 UTC");
        assertEquals(
                resp.getHeaders().getFirst("X-Trace-Id"),
                JsonPath.read(body, "$.traceId"),
                "响应头与 body.traceId 恒等（logging-trace.md）");
    }

    @Test
    @Order(2)
    void duplicateEmailIsBusiness3001() {
        ResponseEntity<String> resp =
                postUser("{\"email\":\"" + FIRST_EMAIL + "\",\"name\":\"dup\"}", null);
        assertEquals(409, resp.getStatusCode().value());
        assertEquals(3001, (int) JsonPath.read(resp.getBody(), "$.code"));
        assertTrue(((String) JsonPath.read(resp.getBody(), "$.message")).startsWith("邮箱已存在"));
    }

    @Test
    @Order(3)
    void paginationObeysD6BeyondLastPage() {
        for (int i = 0; i < 24; i++) {
            assertEquals(
                    201,
                    postUser("{\"email\":\"u" + i + "@example.com\",\"name\":\"u" + i + "\"}", null)
                            .getStatusCode()
                            .value());
        }
        ResponseEntity<String> page1 =
                rest.getForEntity(url("/api/v1/users?page=1&pageSize=20"), String.class);
        assertEquals(200, page1.getStatusCode().value());
        assertEquals(25, ((Number) JsonPath.read(page1.getBody(), "$.data.total")).intValue());
        assertEquals(20, ((List<?>) JsonPath.read(page1.getBody(), "$.data.list")).size());

        ResponseEntity<String> beyond =
                rest.getForEntity(url("/api/v1/users?page=999&pageSize=20"), String.class);
        assertEquals(200, beyond.getStatusCode().value());
        assertEquals(0, (int) JsonPath.read(beyond.getBody(), "$.code"));
        assertTrue(
                ((List<?>) JsonPath.read(beyond.getBody(), "$.data.list")).isEmpty(),
                "D6：越界页返回空 list + 真实 total");
    }

    @Test
    @Order(4)
    void pageSizeOverLimitIs1001() {
        ResponseEntity<String> resp =
                rest.getForEntity(url("/api/v1/users?pageSize=500"), String.class);
        assertEquals(400, resp.getStatusCode().value());
        assertEquals(1001, (int) JsonPath.read(resp.getBody(), "$.code"));
    }

    @Test
    @Order(5)
    void getSingleAnd404() {
        ResponseEntity<String> ok =
                rest.getForEntity(url("/api/v1/users/" + firstUserId), String.class);
        assertEquals(200, ok.getStatusCode().value());
        assertEquals(FIRST_EMAIL, JsonPath.read(ok.getBody(), "$.data.email"));

        ResponseEntity<String> missing =
                rest.getForEntity(url("/api/v1/users/999999"), String.class);
        assertEquals(404, missing.getStatusCode().value());
        assertEquals(1004, (int) JsonPath.read(missing.getBody(), "$.code"));
    }

    @Test
    @Order(6)
    void deleteIsLogicalAndReleasesEmail() {
        assertEquals(
                200,
                rest.exchange(
                                url("/api/v1/users/" + firstUserId),
                                HttpMethod.DELETE,
                                HttpEntity.EMPTY,
                                String.class)
                        .getStatusCode()
                        .value());
        assertEquals(
                404,
                rest.getForEntity(url("/api/v1/users/" + firstUserId), String.class)
                        .getStatusCode()
                        .value());
        // 部分唯一索引（postgresql.md 二-2）：逻辑删除后同名邮箱可重新注册
        assertEquals(
                201,
                postUser("{\"email\":\"" + FIRST_EMAIL + "\",\"name\":\"alice-again\"}", null)
                        .getStatusCode()
                        .value());
    }

    @Test
    @Order(7)
    void idempotencyReplaysSameResultAndConflictsOnDifferentBody() {
        String json = "{\"email\":\"idem@example.com\",\"name\":\"idem\"}";
        long firstId =
                ((Number) JsonPath.read(postUser(json, "idem-key-1").getBody(), "$.data.id"))
                        .longValue();

        ResponseEntity<String> replay = postUser(json, "idem-key-1");
        assertEquals(201, replay.getStatusCode().value());
        assertEquals(
                firstId,
                ((Number) JsonPath.read(replay.getBody(), "$.data.id")).longValue(),
                "同键同参必须回放原响应");

        ResponseEntity<String> conflict =
                postUser("{\"email\":\"other@example.com\",\"name\":\"other\"}", "idem-key-1");
        assertEquals(409, conflict.getStatusCode().value());
        assertEquals(1007, (int) JsonPath.read(conflict.getBody(), "$.code"));
    }

    @Test
    @Order(8)
    void actuatorHealthExposed() {
        assertEquals(
                200,
                rest.getForEntity(url("/actuator/health"), String.class).getStatusCode().value());
    }
}
