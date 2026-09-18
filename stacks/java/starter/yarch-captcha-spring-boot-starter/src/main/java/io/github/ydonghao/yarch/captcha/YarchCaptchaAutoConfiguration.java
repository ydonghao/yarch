package io.github.ydonghao.yarch.captcha;

import io.github.ydonghao.yarch.common.web.RestResponse;
import io.github.ydonghao.yarch.web.ratelimit.RateLimited;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 验证码装配（contract/api/captcha.md）：三档 Provider + 框架核心 + 可选标准入口（yarch.captcha.endpoint-enabled=false
 * 关闭）
 */
@AutoConfiguration
@EnableConfigurationProperties(CaptchaProperties.class)
public class YarchCaptchaAutoConfiguration {

    @Bean
    public ImageCaptchaProvider imageCaptchaProvider() {
        return new ImageCaptchaProvider();
    }

    /** sms-otp 档：宿主提供 {@link SmsSender} 分发通道 Bean 才装配（二-3） */
    @Bean
    @ConditionalOnBean(SmsSender.class)
    public SmsOtpProvider smsOtpProvider(SmsSender sender) {
        return new SmsOtpProvider(sender);
    }

    /** turnstile 档：接入凭据配置存在才装配（二-4） */
    @Bean
    @ConditionalOnProperty("yarch.captcha.turnstile.secret-key")
    public TurnstileProvider turnstileProvider(CaptchaProperties properties) {
        return new TurnstileProvider(properties.getTurnstile());
    }

    @Bean
    public CaptchaService captchaService(
            StringRedisTemplate redis,
            @Value("${spring.application.name:unknown-service}") String serviceName,
            ObjectProvider<CaptchaProvider> providers,
            CaptchaProperties properties) {
        return new CaptchaService(
                redis, serviceName, providers.orderedStream().toList(), properties);
    }

    @Bean
    @ConditionalOnProperty(
            name = "yarch.captcha.endpoint-enabled",
            havingValue = "true",
            matchIfMissing = true)
    public Object yarchCaptchaEndpoint(CaptchaService captchaService) {
        return new CaptchaController(captchaService);
    }

    @RestController
    static class CaptchaController {

        private final CaptchaService captchaService;

        CaptchaController(CaptchaService captchaService) {
            this.captchaService = captchaService;
        }

        /** 六-1：image 默认档端点；scene 可选路由参数（四）。五-1：默认挂限流（1006，60s/10 次【参考】档） */
        @GetMapping("/api/v1/captcha")
        @RateLimited(key = "captcha", permits = 10, windowSeconds = 60)
        public RestResponse<CaptchaService.Challenge> captcha(
                @RequestParam(name = "scene", required = false) String scene) {
            return RestResponse.ok(captchaService.challenge(scene));
        }
    }
}
