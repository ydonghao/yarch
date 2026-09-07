package io.github.ydonghao.yarch.examples.ddd.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateProductRequest(
        @NotBlank @Size(max = 100) String name,
        @NotNull @Min(1) Long priceCents,
        @NotNull @Min(0) Integer stock) {}
