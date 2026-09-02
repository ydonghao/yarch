package ${package}.infrastructure.persistence;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import ${package}.domain.model.User;
import ${package}.domain.repository.UserRepository;
import io.github.yuandonghao.yarch.common.web.PageData;
import io.github.yuandonghao.yarch.persistence.support.PageDatas;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/** 仓储实现：MP 细节全部封在这里；分页下推（G5）、逻辑删除、审计填充由 starter 保障 */
@Repository
@RequiredArgsConstructor
public class UserRepositoryImpl implements UserRepository {

    private final UserMapper mapper;

    @Override
    public User save(User user) {
        UserPO po = UserPO.fromDomain(user);
        if (po.getId() == null) {
            mapper.insert(po);
        } else {
            // G8：变更集更新——显式列，不做全字段 updateById
            UserPO patch = new UserPO();
            patch.setId(po.getId());
            patch.setEmail(po.getEmail());
            patch.setName(po.getName());
            mapper.updateById(patch);
        }
        return po.toDomain();
    }

    @Override
    public Optional<User> findById(Long id) {
        return Optional.ofNullable(mapper.selectById(id)).map(UserPO::toDomain);
    }

    @Override
    public boolean existsByEmail(String email) {
        return mapper.selectCount(Wrappers.<UserPO>query().eq("email", email)) > 0;
    }

    @Override
    public PageData<User> page(int page, int pageSize) {
        return PageDatas.of(
                mapper.selectPage(
                        PageDatas.mpPage(page, pageSize),
                        Wrappers.<UserPO>query().orderByAsc("id")),
                UserPO::toDomain);
    }

    @Override
    public void deleteById(Long id) {
        mapper.deleteById(id); // 逻辑删除（BaseEntity @TableLogic）
    }
}
