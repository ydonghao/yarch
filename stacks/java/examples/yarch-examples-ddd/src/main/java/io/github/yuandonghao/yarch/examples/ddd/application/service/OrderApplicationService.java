package io.github.yuandonghao.yarch.examples.ddd.application.service;

import io.github.yuandonghao.yarch.common.code.BusinessException;
import io.github.yuandonghao.yarch.examples.ddd.domain.model.Order;
import io.github.yuandonghao.yarch.examples.ddd.domain.repository.OrderRepository;
import io.github.yuandonghao.yarch.examples.ddd.domain.service.OrderDomainService;
import io.github.yuandonghao.yarch.examples.ddd.types.errno.ExampleErrorCode;
import io.github.yuandonghao.yarch.redis.LockClient;
import io.github.yuandonghao.yarch.redis.RedisKeys;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * 订单应用服务：幂等下单 = 分布式锁（互斥）+ 存储层条件扣减（最终防线）+ API 层 Idempotency-Key（回放）。 三层幂等对应 rest-conventions.md
 * 幂等总则-2 的兜底次序。
 */
@Service
public class OrderApplicationService {

    private final OrderRepository orders;
    private final ProductApplicationService products;
    private final LockClient lockClient;
    private final OrderDomainService domainService = new OrderDomainService();

    public OrderApplicationService(
            OrderRepository orders, ProductApplicationService products, LockClient lockClient) {
        this.orders = orders;
        this.products = products;
        this.lockClient = lockClient;
    }

    public Order place(Long productId, String buyerEmail, int quantity) {
        String lockKey = RedisKeys.of("yarch-examples-ddd").parts("lock", "product", productId);
        return lockClient
                .tryAcquire(lockKey, Duration.ofSeconds(5), Duration.ofSeconds(3))
                .map(
                        token -> {
                            try {
                                Order order =
                                        domainService.place(
                                                products.requireById(productId),
                                                buyerEmail,
                                                quantity);
                                products.deductStock(productId, quantity);
                                return orders.save(order);
                            } finally {
                                lockClient.release(lockKey, token);
                            }
                        })
                .orElseThrow(
                        () ->
                                new BusinessException(
                                        ExampleErrorCode.STOCK_INSUFFICIENT, "并发下单锁竞争超时"));
    }

    /** keyset 通道列表（深分页/增量拉取场景） */
    public List<Order> pageAfter(Optional<String> cursor, int pageSize) {
        Long lastId = cursor.filter(s -> !s.isBlank()).map(Long::valueOf).orElse(null);
        return orders.pageAfter(lastId, pageSize);
    }

    /** 状态机转移（E5）：领域规则在 Order，本层只编排 */
    public Order pay(Long id) {
        return orders.save(requireById(id).pay());
    }

    public Order cancel(Long id) {
        return orders.save(requireById(id).cancel());
    }

    public Order requireById(Long id) {
        return orders.findById(id)
                .orElseThrow(
                        () ->
                                new BusinessException(
                                        ExampleErrorCode.ORDER_NOT_FOUND, String.valueOf(id)));
    }
}
