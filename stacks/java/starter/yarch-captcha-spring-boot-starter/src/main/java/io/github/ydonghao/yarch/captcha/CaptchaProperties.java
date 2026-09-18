package io.github.ydonghao.yarch.captcha;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** 装配配置（contract/api/captcha.md 三-2/四-2/二-4）。租户级覆盖是宿主应用层扩展位（四-3），本配置面到应用级为止。 */
@ConfigurationProperties("yarch.captcha")
public class CaptchaProperties {

    /** 默认 Provider（四-2，缺省 image）；未配置场景一律走此档 */
    private String defaultProvider = "image";

    /** scene → Provider 覆盖映射（四-2）；scene 为宿主自定 kebab-case，yarch 不拥有场景枚举 */
    private Map<String, String> scenes = new LinkedHashMap<>();

    /** challenge TTL（三-2【推荐】120s；OTP 档可放宽至 300s） */
    private Duration ttl = Duration.ofSeconds(120);

    private final Turnstile turnstile = new Turnstile();

    public String getDefaultProvider() {
        return defaultProvider;
    }

    public void setDefaultProvider(String defaultProvider) {
        this.defaultProvider = defaultProvider;
    }

    public Map<String, String> getScenes() {
        return scenes;
    }

    public void setScenes(Map<String, String> scenes) {
        this.scenes = scenes;
    }

    public Duration getTtl() {
        return ttl;
    }

    public void setTtl(Duration ttl) {
        this.ttl = ttl;
    }

    public Turnstile getTurnstile() {
        return turnstile;
    }

    /** turnstile 档接入配置（二-4）：secret-key 配置存在时该档才装配 */
    public static class Turnstile {

        private String siteKey = "";

        private String secretKey = "";

        private String verifyUrl = "https://challenges.cloudflare.com/turnstile/v0/siteverify";

        public String getSiteKey() {
            return siteKey;
        }

        public void setSiteKey(String siteKey) {
            this.siteKey = siteKey;
        }

        public String getSecretKey() {
            return secretKey;
        }

        public void setSecretKey(String secretKey) {
            this.secretKey = secretKey;
        }

        public String getVerifyUrl() {
            return verifyUrl;
        }

        public void setVerifyUrl(String verifyUrl) {
            this.verifyUrl = verifyUrl;
        }
    }
}
