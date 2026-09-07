package ${package}.domain.repository;

import ${package}.domain.model.User;
import io.github.ydonghao.yarch.common.web.PageData;
import java.util.Optional;

/** 仓储接口归领域（依赖倒置），实现在 infrastructure.persistence */
public interface UserRepository {

    User save(User user);

    Optional<User> findById(Long id);

    boolean existsByEmail(String email);

    PageData<User> page(int page, int pageSize);

    void deleteById(Long id);
}
