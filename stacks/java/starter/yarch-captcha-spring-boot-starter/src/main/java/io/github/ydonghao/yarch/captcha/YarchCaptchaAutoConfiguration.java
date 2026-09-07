package io.github.ydonghao.yarch.captcha;

import io.github.ydonghao.yarch.common.web.RestResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** 验证码装配：服务 + 可选标准入口（yarch.captcha.endpoint-enabled=false 关闭） */
@AutoConfiguration
public class YarchCaptchaAutoConfiguration {

    @Bean
    public CaptchaService captchaService(
            StringRedisTemplate redis,
            @Value("${spring.application.name:unknown-service}") String serviceName) {
        return new CaptchaService(redis, serviceName);
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

        @GetMapping("/api/v1/captcha")
        public RestResponse<CaptchaService.Captcha> captcha() {
            return RestResponse.ok(captchaService.generate());
        }
    }
}
