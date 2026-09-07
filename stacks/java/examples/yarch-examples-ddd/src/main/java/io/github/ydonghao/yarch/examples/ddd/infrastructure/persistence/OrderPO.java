package io.github.ydonghao.yarch.examples.ddd.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.TableName;
import io.github.ydonghao.yarch.examples.ddd.domain.model.Order;
import io.github.ydonghao.yarch.persistence.entity.BaseEntity;

@TableName("orders")
public class OrderPO extends BaseEntity {

    private Long productId;
    private String buyerEmail;
    private Integer quantity;
    private String status;

    public static OrderPO fromDomain(Order order) {
        OrderPO po = new OrderPO();
        po.setId(order.id());
        po.setProductId(order.productId());
        po.setBuyerEmail(order.buyerEmail());
        po.setQuantity(order.quantity());
        po.setStatus(order.status());
        return po;
    }

    public Order toDomain() {
        return new Order(getId(), productId, buyerEmail, quantity, status, getCreatedAt());
    }

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public String getBuyerEmail() {
        return buyerEmail;
    }

    public void setBuyerEmail(String buyerEmail) {
        this.buyerEmail = buyerEmail;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
