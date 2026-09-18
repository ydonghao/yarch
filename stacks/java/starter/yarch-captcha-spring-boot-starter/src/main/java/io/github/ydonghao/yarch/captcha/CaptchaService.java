package io.github.ydonghao.yarch.captcha;

import io.github.ydonghao.yarch.common.code.BusinessException;
import io.github.ydonghao.yarch.common.code.GlobalErrorCode;
import io.github.ydonghao.yarch.redis.RedisKeys;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

/**
 * 框架核心（contract/api/captcha.md 一-2）：challenge 生命周期（生成/存储/TTL/一次性原子消费）、 场景路由（四）、错误语义（五）归此；生成与比对归
 * {@link CaptchaProvider}。
 *
 * <p>存储 key（三-5）：{@code 服务名:captcha:{provider}:{key}}（首段=服务名，共享实例租户边界）。
 */
public class CaptchaService {

    /**
     * GETDEL 等价 Lua（三-4【强制】原子消费）：取答案与删除一次提交，兼容 &lt; Redis 6.2 实例； 两步 get→delete 是缺陷（并发双 verify
     * 竞态双双通过）。
     */
    private static final RedisScript<String> GET_AND_DELETE =
            RedisScript.of(
                    "local v = redis.call('GET', KEYS[1]); redis.call('DEL', KEYS[1]); return v",
                    String.class);

    private final StringRedisTemplate redis;
    private final String serviceName;
    private final Map<String, CaptchaProvider> providers;
    private final CaptchaProperties properties;

    public CaptchaService(
            StringRedisTemplate redis,
            String serviceName,
            List<CaptchaProvider> providers,
            CaptchaProperties properties) {
        this.redis = redis;
        this.serviceName = serviceName;
        this.providers =
                providers.stream()
                        .collect(
                                Collectors.toUnmodifiableMap(
                                        CaptchaProvider::id, Function.identity()));
        this.properties = properties;
    }

    /** REST 端点形状（六-1）：provider/key/imageBase64 恒在，存量字段形态不动（CP10） */
    public record Challenge(String provider, String key, String imageBase64) {}

    /** 编程式分发产物（六-4：sms-otp/turnstile 档首发走编程式，HTTP 端点为触发档） */
    public record Issued(String provider, String key, Map<String, String> payload) {}

    /**
     * 分发（按场景路由，四-2：未配置场景走默认 Provider）。
     *
     * @param scene 场景标识（可 null = 默认档）
     * @param destination 发送目标（sms-otp 档必填，image 档忽略）
     */
    public Issued issue(String scene, String destination) {
        CaptchaProvider provider = route(scene);
        CaptchaProvider.IssuedChallenge challenge =
                provider.issue(new CaptchaProvider.IssueRequest(scene, destination));
        String key = null;
        if (provider.mode() == CaptchaProvider.VerifyMode.LOCAL) {
            // 三-1：key 服务端安全随机（≥128 bit），禁业务可预测值
            key = UUID.randomUUID().toString();
            redis.opsForValue().set(storeKey(provider.id(), key), challenge.secretAnswer(), ttl());
        }
        return new Issued(provider.id(), key, challenge.payload());
    }

    /**
     * 校验（六-2：内联受保护业务流，不设独立端点）。LOCAL = GETDEL 原子消费后按 Provider 比对 （三-3 对/错/异常均消费）；REMOTE =
     * 外委校验（二-1，token 一次性由上游保证）。
     */
    public boolean verify(String scene, String key, String answer) {
        CaptchaProvider provider = route(scene);
        if (provider.mode() == CaptchaProvider.VerifyMode.REMOTE) {
            return provider.verifyRemote(answer, null);
        }
        String stored = redis.execute(GET_AND_DELETE, List.of(storeKey(provider.id(), key)));
        return provider.matches(stored, answer);
    }

    /** REST 端点便捷面（六-1）：GET /api/v1/captcha 只服务 image 档，路由到他档即参数错（1001） */
    public Challenge challenge(String scene) {
        Issued issued = issue(scene, null);
        if (!"image".equals(issued.provider())) {
            throw BusinessException.of(
                    GlobalErrorCode.INVALID_ARGUMENT, "场景 " + scene + " 非 image 档，不经图形分发端点");
        }
        return new Challenge(issued.provider(), issued.key(), issued.payload().get("imageBase64"));
    }

    // ---------- 存量便捷面（E2 形态，存量调用方零改动；语义锁死 image 档，不随默认路由漂移） ----------

    /** 存量图形验证码响应（key + 裸 base64） */
    public record Captcha(String key, String imageBase64) {}

    public Captcha generate() {
        CaptchaProvider image = provider("image");
        CaptchaProvider.IssuedChallenge challenge =
                image.issue(new CaptchaProvider.IssueRequest(null, null));
        String key = UUID.randomUUID().toString();
        redis.opsForValue().set(storeKey("image", key), challenge.secretAnswer(), ttl());
        return new Captcha(key, challenge.payload().get("imageBase64"));
    }

    public boolean verify(String key, String answer) {
        CaptchaProvider image = provider("image");
        String stored = redis.execute(GET_AND_DELETE, List.of(storeKey("image", key)));
        return image.matches(stored, answer);
    }

    // ---------- 内部 ----------

    private CaptchaProvider route(String scene) {
        String providerId =
                properties.getScenes().getOrDefault(scene, properties.getDefaultProvider());
        return provider(providerId);
    }

    private CaptchaProvider provider(String providerId) {
        CaptchaProvider provider = providers.get(providerId);
        if (provider == null) {
            throw new IllegalStateException("未注册的验证码 Provider：" + providerId);
        }
        return provider;
    }

    private Duration ttl() {
        Duration ttl = properties.getTtl();
        return ttl == null || ttl.isNegative() || ttl.isZero() ? Duration.ofSeconds(120) : ttl;
    }

    String storeKey(String providerId, String key) {
        return RedisKeys.of(serviceName).parts("captcha", providerId, key);
    }

    String peek(String providerId, String key) {
        return redis.opsForValue().get(storeKey(providerId, key));
    }

    long ttlSeconds(String providerId, String key) {
        Long ttl = redis.getExpire(storeKey(providerId, key));
        return ttl == null ? -1 : ttl;
    }
}
