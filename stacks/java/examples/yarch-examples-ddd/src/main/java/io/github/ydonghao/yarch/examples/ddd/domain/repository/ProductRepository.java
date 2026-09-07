package io.github.ydonghao.yarch.examples.ddd.domain.repository;

import io.github.ydonghao.yarch.common.web.PageData;
import io.github.ydonghao.yarch.examples.ddd.domain.model.Product;
import java.util.Optional;

public interface ProductRepository {

    Product save(Product product);

    Optional<Product> findById(Long id);

    PageData<Product> page(int page, int pageSize);
}
