package io.github.yuandonghao.yarch.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** 认证机制件装配：配置 yarch.auth.secret 后启用（HS256 默认；RS/ES 经扩展点替换 JwtCodec bean） */
@AutoConfiguration
public class YarchAuthAutoConfiguration {

    @Bean
    @ConditionalOnProperty("yarch.auth.secret")
    public JwtCodec jwtCodec(@Value("${yarch.auth.secret}") String secret) {
        return new JwtCodec(secret);
    }

    @Bean
    @ConditionalOnProperty("yarch.auth.secret")
    public WebMvcConfigurer yarchAuthInterceptorConfigurer(JwtCodec jwtCodec) {
        return new WebMvcConfigurer() {
            @Override
            public void addInterceptors(InterceptorRegistry registry) {
                registry.addInterceptor(new AuthInterceptor(jwtCodec));
            }
        };
    }
}
