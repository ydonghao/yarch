package ${package}.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import ${package}.dao.UserMapper;
import ${package}.model.UserDO;
import ${package}.model.dto.UserCreateRequest;
import ${package}.model.dto.UserDTO;
import ${package}.types.errno.UserErrorCode;
import io.github.yuandonghao.yarch.common.code.BusinessException;
import io.github.yuandonghao.yarch.common.code.GlobalErrorCode;
import io.github.yuandonghao.yarch.common.web.PageData;
import io.github.yuandonghao.yarch.persistence.support.PageDatas;
import io.github.yuandonghao.yarch.web.PageQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Service 层：具体业务逻辑（简单贫血档：DO 即模型，无独立领域层） */
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserMapper userMapper;

    @Transactional
    public UserDTO create(UserCreateRequest request) {
        if (userMapper.selectCount(Wrappers.<UserDO>query().eq("email", request.email())) > 0) {
            throw new BusinessException(UserErrorCode.EMAIL_EXISTS, request.email());
        }
        UserDO user = new UserDO();
        user.setEmail(request.email());
        user.setName(request.name());
        userMapper.insert(user);
        return UserDTO.from(user);
    }

    @Transactional(readOnly = true)
    public UserDTO requireById(Long id) {
        UserDO user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException(GlobalErrorCode.NOT_FOUND, "user " + id);
        }
        return UserDTO.from(user);
    }

    @Transactional(readOnly = true)
    public PageData<UserDTO> page(PageQuery query) {
        return PageDatas.of(
                userMapper.selectPage(
                        PageDatas.mpPage(query.getPage(), query.getPageSize()),
                        Wrappers.<UserDO>query().orderByAsc("id")),
                UserDTO::from);
    }

    @Transactional
    public void delete(Long id) {
        userMapper.deleteById(id); // 逻辑删除
    }
}
