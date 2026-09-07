package io.github.ydonghao.yarch.web.security;

import java.time.Duration;

/**
 * 签名防重放的一次性 nonce 存储 SPI：web starter 定义，yarch-redis starter 提供 Redis 实现 （SET NX
 * PX，跨实例），默认降级为进程内实现（单实例语义）。业务工程可自行替换。
 */
public interface NonceStore {

    /**
     * 消费 nonce：true = 首见放行；false = TTL 窗口内已消费（重放，拒绝）。
     *
     * @param key 完整 redis key 语义由实现自定（调用方按「服务名:signedapi:nonce:摘要」构建）
     */
    boolean consume(String key, Duration ttl);
}
