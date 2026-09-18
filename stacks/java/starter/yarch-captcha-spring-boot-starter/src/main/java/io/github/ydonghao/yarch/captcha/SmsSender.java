package io.github.ydonghao.yarch.captcha;

/**
 * OTP 分发通道 SPI（contract/api/captcha.md 二-3）：发送通道由宿主注入——yarch 不背通知渠道抽象 （通知领域契约排队中 EP7，届时对齐）。宿主提供本接口
 * Bean 后 sms-otp 档自动装配。
 */
public interface SmsSender {

    /**
     * 发送 OTP 到目标（手机号）。
     *
     * @param destination 发送目标（明文，仅在此处出现；回显侧已由框架脱敏）
     * @param content 6 位数字 OTP
     */
    void send(String destination, String content);
}
