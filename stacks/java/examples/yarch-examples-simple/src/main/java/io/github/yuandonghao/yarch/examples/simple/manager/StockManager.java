package io.github.yuandonghao.yarch.examples.simple.manager;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.github.yuandonghao.yarch.common.code.BusinessException;
import io.github.yuandonghao.yarch.examples.simple.dao.ProductMapper;
import io.github.yuandonghao.yarch.examples.simple.model.ProductDO;
import io.github.yuandonghao.yarch.examples.simple.types.errno.ExampleErrorCode;
import io.github.yuandonghao.yarch.redis.LockClient;
import io.github.yuandonghao.yarch.redis.RedisKeys;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Manager 层示例：通用业务下沉（库存扣减是多个 Service 都可能用的能力）。 互斥用分布式锁，最终防线是存储层条件更新（幂等总则-2 的兜底次序）。 */
@Component
@RequiredArgsConstructor
public class StockManager {

    private final ProductMapper productMapper;
    private final LockClient lockClient;

    /** 锁内校验并原子扣减；返回扣减后的商品 */
    public ProductDO deduct(Long productId, int quantity) {
        String lockKey = RedisKeys.of("yarch-examples-simple").parts("lock", "product", productId);
        return lockClient
                .tryAcquire(lockKey, Duration.ofSeconds(5), Duration.ofSeconds(3))
                .map(
                        token -> {
                            try {
                                ProductDO product = productMapper.selectById(productId);
                                if (product == null) {
                                    throw new BusinessException(
                                            ExampleErrorCode.PRODUCT_NOT_FOUND,
                                            String.valueOf(productId));
                                }
                                if (product.getStock() < quantity) {
                                    throw new BusinessException(
                                            ExampleErrorCode.STOCK_INSUFFICIENT,
                                            productId
                                                    + " 剩余 "
                                                    + product.getStock()
                                                    + " 需 "
                                                    + quantity);
                                }
                                int changed =
                                        productMapper.update(
                                                null,
                                                Wrappers.<ProductDO>update()
                                                        .setSql("stock = stock - " + quantity)
                                                        .eq("id", productId)
                                                        .ge("stock", quantity));
                                if (changed != 1) {
                                    throw new BusinessException(
                                            ExampleErrorCode.STOCK_INSUFFICIENT, "并发扣减竞争失败");
                                }
                                product.setStock(product.getStock() - quantity);
                                return product;
                            } finally {
                                lockClient.release(lockKey, token);
                            }
                        })
                .orElseThrow(
                        () ->
                                new BusinessException(
                                        ExampleErrorCode.STOCK_INSUFFICIENT, "并发下单锁竞争超时"));
    }
}
