package io.github.ydonghao.yarch.web.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 接口签名（E4 反扒基线）：HMAC-SHA256(method + path + timestamp + nonce + body)， 头部 X-App-Key / X-Timestamp /
 * X-Nonce / X-Sign；时间窗 ±300s + nonce 一次性消费双防线防重放（nonce 存储：默认进程内，引入 yarch-redis starter 后跨实例 Redis
 * SET NX PX）。 密钥配置：yarch.security.sign.apps.&lt;appKey&gt;=&lt;secret&gt;。失败 → 2001（未认证）。
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface SignedApi {}
