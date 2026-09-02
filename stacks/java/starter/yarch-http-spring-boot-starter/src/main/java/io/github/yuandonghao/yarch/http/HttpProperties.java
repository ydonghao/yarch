package io.github.yuandonghao.yarch.http;

import java.time.Duration;

/** 超时默认值即 A7 契约（远程调用必须超时），可按下游调紧调松、不可关闭 */
public class HttpProperties {

    private Duration connectTimeout = Duration.ofSeconds(1);

    private Duration readTimeout = Duration.ofSeconds(3);

    public Duration connectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(Duration connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public Duration readTimeout() {
        return readTimeout;
    }

    public void setReadTimeout(Duration readTimeout) {
        this.readTimeout = readTimeout;
    }
}
