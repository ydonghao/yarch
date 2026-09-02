package io.github.yuandonghao.yarch.logging;

import java.util.Properties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.PropertiesPropertySource;

/**
 * 默认 ndjson 日志配置注入：应用未显式配置 logging.config 且 classpath 无 logback*.xml 时， 指向 starter 自带的
 * yarch/yarch-logback.xml——开箱即得契约行协议，用户自有配置优先。
 */
public class YarchLoggingEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    static final String PROPERTY = "logging.config";
    static final String DEFAULT_CONFIG = "classpath:yarch/yarch-logback.xml";

    @Override
    public void postProcessEnvironment(
            ConfigurableEnvironment environment, SpringApplication application) {
        if (environment.containsProperty(PROPERTY)) {
            return; // 用户显式配置优先
        }
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        for (String userConfig : new String[] {"logback-spring.xml", "logback.xml"}) {
            if (classLoader.getResource(userConfig) != null) {
                return; // 用户自有 logback 配置优先
            }
        }
        Properties defaults = new Properties();
        defaults.setProperty(PROPERTY, DEFAULT_CONFIG);
        environment
                .getPropertySources()
                .addFirst(new PropertiesPropertySource("yarch-logging", defaults));
    }

    @Override
    public int getOrder() {
        // 需在 logging 初始化读取 logging.config 之前生效
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }
}
