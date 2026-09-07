package io.github.ydonghao.yarch.test.containers;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * PG 测试容器基座（postgresql 规约 14+，现役 17）： 只用 Testcontainers 核心 GenericContainer，封装掉 TC 2.x 模块坐标变动。
 * 用法：static PgTestDb pg = PgTestDb.start(); 然后 @DynamicPropertySource 里 pg.register(registry)。
 */
public final class PgTestDb implements AutoCloseable {

    public static final String DEFAULT_IMAGE = "postgres:17-alpine";
    public static final String USER = "yarch";
    public static final String PASSWORD = "yarch";
    public static final String DATABASE = "yarch";

    private final GenericContainer<?> container;

    private PgTestDb(DockerImageName image) {
        this.container =
                new GenericContainer<>(image)
                        .withExposedPorts(5432)
                        .withEnv("POSTGRES_USER", USER)
                        .withEnv("POSTGRES_PASSWORD", PASSWORD)
                        .withEnv("POSTGRES_DB", DATABASE);
    }

    public static boolean dockerAvailable() {
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        } catch (Throwable t) {
            return false;
        }
    }

    public static PgTestDb start() {
        return start(DEFAULT_IMAGE);
    }

    public static PgTestDb start(String image) {
        PgTestDb db = new PgTestDb(DockerImageName.parse(image));
        db.container.start();
        return db;
    }

    public String host() {
        return container.getHost();
    }

    public int port() {
        return container.getMappedPort(5432);
    }

    public String jdbcUrl() {
        return "jdbc:postgresql://" + host() + ":" + port() + "/" + DATABASE;
    }

    public void register(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", this::jdbcUrl);
        registry.add("spring.datasource.username", () -> USER);
        registry.add("spring.datasource.password", () -> PASSWORD);
    }

    @Override
    public void close() {
        container.stop();
    }
}
