package io.github.ydonghao.yarch.test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.github.ydonghao.yarch.common.code.GlobalErrorCode;
import io.github.ydonghao.yarch.common.web.PageData;
import io.github.ydonghao.yarch.common.web.RestResponse;
import io.github.ydonghao.yarch.test.containers.PgTestDb;
import io.github.ydonghao.yarch.test.containers.RedisTestDb;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 基座自检：契约断言工具自身可用 + 容器基座可启动（Docker 可用时） */
class ContractAssertsSelfTest {

    @Test
    void errorCodeTableAssertsPass() {
        // 全表核对（13 码 + HTTP 映射 + 默认文案），任何漂移在此失败
        ContractAsserts.assertErrorCodeTable();
    }

    @Test
    void envelopeAndPageDataAsserts() {
        ContractAsserts.assertSuccessEnvelope(RestResponse.ok(List.of(1)));
        ContractAsserts.assertErrorEnvelope(
                RestResponse.fail(GlobalErrorCode.NOT_FOUND, "资源不存在：item 1"),
                GlobalErrorCode.NOT_FOUND);
        ContractAsserts.assertPageDataShape(PageData.of(List.of(), 0, 1, 20));
        assertThrows(
                AssertionError.class,
                () ->
                        ContractAsserts.assertErrorEnvelope(
                                RestResponse.ok(), GlobalErrorCode.NOT_FOUND));
    }

    @Test
    void formatAsserts() {
        ContractAsserts.assertIso8601Utc("2026-09-01T12:00:00Z");
        ContractAsserts.assertIso8601Utc("2026-09-01T12:00:00.123Z");
        assertThrows(
                AssertionError.class,
                () -> ContractAsserts.assertIso8601Utc("2026-09-01 12:00:00"));
        assertThrows(
                AssertionError.class,
                () -> ContractAsserts.assertIso8601Utc("2026-09-01T12:00:00+08:00"));
        ContractAsserts.assertTraceIdFormat("0af7651916cd43dd8448eb211c80319c");
        assertThrows(AssertionError.class, () -> ContractAsserts.assertTraceIdFormat("ABC"));
    }

    @Test
    void containersStartWhenDockerAvailable() {
        assumeTrue(PgTestDb.dockerAvailable(), "本机无 Docker，跳过容器自检");
        try (PgTestDb pg = PgTestDb.start()) {
            org.junit.jupiter.api.Assertions.assertTrue(
                    pg.jdbcUrl().startsWith("jdbc:postgresql://"));
            org.junit.jupiter.api.Assertions.assertTrue(pg.port() > 0);
        }
        try (RedisTestDb redis = RedisTestDb.start()) {
            org.junit.jupiter.api.Assertions.assertTrue(redis.port() > 0);
        }
    }
}
