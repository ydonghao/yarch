package io.github.ydonghao.yarch.examples.simple.controller;

import io.github.ydonghao.yarch.common.web.RestResponse;
import io.github.ydonghao.yarch.examples.simple.model.dto.Dtos.CreateProductRequest;
import io.github.ydonghao.yarch.examples.simple.model.dto.Dtos.ProductDTO;
import io.github.ydonghao.yarch.examples.simple.service.ProductService;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/products")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @PostMapping
    public ResponseEntity<RestResponse<ProductDTO>> create(
            @Valid @RequestBody CreateProductRequest request) {
        ProductDTO created = productService.create(request);
        return ResponseEntity.created(URI.create("/api/v1/products/" + created.id()))
                .body(RestResponse.ok(created));
    }

    @GetMapping("/{productId}")
    public RestResponse<ProductDTO> get(@PathVariable Long productId) {
        return RestResponse.ok(productService.requireById(productId));
    }
}
