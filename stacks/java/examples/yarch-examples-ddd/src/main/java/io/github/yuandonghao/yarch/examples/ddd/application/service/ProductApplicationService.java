package io.github.yuandonghao.yarch.examples.ddd.application.service;

import io.github.yuandonghao.yarch.common.code.BusinessException;
import io.github.yuandonghao.yarch.examples.ddd.domain.model.Product;
import io.github.yuandonghao.yarch.examples.ddd.domain.repository.ProductCache;
import io.github.yuandonghao.yarch.examples.ddd.domain.repository.ProductRepository;
import io.github.yuandonghao.yarch.examples.ddd.types.errno.ExampleErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 商品应用服务：cache-aside 读、写后删缓存（经 domain 端口，不触碰 infra 实现） */
@Service
public class ProductApplicationService {

    private final ProductRepository products;
    private final ProductCache cache;

    public ProductApplicationService(ProductRepository products, ProductCache cache) {
        this.products = products;
        this.cache = cache;
    }

    @Transactional
    public Product create(String name, long priceCents, int stock) {
        Product created = products.save(new Product(null, name, priceCents, stock, null));
        return created;
    }

    /** Cache-Aside：miss 回源回填（单飞由分布式锁保障的写路径互补；读路径本例从简） */
    public Product requireById(Long id) {
        return cache.find(id)
                .orElseGet(
                        () -> {
                            Product loaded =
                                    products.findById(id)
                                            .orElseThrow(
                                                    () ->
                                                            new BusinessException(
                                                                    ExampleErrorCode
                                                                            .PRODUCT_NOT_FOUND,
                                                                    String.valueOf(id)));
                            cache.put(loaded);
                            return loaded;
                        });
    }

    @Transactional
    public Product deductStock(Long productId, int quantity) {
        Product product = requireById(productId);
        if (product.stock() < quantity) {
            throw new BusinessException(
                    ExampleErrorCode.STOCK_INSUFFICIENT,
                    productId + " 剩余 " + product.stock() + " 需 " + quantity);
        }
        Product deducted = products.save(product.deduct(quantity));
        cache.evict(productId); // 先更新 DB 再删除缓存（redis.md 七-1）
        return deducted;
    }
}
