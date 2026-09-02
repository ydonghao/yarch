package io.github.yuandonghao.yarch.examples.simple.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.github.yuandonghao.yarch.examples.simple.model.OrderDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface OrderMapper extends BaseMapper<OrderDO> {}
