package ${package}.domain.service;

import ${package}.domain.model.User;
import ${package}.domain.repository.UserRepository;
import ${package}.types.errno.UserErrorCode;
import io.github.ydonghao.yarch.common.code.BusinessException;

/** 领域服务：跨实体的业务规则（唯一性等）。纯 Java——零框架依赖（ArchUnit 守护）， 由 application 层组装（构造注入仓储接口）。 */
public class UserDomainService {

    private final UserRepository userRepository;

    public UserDomainService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public User register(String email, String name) {
        if (userRepository.existsByEmail(email)) {
            throw new BusinessException(UserErrorCode.EMAIL_EXISTS, email);
        }
        return User.register(email, name);
    }
}
