package io.github.ydonghao.yarch.captcha;

import io.github.ydonghao.yarch.common.code.BusinessException;
import io.github.ydonghao.yarch.common.code.GlobalErrorCode;
import java.security.SecureRandom;
import java.util.Map;

/** sms-otp 档（contract/api/captcha.md 二-3，LOCAL）：6 位数字 OTP；分发通道宿主注入 {@link SmsSender}；目标回显脱敏。 */
public class SmsOtpProvider implements CaptchaProvider {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final SmsSender sender;

    public SmsOtpProvider(SmsSender sender) {
        this.sender = sender;
    }

    @Override
    public String id() {
        return "sms-otp";
    }

    @Override
    public VerifyMode mode() {
        return VerifyMode.LOCAL;
    }

    @Override
    public IssuedChallenge issue(IssueRequest request) {
        String destination = request.destination();
        if (destination == null || destination.isBlank()) {
            throw BusinessException.of(GlobalErrorCode.INVALID_ARGUMENT, "sms-otp 档须提供发送目标");
        }
        String otp = String.format("%06d", RANDOM.nextInt(1_000_000));
        sender.send(destination, otp);
        return new IssuedChallenge(otp, Map.of("destination", mask(destination)));
    }

    /** 精确匹配（二-3 禁宽松比对）：仅去首尾空白，无大小写折中 */
    @Override
    public boolean matches(String storedAnswer, String attempted) {
        return storedAnswer != null && attempted != null && storedAnswer.equals(attempted.trim());
    }

    /** 目标脱敏回显（二-3）：≥7 位留前 3 后 4，其余全遮 */
    static String mask(String destination) {
        if (destination.length() < 7) {
            return "****";
        }
        return destination.substring(0, 3)
                + "****"
                + destination.substring(destination.length() - 4);
    }
}
