package io.github.ydonghao.yarch.web.security;

import java.util.HashMap;
import java.util.Map;

/** 签名密钥配置：yarch.security.sign.apps.&lt;appKey&gt;=&lt;secret&gt; */
public class SignatureProperties {

    private Map<String, String> apps = new HashMap<>();

    public Map<String, String> apps() {
        return apps;
    }

    public void setApps(Map<String, String> apps) {
        this.apps = apps;
    }
}
