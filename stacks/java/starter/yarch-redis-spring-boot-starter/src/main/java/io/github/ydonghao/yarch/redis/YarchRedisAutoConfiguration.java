package io.github.ydonghao.yarch.redis;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.github.ydonghao.yarch.web.idempotency.IdempotencyStore;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis 契约件装配：JSON value 序列化（禁 JDK 序列化、支持 java.time 按 ISO-8601 存储）、 分布式锁、web 幂等 SPI 的跨实例实现（在 web 默认
 * InMemory 之前装配以生效）。
 */
@AutoConfiguration(before = io.github.ydonghao.yarch.web.YarchWebAutoConfiguration.class)
@ConditionalOnClass(RedisTemplate.class)
public class YarchRedisAutoConfiguration {

    /**
     * 值序列化：JSON + 默认类型信息（跨实例反序列化回原类型）+ jsr310（Instant 等 ISO-8601， 契约 D4）+ 禁时间戳数字。语言无关可读
     * JSON（redis.md 三-1）。
     */
    static GenericJackson2JsonRedisSerializer jsonValueSerializer() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.activateDefaultTyping(
                mapper.getPolymorphicTypeValidator(),
                ObjectMapper.DefaultTyping.EVERYTHING,
                JsonTypeInfo.As.PROPERTY);
        return new GenericJackson2JsonRedisSerializer(mapper);
    }

    @Bean
    @ConditionalOnMissingBean(name = "yarchRedisTemplate")
    public RedisTemplate<String, Object> yarchRedisTemplate(RedisConnectionFactory factory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        GenericJackson2JsonRedisSerializer valueSerializer = jsonValueSerializer();
        template.setValueSerializer(valueSerializer);
        template.setHashValueSerializer(valueSerializer);
        template.afterPropertiesSet();
        return template;
    }

    @Bean
    @ConditionalOnMissingBean
    public LockClient yarchLockClient(
            org.springframework.data.redis.core.StringRedisTemplate redis) {
        return new LockClient(redis);
    }

    @Bean
    @ConditionalOnClass(IdempotencyStore.class)
    @ConditionalOnMissingBean(IdempotencyStore.class)
    public IdempotencyStore yarchRedisIdempotencyStore(
            org.springframework.data.redis.core.StringRedisTemplate redis) {
        return new RedisIdempotencyStore(redis);
    }
}
