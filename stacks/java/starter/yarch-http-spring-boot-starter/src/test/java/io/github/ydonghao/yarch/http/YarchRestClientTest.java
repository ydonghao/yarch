package io.github.ydonghao.yarch.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.github.ydonghao.yarch.common.code.BusinessException;
import io.github.ydonghao.yarch.common.code.GlobalErrorCode;
import io.github.ydonghao.yarch.common.trace.TraceIds;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** 出口传播契约验收（JDK 内嵌 HttpServer，无外部依赖） */
class YarchRestClientTest {

    static HttpServer server;
    static String base;
    static final AtomicReference<String> lastTraceParent = new AtomicReference<>();

    @BeforeAll
    static void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(
                "/echo",
                exchange -> {
                    lastTraceParent.set(exchange.getRequestHeaders().getFirst("traceparent"));
                    respond(
                            exchange,
                            200,
                            "{\"code\":0,\"message\":\"成功\",\"data\":{\"name\":\"keyboard\"},\"traceId\":\"t\"}");
                });
        server.createContext(
                "/bizerr",
                exchange ->
                        respond(
                                exchange,
                                200,
                                "{\"code\":3001,\"message\":\"商品不存在\",\"data\":null,\"traceId\":\"t\"}"));
        server.createContext("/down", exchange -> respond(exchange, 503, "unavailable"));
        server.createContext(
                "/slow",
                exchange -> {
                    try {
                        Thread.sleep(2000);
                    } catch (InterruptedException ignored) {
                        Thread.currentThread().interrupt();
                    }
                    respond(
                            exchange,
                            200,
                            "{\"code\":0,\"message\":\"成功\",\"data\":{},\"traceId\":\"t\"}");
                });
        server.start();
        base = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterAll
    static void stopServer() {
        server.stop(0);
    }

    static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    @Test
    void unwrapsEnvelopeAndPropagatesTraceParent() {
        TraceIds.put("0af7651916cd43dd8448eb211c80319c");
        try {
            YarchRestClient client = YarchRestClient.builder().baseUrl(base).build();
            Item item = client.get("/echo", Item.class);
            assertEquals("keyboard", item.name());
            assertTrue(
                    lastTraceParent.get().startsWith("00-0af7651916cd43dd8448eb211c80319c-"),
                    "traceparent 必须携带当前 traceId（传播矩阵出口行）: " + lastTraceParent.get());
        } finally {
            TraceIds.remove();
        }
    }

    @Test
    void propagatesDownstreamBusinessCode() {
        YarchRestClient client = YarchRestClient.builder().baseUrl(base).build();
        BusinessException ex =
                assertThrows(BusinessException.class, () -> client.get("/bizerr", Item.class));
        assertEquals(3001, ex.getErrorCode().code());
        assertEquals("商品不存在", ex.getMessage());
    }

    @Test
    void timeoutTranslatesTo1008() {
        YarchRestClient client =
                YarchRestClient.builder().baseUrl(base).readTimeout(Duration.ofMillis(300)).build();
        BusinessException ex =
                assertThrows(BusinessException.class, () -> client.get("/slow", Item.class));
        assertEquals(GlobalErrorCode.UPSTREAM_TIMEOUT.code(), ex.getErrorCode().code());
    }

    @Test
    void unavailableTranslatesTo1009() {
        YarchRestClient client = YarchRestClient.builder().baseUrl(base).build();
        BusinessException ex =
                assertThrows(BusinessException.class, () -> client.get("/down", Item.class));
        assertEquals(GlobalErrorCode.UNAVAILABLE.code(), ex.getErrorCode().code());
    }

    record Item(String name) {}
}
