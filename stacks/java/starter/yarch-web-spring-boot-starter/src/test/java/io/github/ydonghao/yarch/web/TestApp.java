package io.github.ydonghao.yarch.web;

import io.github.ydonghao.yarch.common.code.BusinessException;
import io.github.ydonghao.yarch.common.code.GlobalErrorCode;
import io.github.ydonghao.yarch.common.web.PageData;
import io.github.ydonghao.yarch.common.web.RestResponse;
import io.github.ydonghao.yarch.web.idempotency.Idempotent;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 契约测试用最小应用：覆盖信封、错误矩阵、分页校验、幂等 */
@SpringBootConfiguration
@EnableAutoConfiguration
@RestController
public class TestApp {

    final AtomicLong seq = new AtomicLong();
    final Map<Long, String> items = new ConcurrentHashMap<>();

    public record CreateItemRequest(@NotBlank String name) {}

    public record Item(long id, String name) {}

    @PostMapping("/api/v1/items")
    public ResponseEntity<RestResponse<Item>> create(
            @Valid @RequestBody CreateItemRequest request) {
        long id = seq.incrementAndGet();
        items.put(id, request.name());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(RestResponse.ok(new Item(id, request.name())));
    }

    @PostMapping("/api/v1/orders")
    @Idempotent(resource = "orders")
    public ResponseEntity<RestResponse<Item>> createOrder(
            @Valid @RequestBody CreateItemRequest request) {
        long id = seq.incrementAndGet();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(RestResponse.ok(new Item(id, request.name())));
    }

    @GetMapping("/api/v1/items")
    public RestResponse<PageData<Item>> list(@Valid PageQuery query) {
        List<Item> page =
                items.entrySet().stream()
                        .skip(query.offset())
                        .limit(query.getPageSize())
                        .map(e -> new Item(e.getKey(), e.getValue()))
                        .toList();
        return RestResponse.ok(
                PageData.of(page, items.size(), query.getPage(), query.getPageSize()));
    }

    @GetMapping("/api/v1/items/{itemId}")
    public RestResponse<Item> get(@PathVariable long itemId) {
        String name = items.get(itemId);
        if (name == null) {
            throw new BusinessException(GlobalErrorCode.NOT_FOUND, "item " + itemId);
        }
        return RestResponse.ok(new Item(itemId, name));
    }

    @DeleteMapping("/api/v1/items/{itemId}")
    public RestResponse<Void> delete(@PathVariable long itemId) {
        items.remove(itemId);
        return RestResponse.ok();
    }

    @org.springframework.web.bind.annotation.PostMapping("/api/v1/burst")
    @io.github.ydonghao.yarch.web.ratelimit.RateLimited(
            key = "burst",
            permits = 3,
            windowSeconds = 10)
    public RestResponse<Void> burst() {
        return RestResponse.ok();
    }

    @org.springframework.web.bind.annotation.PostMapping("/api/v1/audit")
    @io.github.ydonghao.yarch.web.operlog.OperationLog(action = "test.audit")
    public RestResponse<Void> audit(
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "false")
                    boolean fail) {
        if (fail) {
            throw new IllegalStateException("boom");
        }
        return RestResponse.ok();
    }

    @org.springframework.web.bind.annotation.PostMapping("/api/v1/secure-data")
    @io.github.ydonghao.yarch.web.security.SignedApi
    public RestResponse<Map<String, Object>> secureData(@RequestBody CreateItemRequest request) {
        return RestResponse.ok(Map.of("name", request.name()));
    }

    @GetMapping("/api/v1/boom")
    public RestResponse<Void> boom() {
        throw new IllegalStateException("内部细节不得外泄");
    }

    @GetMapping("/api/v1/echo")
    public RestResponse<Map<String, Object>> echo(@RequestParam(defaultValue = "x") String q) {
        return RestResponse.ok(Map.of("q", q));
    }
}
