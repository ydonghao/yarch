package ${package}.api.dto;

import java.time.Instant;

/** 时间一律 ISO-8601 UTC（D4）：DTO 用 Instant，Jackson 3 默认即 ISO 字符串 */
public record UserResponse(Long id, String email, String name, Instant createdAt) {}
