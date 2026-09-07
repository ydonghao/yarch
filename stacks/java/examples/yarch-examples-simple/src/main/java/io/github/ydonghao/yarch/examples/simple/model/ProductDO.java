package io.github.ydonghao.yarch.examples.simple.model;

import com.baomidou.mybatisplus.annotation.TableName;
import io.github.ydonghao.yarch.persistence.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/** DO：与表一一对应（简单贫血档） */
@Getter
@Setter
@TableName("products")
public class ProductDO extends BaseEntity {

    private String name;
    private Long priceCents;
    private Integer stock;
}
