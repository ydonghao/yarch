package io.github.ydonghao.yarch.examples.simple.model;

import com.baomidou.mybatisplus.annotation.TableName;
import io.github.ydonghao.yarch.persistence.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("orders")
public class OrderDO extends BaseEntity {

    private Long productId;
    private String buyerEmail;
    private Integer quantity;
    private String status;
}
