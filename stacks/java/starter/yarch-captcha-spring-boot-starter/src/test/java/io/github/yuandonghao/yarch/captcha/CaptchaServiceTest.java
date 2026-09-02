package io.github.yuandonghao.yarch.captcha;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.github.yuandonghao.yarch.test.containers.RedisTestDb;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** E2 验收：生成（base64+TTL）、一次性校验（对/错均消费）、大小写不敏感 */
@SpringBootTest(
        classes = CaptchaServiceTest.App.class,
        properties = "yarch.captcha.endpoint-enabled=false")
class CaptchaServiceTest {

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class App {}

    static final RedisTestDb redis = RedisTestDb.dockerAvailable() ? RedisTestDb.start() : null;

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        if (redis != null) {
            redis.register(registry);
        }
    }

    @BeforeAll
    static void requireDocker() {
        assumeTrue(redis != null, "本机无 Docker，跳过验证码契约测试");
    }

    @Autowired CaptchaService captchaService;

    @Test
    void generatesImageWithTtlAndOneShotVerify() {
        CaptchaService.Captcha captcha = captchaService.generate();
        assertNotNull(captcha.key());
        assertTrue(captcha.imageBase64().length() > 100, "PNG base64 应非空");
        assertTrue(captchaService.ttlSeconds(captcha.key()) > 0, "答案须带 TTL");

        String answer = captchaService.peek(captcha.key());
        assertTrue(captchaService.verify(captcha.key(), answer.toLowerCase()), "大小写不敏感");
        assertFalse(captchaService.verify(captcha.key(), answer), "一次性：第二次必败");

        CaptchaService.Captcha other = captchaService.generate();
        assertNotNull(captchaService.peek(other.key()));
        assertFalse(captchaService.verify(other.key(), "XXXX"), "错答案验证失败（且已消费）");
    }
}
