package io.github.yuandonghao.yarch.persistence;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.github.yuandonghao.yarch.persistence.entity.BaseEntity;
import org.apache.ibatis.annotations.Mapper;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;

@SpringBootConfiguration
@EnableAutoConfiguration
public class PersistenceTestApp {

    @TableName("test_users")
    public static class TestUserPO extends BaseEntity {
        private String name;

        public TestUserPO() {}

        public TestUserPO(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    @Mapper
    public interface TestUserMapper extends BaseMapper<TestUserPO> {}
}
