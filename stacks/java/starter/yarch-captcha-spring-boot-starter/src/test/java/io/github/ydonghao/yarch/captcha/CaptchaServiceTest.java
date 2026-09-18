package io.github.ydonghao.yarch.captcha;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.github.ydonghao.yarch.common.code.BusinessException;
import io.github.ydonghao.yarch.test.containers.RedisTestDb;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * captcha.md v1.0 跨栈一致性向量（附录 V 系）——java 首批落地；golang/python 触发时同源引用。 覆盖：V1-V5（image
 * 生命周期）、V6（原子消费）、V7（TTL）、V8（sms-otp）、V10（场景路由）+ 存量便捷面。
 */
@SpringBootTest(
        classes = {CaptchaServiceTest.App.class, CaptchaServiceTest.Fakes.class},
        properties = {"yarch.captcha.endpoint-enabled=false", "yarch.captcha.scenes.login=sms-otp"})
class CaptchaServiceTest {

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class App {}

    /** V8：sms-otp 档装配条件——宿主 SmsSender（静态捕获，测试方法间共享，断言用差量） */
    @TestConfiguration
    static class Fakes {
        @Bean
        SmsSender capturingSmsSender() {
            return (destination, content) -> SENT.add(content);
        }
    }

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

    static final List<String> SENT = new CopyOnWriteArrayList<>();

    // ---------- V1-V5：image 档生命周期 ----------

    @Test
    void v1_correctAnswerVerifiesOnce() {
        CaptchaService.Issued issued = captchaService.issue(null, null);
        assertEquals("image", issued.provider());
        String answer = captchaService.peek("image", issued.key());

        assertTrue(captchaService.verify(null, issued.key(), answer.toLowerCase()), "V5：大小写不敏感");
        assertFalse(captchaService.verify(null, issued.key(), answer), "V2：一次性——第二次必败");
    }

    @Test
    void v5_answerWithSurroundingWhitespaceMatches() {
        CaptchaService.Issued issued = captchaService.issue(null, null);
        String answer = captchaService.peek("image", issued.key());
        assertTrue(
                captchaService.verify(null, issued.key(), "  " + answer.toLowerCase() + "  "),
                "V5：trim + 大小写不敏感");
    }

    @Test
    void v3_wrongAnswerConsumesKey() {
        CaptchaService.Issued issued = captchaService.issue(null, null);
        String answer = captchaService.peek("image", issued.key());
        assertFalse(captchaService.verify(null, issued.key(), "XXXX"), "错答案验证失败");
        assertFalse(captchaService.verify(null, issued.key(), answer), "V3：错答案后 key 已消费（防重放枚举）");
    }

    @Test
    void v4_unknownKeyFails() {
        assertFalse(captchaService.verify(null, "00000000-0000-0000-0000-000000000000", "ABCD"));
    }

    @Test
    void v7_ttlWithinDefaultWindow() {
        CaptchaService.Issued issued = captchaService.issue(null, null);
        long ttl = captchaService.ttlSeconds("image", issued.key());
        assertTrue(ttl > 0 && ttl <= 120, "V7：0 < TTL ≤ 120，实际 " + ttl);
    }

    @Test
    void v6_concurrentVerifyExactlyOneWins() throws Exception {
        CaptchaService.Issued issued = captchaService.issue(null, null);
        String answer = captchaService.peek("image", issued.key());
        int threads = 8;
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        List<Boolean> results = new CopyOnWriteArrayList<>();
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        for (int i = 0; i < threads; i++) {
            pool.submit(
                    () -> {
                        try {
                            start.await();
                            results.add(captchaService.verify(null, issued.key(), answer));
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        } finally {
                            done.countDown();
                        }
                    });
        }
        start.countDown();
        assertTrue(done.await(5, TimeUnit.SECONDS));
        pool.shutdownNow();
        assertEquals(
                1,
                results.stream().filter(Boolean::booleanValue).count(),
                "V6：并发校验恰一个成功（GETDEL 原子）");
    }

    // ---------- V8：sms-otp 档 ----------

    @Test
    void v8_smsOtpSendsSixDigitsAndVerifiesExactly() {
        int before = SENT.size();
        CaptchaService.Issued issued = captchaService.issue("login", "13800138000");
        assertEquals("sms-otp", issued.provider(), "V10：scenes.login 映射生效");
        assertEquals(before + 1, SENT.size(), "OTP 已通过宿主 SmsSender 发送");
        String otp = SENT.get(SENT.size() - 1);
        assertTrue(otp.matches("\\d{6}"), "6 位数字 OTP");
        assertEquals("138****8000", issued.payload().get("destination"), "目标脱敏回显");

        assertTrue(captchaService.verify("login", issued.key(), otp), "正确 OTP 通过");
        CaptchaService.Issued second = captchaService.issue("login", "13800138000");
        String secondOtp = SENT.get(SENT.size() - 1);
        String wrong = secondOtp.equals("000000") ? "111111" : "000000";
        assertFalse(captchaService.verify("login", second.key(), wrong), "V8：错码失败（精确比对）");
        assertFalse(captchaService.verify("login", second.key(), secondOtp), "V3：错试后已消费");
    }

    // ---------- V10：场景路由 ----------

    @Test
    void v10_unconfiguredSceneFallsBackToDefault() {
        assertEquals(
                "image", captchaService.issue("register", null).provider(), "未配置场景走默认 Provider");
        assertEquals("image", captchaService.issue(null, null).provider(), "null 场景走默认");
    }

    @Test
    void legacyFacadeStaysOnImage() {
        // 存量便捷面（E2 形态）：语义锁死 image 档，不随路由配置漂移
        CaptchaService.Captcha legacy = captchaService.generate();
        assertNotNull(legacy.key());
        assertTrue(legacy.imageBase64().length() > 100);
        assertTrue(
                captchaService.verify(
                        legacy.key(), captchaService.peek("image", legacy.key()).toLowerCase()));
    }

    @Test
    void challengeRejectsNonImageScene() {
        BusinessException e =
                assertThrows(BusinessException.class, () -> captchaService.challenge("login"));
        assertEquals(1001, e.getErrorCode().code(), "六-1：非 image 档场景经图形端点 = 1001");
    }
}
