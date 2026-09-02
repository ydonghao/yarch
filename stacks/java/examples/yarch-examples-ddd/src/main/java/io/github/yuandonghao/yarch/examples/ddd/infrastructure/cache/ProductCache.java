package io.github.yuandonghao.yarch.examples.ddd.infrastructure.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.yuandonghao.yarch.examples.ddd.domain.model.Product;
import io.github.yuandonghao.yarch.redis.RedisKeys;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 商品缓存（redis.md「缓存一致性」的 Cache-Aside 落地）： 读 miss 回源回填；写路径删除缓存（禁更新缓存）；TTL 带 10% 随机抖动防雪崩（二-5）。 key 形如
 * yarch-examples-ddd:product:detail:{id}——首段=服务名（租户边界）。
 */
@Component
public class ProductCache {

    private static final String SERVICE = "yarch-examples-ddd";
    private static final long TTL_SECONDS = 300;

    private final RedisTemplate<String, Object> redis;
    private final ObjectMapper mapper = new ObjectMapper();

    public ProductCache(RedisTemplate<String, Object> redis) {
        this.redis = redis;
    }

    public Optional<Product> find(Long productId) {
        Object cached = redis.opsForValue().get(key(productId));
        if (cached == null) {
            return Optional.empty();
        }
        try {
            @SuppressWarnings("unchecked")
            var tree = mapper.readTree(mapper.writeValueAsString(cached));
            return Optional.of(
                    new Product(
                            tree.path("id").asLong(),
                            tree.path("name").asText(),
                            tree.path("priceCents").asLong(),
                            tree.path("stock").asInt(),
                            null));
        } catch (Exception e) {
            return Optional.empty(); // 损坏条目按 miss 处理
        }
    }

    public void put(Product product) {
        long jitter = ThreadLocalRandom.current().nextLong(-TTL_SECONDS / 10, TTL_SECONDS / 10);
        redis.opsForValue()
                .set(key(product.id()), product, Duration.ofSeconds(TTL_SECONDS + jitter));
    }

    /** 写路径：先更新 DB，再删除缓存（DEL，禁更新缓存——redis.md 七-1） */
    public void evict(Long productId) {
        redis.delete(key(productId));
    }

    private String key(Long productId) {
        return RedisKeys.of(SERVICE).parts("product", "detail", productId);
    }
}
