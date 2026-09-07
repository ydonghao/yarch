package io.github.ydonghao.yarch.examples.ddd.domain.repository;

import io.github.ydonghao.yarch.examples.ddd.domain.model.Product;
import java.util.Optional;

/** 缓存端口：domain/application 只见接口，Redis 实现在 infrastructure（依赖倒置） */
public interface ProductCache {

    Optional<Product> find(Long productId);

    void put(Product product);

    void evict(Long productId);
}
