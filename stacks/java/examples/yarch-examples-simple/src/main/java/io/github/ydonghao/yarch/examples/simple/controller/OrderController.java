package io.github.ydonghao.yarch.examples.simple.controller;

import io.github.ydonghao.yarch.common.web.PageData;
import io.github.ydonghao.yarch.common.web.RestResponse;
import io.github.ydonghao.yarch.examples.simple.model.dto.Dtos.CreateOrderRequest;
import io.github.ydonghao.yarch.examples.simple.model.dto.Dtos.OrderDTO;
import io.github.ydonghao.yarch.examples.simple.service.OrderService;
import io.github.ydonghao.yarch.web.idempotency.Idempotent;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    @Idempotent(resource = "orders")
    public ResponseEntity<RestResponse<OrderDTO>> place(
            @Valid @RequestBody CreateOrderRequest request) {
        OrderDTO order = orderService.place(request);
        return ResponseEntity.created(URI.create("/api/v1/orders/" + order.id()))
                .body(RestResponse.ok(order));
    }

    @GetMapping
    public RestResponse<PageData<OrderDTO>> list(
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int pageSize) {
        int size = Math.min(Math.max(pageSize, 1), 100);
        return RestResponse.ok(orderService.pageAfter(cursor, size));
    }

    @GetMapping("/{orderId}")
    public RestResponse<OrderDTO> get(@PathVariable Long orderId) {
        return RestResponse.ok(orderService.requireById(orderId));
    }
}
