package io.github.yuandonghao.yarch.examples.ddd.domain.repository;

import io.github.yuandonghao.yarch.examples.ddd.domain.model.Product;
import java.util.Optional;

public interface ProductRepository {

    Product save(Product product);

    Optional<Product> findById(Long id);
}
