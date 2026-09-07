package io.github.ydonghao.yarch.logging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.OutputStreamAppender;
import io.github.ydonghao.yarch.common.trace.TraceIds;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import net.logstash.logback.argument.StructuredArguments;
import net.logstash.logback.encoder.LogstashEncoder;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;

/** ndjson 行协议契约断言：默认配置已装配 + 编码器输出逐字段验证 */
@SpringBootTest(
        classes = NdjsonLineProtocolTest.App.class,
        properties = {
            "spring.application.name=contract-test-svc",
            "yarch.env=test",
        })
class NdjsonLineProtocolTest {

    @org.springframework.boot.SpringBootConfiguration
    @org.springframework.boot.autoconfigure.EnableAutoConfiguration
    static class App {}

    @Autowired Environment environment;

    @Test
    void defaultNdjsonConfigIsWired() {
        assertEquals("contract-test-svc", environment.getProperty("spring.application.name"));
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        assertNotNull(
                context.getLogger(ch.qos.logback.classic.Logger.ROOT_LOGGER_NAME)
                        .getAppender("CONSOLE"),
                "yarch 默认 ndjson 配置未生效（应存在 CONSOLE appender）");
    }

    @Test
    void encoderProducesContractFieldSet() throws Exception {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();

        LogstashEncoder encoder = new LogstashEncoder();
        // 复制契约编码器的关键定制（ts/msg/logger/stack 字段名 + UTC + 毫秒）
        encoder.getFieldNames().setTimestamp("ts");
        encoder.getFieldNames().setMessage("msg");
        encoder.getFieldNames().setLogger("logger");
        encoder.getFieldNames().setStackTrace("stack");
        encoder.setTimeZone("UTC");
        encoder.setTimestampPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
        encoder.setContext(context);
        encoder.start();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        OutputStreamAppender<ILoggingEvent> appender = new OutputStreamAppender<>();
        appender.setEncoder(encoder);
        appender.setOutputStream(out);
        appender.setContext(context);
        appender.start();

        ch.qos.logback.classic.Logger logger =
                context.getLogger("io.github.ydonghao.yarch.logging.NdjsonProbe");
        logger.addAppender(appender);
        logger.setAdditive(false);

        TraceIds.put("0af7651916cd43dd8448eb211c80319c");
        try {
            logger.info(
                    "request completed",
                    StructuredArguments.kv("method", "GET"),
                    StructuredArguments.kv("path", "/api/v1/users"),
                    StructuredArguments.kv("status", 200),
                    StructuredArguments.kv("costMs", 12));
        } finally {
            TraceIds.remove();
            logger.detachAppender(appender);
            appender.stop();
        }

        String line = out.toString(StandardCharsets.UTF_8).trim();
        com.fasterxml.jackson.databind.ObjectMapper mapper =
                new com.fasterxml.jackson.databind.ObjectMapper();
        Map<String, Object> json = mapper.readValue(line, Map.class);

        assertEquals("0af7651916cd43dd8448eb211c80319c", json.get("traceId"));
        assertEquals("request completed", json.get("msg"));
        assertEquals("INFO", json.get("level"));
        assertEquals("io.github.ydonghao.yarch.logging.NdjsonProbe", json.get("logger"));
        assertEquals("GET", json.get("method"));
        assertEquals("/api/v1/users", json.get("path"));
        assertEquals(200, json.get("status"));
        assertEquals(12, json.get("costMs"));
        assertTrue(
                String.valueOf(json.get("ts"))
                        .matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}Z"),
                "ts 必须 RFC3339 毫秒 UTC 恒以 Z 结尾: " + json.get("ts"));
    }
}
