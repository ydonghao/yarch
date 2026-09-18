package io.github.ydonghao.yarch.captcha;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/** turnstile 档（contract/api/captcha.md 二-4，REMOTE）：siteKey 透出 + siteverify 外委校验；token 不落 Redis。 */
public class TurnstileProvider implements CaptchaProvider {

    /** siteverify 调用器（构造注入，测试可打桩） */
    @FunctionalInterface
    public interface SiteVerifyCaller {
        boolean call(String verifyUrl, String secret, String response, String remoteIp);
    }

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final CaptchaProperties.Turnstile props;
    private final SiteVerifyCaller caller;

    public TurnstileProvider(CaptchaProperties.Turnstile props) {
        this(props, TurnstileProvider::siteVerify);
    }

    TurnstileProvider(CaptchaProperties.Turnstile props, SiteVerifyCaller caller) {
        this.props = props;
        this.caller = caller;
    }

    @Override
    public String id() {
        return "turnstile";
    }

    @Override
    public VerifyMode mode() {
        return VerifyMode.REMOTE;
    }

    @Override
    public IssuedChallenge issue(IssueRequest request) {
        // REMOTE 档：无服务端答案（二-1），分发即接入配置透出（二-4：前端嵌 widget 自取 token）
        return new IssuedChallenge(null, Map.of("siteKey", props.getSiteKey()));
    }

    @Override
    public boolean verifyRemote(String token, String remoteIp) {
        if (token == null || token.isBlank()) {
            return false;
        }
        return caller.call(props.getVerifyUrl(), props.getSecretKey(), token, remoteIp);
    }

    /** 默认调用器：POST siteverify（form 编码），解析 {@code {"success":bool}}；上游不可达一律 false（fail closed） */
    static boolean siteVerify(String verifyUrl, String secret, String response, String remoteIp) {
        try {
            StringBuilder form =
                    new StringBuilder("secret=")
                            .append(URLEncoder.encode(secret, StandardCharsets.UTF_8))
                            .append("&response=")
                            .append(URLEncoder.encode(response, StandardCharsets.UTF_8));
            if (remoteIp != null && !remoteIp.isBlank()) {
                form.append("&remoteip=")
                        .append(URLEncoder.encode(remoteIp, StandardCharsets.UTF_8));
            }
            HttpRequest request =
                    HttpRequest.newBuilder(URI.create(verifyUrl))
                            .header("Content-Type", "application/x-www-form-urlencoded")
                            .POST(HttpRequest.BodyPublishers.ofString(form.toString()))
                            .build();
            String body =
                    HttpClient.newHttpClient()
                            .send(request, HttpResponse.BodyHandlers.ofString())
                            .body();
            return MAPPER.readTree(body).path("success").asBoolean(false);
        } catch (Exception e) {
            return false;
        }
    }
}
