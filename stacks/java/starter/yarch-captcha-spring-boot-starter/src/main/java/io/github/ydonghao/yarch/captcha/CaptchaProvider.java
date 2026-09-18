package io.github.ydonghao.yarch.captcha;

import java.util.Map;

/**
 * 验证码 Provider SPI（contract/api/captcha.md 一-2/二）：渠道差异是框架内多态，实现只管「生成挑战」与「答案比对/远程校验」；
 * 生命周期（key/存储/TTL/一次性原子消费）、场景路由、错误语义归框架核心 {@link CaptchaService}。
 *
 * <p>保留字 id（一-3）：{@code image} / {@code sms-otp} / {@code turnstile}。
 */
public interface CaptchaProvider {

    /** Provider 标识（kebab-case，保留字见类注释） */
    String id();

    /** 校验模式（二-1）：LOCAL=答案由框架核心托管、一次性原子消费；REMOTE=远程校验、无服务端答案存储 */
    VerifyMode mode();

    /**
     * 生成分发（LOCAL：产出机密答案 + 公开载体；REMOTE：答案恒 null、载体为接入配置透出）。
     *
     * @param request 分发请求（场景 + 可选发送目标，sms-otp 档必填目标）
     */
    IssuedChallenge issue(IssueRequest request);

    /** LOCAL 档答案比对规则。默认 = 首尾空白 trim + 大小写不敏感（二-2 image 档口径）； 精确档（二-3 sms-otp）覆写。 */
    default boolean matches(String storedAnswer, String attempted) {
        return storedAnswer != null
                && storedAnswer.equalsIgnoreCase(attempted == null ? "" : attempted.trim());
    }

    /** REMOTE 档远程校验（二-1）；LOCAL 档实现可不覆写 */
    default boolean verifyRemote(String token, String remoteIp) {
        throw new UnsupportedOperationException("REMOTE 型 Provider 必须实现 verifyRemote");
    }

    enum VerifyMode {
        LOCAL,
        REMOTE
    }

    /** 分发请求：scene 为路由场景（四-1，可 null）；destination 为发送目标（sms-otp 档必填） */
    record IssueRequest(String scene, String destination) {}

    /**
     * 分发产物：secretAnswer 为机密答案（LOCAL 档进 Redis，由框架核心托管；REMOTE 档恒 null）； payload 为公开载体（image 的
     * imageBase64 / turnstile 的 siteKey / sms-otp 的脱敏目标）。
     */
    record IssuedChallenge(String secretAnswer, Map<String, String> payload) {}
}
