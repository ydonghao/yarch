package ${package}.api.assembler;

import ${package}.api.dto.UserResponse;
import ${package}.domain.model.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/** 对象转换器（J6：MapStruct，编译期生成）；DTO 不渗入领域层 */
@Mapper(componentModel = "spring")
public interface UserAssembler {

    @Mapping(target = "id", source = "id")
    UserResponse toResponse(User user);
}
