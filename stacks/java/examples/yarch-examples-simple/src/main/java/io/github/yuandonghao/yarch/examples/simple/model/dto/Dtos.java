package io.github.yuandonghao.yarch.examples.simple.model.dto;

import io.github.yuandonghao.yarch.examples.simple.model.OrderDO;
import io.github.yuandonghao.yarch.examples.simple.model.ProductDO;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

/** 对象族（DO 之外）：请求体 + DTO 输出。金额整型分值、时间 ISO-8601（契约 D4） */
public final class Dtos {

    private Dtos() {}

    public record CreateProductRequest(
            @NotBlank @Size(max = 100) String name,
            @NotNull @Min(1) Long priceCents,
            @NotNull @Min(0) Integer stock) {}

    public record ProductDTO(Long id, String name, long priceCents, int stock, Instant createdAt) {
        public static ProductDTO from(ProductDO p) {
            return new ProductDTO(
                    p.getId(), p.getName(), p.getPriceCents(), p.getStock(), p.getCreatedAt());
        }
    }

    public record CreateOrderRequest(
            @NotNull Long productId,
            @NotBlank @Email String buyerEmail,
            @NotNull @Min(1) @Max(100) Integer quantity) {}

    public record OrderDTO(
            Long id,
            Long productId,
            String buyerEmail,
            int quantity,
            String status,
            Instant createdAt) {
        public static OrderDTO from(OrderDO o) {
            return new OrderDTO(
                    o.getId(),
                    o.getProductId(),
                    o.getBuyerEmail(),
                    o.getQuantity(),
                    o.getStatus(),
                    o.getCreatedAt());
        }
    }
}
