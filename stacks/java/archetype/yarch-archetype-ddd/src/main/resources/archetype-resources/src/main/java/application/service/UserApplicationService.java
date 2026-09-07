package ${package}.application.service;

import ${package}.application.command.CreateUserCommand;
import ${package}.domain.model.User;
import ${package}.domain.repository.UserRepository;
import ${package}.domain.service.UserDomainService;
import io.github.ydonghao.yarch.common.code.BusinessException;
import io.github.ydonghao.yarch.common.code.GlobalErrorCode;
import io.github.ydonghao.yarch.common.web.PageData;
import io.github.ydonghao.yarch.web.PageQuery;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** application 层：编排领域服务与仓储、事务边界；不写业务规则（规则在 domain） */
@Service
public class UserApplicationService {

    private final UserRepository userRepository;
    private final UserDomainService domainService;

    public UserApplicationService(UserRepository userRepository) {
        this.userRepository = userRepository;
        this.domainService = new UserDomainService(userRepository);
    }

    @Transactional
    public User create(CreateUserCommand command) {
        User user = domainService.register(command.email(), command.name());
        return userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public User requireById(Long id) {
        return userRepository
                .findById(id)
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.NOT_FOUND, "user " + id));
    }

    @Transactional(readOnly = true)
    public PageData<User> page(PageQuery query) {
        return userRepository.page(query.getPage(), query.getPageSize());
    }

    @Transactional
    public void delete(Long id) {
        userRepository.deleteById(id);
    }
}
