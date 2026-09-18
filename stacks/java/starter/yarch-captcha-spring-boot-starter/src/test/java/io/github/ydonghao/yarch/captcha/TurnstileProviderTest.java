package io.github.ydonghao.yarch.captcha;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

/** V9：turnstile 档（REMOTE）——stub siteverify 通过/失败两分支；全程零服务端答案存储（二-4）。 */
class TurnstileProviderTest {

    private final CaptchaProperties.Turnstile props = new CaptchaProperties.Turnstile();

    @Test
    void v9_issueExposesSiteKeyWithoutServerSideAnswer() {
        props.setSiteKey("site-key-1");
        TurnstileProvider provider = new TurnstileProvider(props, (u, s, r, ip) -> true);

        CaptchaProvider.IssuedChallenge issued =
                provider.issue(new CaptchaProvider.IssueRequest(null, null));
        assertNull(issued.secretAnswer(), "REMOTE 档无服务端答案（二-1）");
        assertEquals(Map.of("siteKey", "site-key-1"), issued.payload());
        assertEquals(CaptchaProvider.VerifyMode.REMOTE, provider.mode());
    }

    @Test
    void v9_verifyDelegatesToCaller() {
        TurnstileProvider pass = new TurnstileProvider(props, (u, s, r, ip) -> true);
        TurnstileProvider fail = new TurnstileProvider(props, (u, s, r, ip) -> false);
        assertTrue(pass.verifyRemote("tok", "10.0.0.1"));
        assertFalse(fail.verifyRemote("tok", "10.0.0.1"));
    }

    @Test
    void v9_blankTokenFails() {
        TurnstileProvider provider = new TurnstileProvider(props, (u, s, r, ip) -> true);
        assertFalse(provider.verifyRemote(null, null));
        assertFalse(provider.verifyRemote("  ", null));
    }

    @Test
    void v9_upstreamUnreachableFailsClosed() {
        props.setVerifyUrl("http://127.0.0.1:1/siteverify"); // 不可达端点
        TurnstileProvider provider = new TurnstileProvider(props);
        assertFalse(provider.verifyRemote("tok", null), "上游不可达一律 false（fail closed）");
    }
}
