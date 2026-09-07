package io.github.ydonghao.yarch.examples.simple.service;

import io.github.ydonghao.yarch.common.code.BusinessException;
import io.github.ydonghao.yarch.examples.simple.dao.ProductMapper;
import io.github.ydonghao.yarch.examples.simple.model.ProductDO;
import io.github.ydonghao.yarch.examples.simple.model.dto.Dtos.CreateProductRequest;
import io.github.ydonghao.yarch.examples.simple.model.dto.Dtos.ProductDTO;
import io.github.ydonghao.yarch.examples.simple.types.errno.ExampleErrorCode;
import io.github.ydonghao.yarch.redis.RedisKeys;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Service 层：商品业务 + cache-aside 缓存（redis.md 七） */
@Service
@RequiredArgsConstructor
public class ProductService {

    private static final String SERVICE = "yarch-examples-simple";

    private final ProductMapper productMapper;
    private final RedisTemplate<String, Object> redisTemplate;

    @Transactional
    public ProductDTO create(CreateProductRequest request) {
        ProductDO product = new ProductDO();
        product.setName(request.name());
        product.setPriceCents(request.priceCents());
        product.setStock(request.stock());
        productMapper.insert(product);
        return ProductDTO.from(product);
    }

    public ProductDTO requireById(Long id) {
        ProductDO cached = readCache(id);
        if (cached != null) {
            return ProductDTO.from(cached);
        }
        ProductDO loaded =
                Optional.ofNullable(productMapper.selectById(id))
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                ExampleErrorCode.PRODUCT_NOT_FOUND,
                                                String.valueOf(id)));
        fillCache(loaded);
        return ProductDTO.from(loaded);
    }

    void evict(Long productId) {
        redisTemplate.delete(RedisKeys.of(SERVICE).parts("product", "detail", productId));
    }

    private ProductDO readCache(Long id) {
        Object cached =
                redisTemplate
                        .opsForValue()
                        .get(RedisKeys.of(SERVICE).parts("product", "detail", id));
        return cached instanceof ProductDO product ? product : null;
    }

    private void fillCache(ProductDO product) {
        long ttl = 300 + ThreadLocalRandom.current().nextLong(-30, 30);
        redisTemplate
                .opsForValue()
                .set(
                        RedisKeys.of(SERVICE).parts("product", "detail", product.getId()),
                        product,
                        Duration.ofSeconds(ttl));
    }
}
