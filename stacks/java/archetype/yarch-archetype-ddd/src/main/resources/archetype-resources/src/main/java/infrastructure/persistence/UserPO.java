package ${package}.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.TableName;
import ${package}.domain.model.User;
import io.github.yuandonghao.yarch.persistence.entity.BaseEntity;

/** 持久化对象：MP 注解只出现在 infrastructure（domain 保持零框架依赖）；逻辑删除/审计列继承 BaseEntity */
@TableName("users")
public class UserPO extends BaseEntity {

    private String email;
    private String name;

    public static UserPO fromDomain(User user) {
        UserPO po = new UserPO();
        po.setId(user.id());
        po.setEmail(user.email());
        po.setName(user.name());
        return po;
    }

    public User toDomain() {
        return new User(getId(), email, name, getCreatedAt());
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
