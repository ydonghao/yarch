package io.github.yuandonghao.yarch.http;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;

/** http 契约件装配：预置超时默认的 Builder（原型使用：业务各自 baseUrl 构建） */
@AutoConfiguration
public class YarchHttpAutoConfiguration {

    @Bean
    @ConfigurationProperties("yarch.http")
    public HttpProperties yarchHttpProperties() {
        return new HttpProperties();
    }

    @Bean
    public YarchRestClient.Builder yarchRestClientBuilder(HttpProperties properties) {
        return YarchRestClient.builder()
                .connectTimeout(properties.connectTimeout())
                .readTimeout(properties.readTimeout());
    }
}
