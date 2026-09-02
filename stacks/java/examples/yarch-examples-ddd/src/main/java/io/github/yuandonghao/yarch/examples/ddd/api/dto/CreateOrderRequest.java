package io.github.yuandonghao.yarch.examples.ddd.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateOrderRequest(
        @NotNull Long productId,
        @NotBlank @jakarta.validation.constraints.Email String buyerEmail,
        @NotNull @Min(1) @Max(100) Integer quantity) {}
