package io.github.yuandonghao.yarch.examples.simple.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.github.yuandonghao.yarch.examples.simple.model.ProductDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ProductMapper extends BaseMapper<ProductDO> {}
