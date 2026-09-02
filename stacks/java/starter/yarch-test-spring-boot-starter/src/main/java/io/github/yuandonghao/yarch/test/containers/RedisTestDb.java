package io.github.yuandonghao.yarch.test.containers;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Redis 测试容器基座（redis 规约 6+，现役 7）。 用法：static RedisTestDb redis =
 * RedisTestDb.start(); @DynamicPropertySource 里 redis.register(registry)。
 */
public final class RedisTestDb implements AutoCloseable {

    public static final String DEFAULT_IMAGE = "redis:7-alpine";

    private final GenericContainer<?> container;

    private RedisTestDb(DockerImageName image) {
        this.container = new GenericContainer<>(image).withExposedPorts(6379);
    }

    public static boolean dockerAvailable() {
        try {
            return org.testcontainers.DockerClientFactory.instance().isDockerAvailable();
        } catch (Throwable t) {
            return false;
        }
    }

    public static RedisTestDb start() {
        return start(DEFAULT_IMAGE);
    }

    public static RedisTestDb start(String image) {
        RedisTestDb db = new RedisTestDb(DockerImageName.parse(image));
        db.container.start();
        return db;
    }

    public String host() {
        return container.getHost();
    }

    public int port() {
        return container.getMappedPort(6379);
    }

    public void register(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", this::host);
        registry.add("spring.data.redis.port", () -> port());
    }

    @Override
    public void close() {
        container.stop();
    }
}
