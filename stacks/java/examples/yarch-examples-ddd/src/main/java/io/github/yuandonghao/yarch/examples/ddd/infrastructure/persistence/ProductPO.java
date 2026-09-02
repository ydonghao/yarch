package io.github.yuandonghao.yarch.examples.ddd.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.TableName;
import io.github.yuandonghao.yarch.examples.ddd.domain.model.Product;
import io.github.yuandonghao.yarch.persistence.entity.BaseEntity;

@TableName("products")
public class ProductPO extends BaseEntity {

    private String name;
    private Long priceCents;
    private Integer stock;

    public static ProductPO fromDomain(Product product) {
        ProductPO po = new ProductPO();
        po.setId(product.id());
        po.setName(product.name());
        po.setPriceCents(product.priceCents());
        po.setStock(product.stock());
        return po;
    }

    public Product toDomain() {
        return new Product(getId(), name, priceCents, stock, getCreatedAt());
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Long getPriceCents() {
        return priceCents;
    }

    public void setPriceCents(Long priceCents) {
        this.priceCents = priceCents;
    }

    public Integer getStock() {
        return stock;
    }

    public void setStock(Integer stock) {
        this.stock = stock;
    }
}
