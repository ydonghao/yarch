package io.github.yuandonghao.yarch.examples.ddd.api.controller;

import io.github.yuandonghao.yarch.common.web.PageData;
import io.github.yuandonghao.yarch.common.web.RestResponse;
import io.github.yuandonghao.yarch.web.PageQuery;
import io.github.yuandonghao.yarch.examples.ddd.api.dto.CreateProductRequest;
import io.github.yuandonghao.yarch.examples.ddd.api.dto.ProductResponse;
import io.github.yuandonghao.yarch.examples.ddd.application.service.ProductApplicationService;
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

    private final ProductApplicationService products;

    public ProductController(ProductApplicationService products) {
        this.products = products;
    }

    @PostMapping
    public ResponseEntity<RestResponse<ProductResponse>> create(
            @Valid @RequestBody CreateProductRequest request) {
        var created = products.create(request.name(), request.priceCents(), request.stock());
        return ResponseEntity.created(URI.create("/api/v1/products/" + created.id()))
                .body(RestResponse.ok(ProductResponse.from(created)));
    }

    /** cache-aside 读取（第二次请求命中缓存，可观察 ndjson 日志与 Redis key） */
    @GetMapping
    public RestResponse<PageData<ProductResponse>> list(@Valid PageQuery query) {
        var pageData = products.page(query.getPage(), query.getPageSize());
        return RestResponse.ok(PageData.of(
                pageData.getList().stream().map(ProductResponse::from).toList(),
                pageData.getTotal(), pageData.getPage(), pageData.getPageSize()));
    }

    @GetMapping("/{productId}")
    public RestResponse<ProductResponse> get(@PathVariable Long productId) {
        return RestResponse.ok(ProductResponse.from(products.requireById(productId)));
    }
}
