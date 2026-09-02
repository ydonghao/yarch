package ${package}.domain.model;

import java.time.Instant;

/**
 * 领域模型（充血但克制：业务行为进模型，J1a）：不依赖任何框架—— ArchUnit 守护 domain 零 Spring/MyBatis 依赖（见
 * ArchitectureGuardTest）。
 */
public record User(Long id, String email, String name, Instant createdAt) {

    /** 创建工厂：新用户（持久化前的中间态，id 由仓储回填） */
    public static User register(String email, String name) {
        return new User(null, email, name, null);
    }

    /** 重建：从持久层还原 */
    public User rehydrate(Long id, Instant createdAt) {
        return new User(id, email, name, createdAt);
    }

    public User rename(String newName) {
        return new User(id, email, newName, createdAt);
    }
}
