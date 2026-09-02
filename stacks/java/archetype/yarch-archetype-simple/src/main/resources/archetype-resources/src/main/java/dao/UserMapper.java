package ${package}.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import ${package}.model.UserDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserMapper extends BaseMapper<UserDO> {}
