package io.github.yuandonghao.yarch.examples.ddd.api.dto;

import io.github.yuandonghao.yarch.examples.ddd.domain.model.Product;
import java.time.Instant;

/** 金额整型分值（rest-conventions.md：金额用字符串或整型分值，禁浮点） */
public record ProductResponse(Long id, String name, long priceCents, int stock, Instant createdAt) {

    public static ProductResponse from(Product product) {
        return new ProductResponse(
                product.id(),
                product.name(),
                product.priceCents(),
                product.stock(),
                product.createdAt());
    }
}
