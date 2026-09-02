package ${package}.model.dto;

import ${package}.model.UserDO;
import java.time.Instant;

/** DTO：Service 向外输出（时间 ISO-8601 UTC，D4） */
public record UserDTO(Long id, String email, String name, Instant createdAt) {

    public static UserDTO from(UserDO user) {
        return new UserDTO(user.getId(), user.getEmail(), user.getName(), user.getCreatedAt());
    }
}
