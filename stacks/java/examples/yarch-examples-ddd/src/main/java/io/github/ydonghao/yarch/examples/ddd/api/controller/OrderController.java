package io.github.ydonghao.yarch.examples.ddd.api.controller;

import io.github.ydonghao.yarch.common.web.RestResponse;
import io.github.ydonghao.yarch.examples.ddd.api.dto.CreateOrderRequest;
import io.github.ydonghao.yarch.examples.ddd.api.dto.OrderResponse;
import io.github.ydonghao.yarch.examples.ddd.application.service.OrderApplicationService;
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

    private final OrderApplicationService orders;

    public OrderController(OrderApplicationService orders) {
        this.orders = orders;
    }

    /** 幂等下单：同键同参回放原响应（库存只扣一次）；操作日志切面（G2） */
    @PostMapping
    @Idempotent(resource = "orders")
    @io.github.ydonghao.yarch.web.operlog.OperationLog(action = "order.place")
    public ResponseEntity<RestResponse<OrderResponse>> place(
            @Valid @RequestBody CreateOrderRequest request) {
        var order = orders.place(request.productId(), request.buyerEmail(), request.quantity());
        return ResponseEntity.created(URI.create("/api/v1/orders/" + order.id()))
                .body(RestResponse.ok(OrderResponse.from(order)));
    }

    /** keyset 通道：?cursor=&pageSize=（与页码通道并存，深翻页/增量拉取必须走这里） */
    @GetMapping
    public RestResponse<io.github.ydonghao.yarch.common.web.PageData<OrderResponse>> list(
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int pageSize) {
        int size = Math.min(Math.max(pageSize, 1), 100);
        var rows = orders.pageAfter(java.util.Optional.ofNullable(cursor), size);
        var dtos = rows.stream().map(OrderResponse::from).toList();
        return RestResponse.ok(
                io.github.ydonghao.yarch.persistence.support.PageDatas.ofKeyset(
                        dtos, size, r -> String.valueOf(r.id())));
    }

    /** 状态机演示（E5）：pending → paid / cancelled；非法迁移 409+3004 */
    @io.github.ydonghao.yarch.web.operlog.OperationLog(action = "order.pay")
    @PostMapping("/{orderId}/pay")
    public RestResponse<OrderResponse> pay(@PathVariable Long orderId) {
        return RestResponse.ok(OrderResponse.from(orders.pay(orderId)));
    }

    @io.github.ydonghao.yarch.web.operlog.OperationLog(action = "order.cancel")
    @PostMapping("/{orderId}/cancel")
    public RestResponse<OrderResponse> cancel(@PathVariable Long orderId) {
        return RestResponse.ok(OrderResponse.from(orders.cancel(orderId)));
    }

    /** 限流演示（G1）：窗口内超出 permits → 429 + 1006 */
    @PostMapping("/probe")
    @io.github.ydonghao.yarch.web.ratelimit.RateLimited(
            key = "order-probe",
            permits = 5,
            windowSeconds = 10)
    public RestResponse<Void> probe() {
        return RestResponse.ok();
    }

    @GetMapping("/{orderId}")
    public RestResponse<OrderResponse> get(@PathVariable Long orderId) {
        return RestResponse.ok(OrderResponse.from(orders.requireById(orderId)));
    }
}
