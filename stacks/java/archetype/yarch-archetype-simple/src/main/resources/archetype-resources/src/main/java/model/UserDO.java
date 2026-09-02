package ${package}.model;

import com.baomidou.mybatisplus.annotation.TableName;
import io.github.yuandonghao.yarch.persistence.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/** DO：与表一一对应（简单贫血档：DO 即模型；逻辑删除/审计列继承 BaseEntity） */
@Getter
@Setter
@TableName("users")
public class UserDO extends BaseEntity {

    private String email;
    private String name;
}
