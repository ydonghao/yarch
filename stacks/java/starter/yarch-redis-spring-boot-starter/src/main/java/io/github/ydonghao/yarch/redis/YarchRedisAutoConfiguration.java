package io.github.ydonghao.yarch.redis;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.github.ydonghao.yarch.web.idempotency.IdempotencyStore;
import io.github.ydonghao.yarch.web.security.NonceStore;
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
     * 值序列化：JSON + 类型信息（跨实例反序列化回原类型，含 record 等 final 类）+ jsr310（Instant 等 ISO-8601， 契约 D4）+
     * 禁时间戳数字。语言无关可读 JSON（redis.md 三-1）。
     *
     * <p>反序列化白名单：默认只放行 JDK 基础容器/时间/数值类型——Redis 值被污染时不得成为任意类实例化的攻击面 （对齐 redis.md「禁语言原生序列化」的初衷）。业务
     * DTO 的类型化回读需把其包前缀登记到 {@code yarch.redis.json-trusted-packages}（逗号分隔，如 {@code com.mycompany}）。
     *
     * @param trustedPackages 额外信任的包前缀（来自 yarch.redis.json-trusted-packages，可空）
     */
    static GenericJackson2JsonRedisSerializer jsonValueSerializer(String... trustedPackages) {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        BasicPolymorphicTypeValidator.Builder builder =
                BasicPolymorphicTypeValidator.builder()
                        .allowIfSubType("java.util.")
                        .allowIfSubType("java.time.")
                        .allowIfSubType("java.lang.")
                        .allowIfSubType("java.math.");
        for (String pkg : trustedPackages) {
            if (!pkg.isBlank()) {
                builder.allowIfSubType(pkg.trim());
            }
        }
        mapper.activateDefaultTyping(
                builder.build(), ObjectMapper.DefaultTyping.EVERYTHING, JsonTypeInfo.As.PROPERTY);
        return new GenericJackson2JsonRedisSerializer(mapper);
    }

    @Bean
    @ConditionalOnMissingBean(name = "yarchRedisTemplate")
    public RedisTemplate<String, Object> yarchRedisTemplate(
            RedisConnectionFactory factory,
            @org.springframework.beans.factory.annotation.Value(
                            "${yarch.redis.json-trusted-packages:}")
                    String trustedPackages) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        GenericJackson2JsonRedisSerializer valueSerializer =
                jsonValueSerializer(trustedPackages.split(","));
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

    @Bean
    @ConditionalOnClass(NonceStore.class)
    @ConditionalOnMissingBean(NonceStore.class)
    public NonceStore yarchRedisNonceStore(
            org.springframework.data.redis.core.StringRedisTemplate redis) {
        return new RedisNonceStore(redis);
    }
}
