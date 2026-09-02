package io.github.yuandonghao.yarch.examples.ddd.api.dto;

import io.github.yuandonghao.yarch.examples.ddd.domain.model.Order;
import java.time.Instant;

public record OrderResponse(
        Long id,
        Long productId,
        String buyerEmail,
        int quantity,
        String status,
        Instant createdAt) {

    public static OrderResponse from(Order order) {
        return new OrderResponse(
                order.id(),
                order.productId(),
                order.buyerEmail(),
                order.quantity(),
                order.status(),
                order.createdAt());
    }
}
