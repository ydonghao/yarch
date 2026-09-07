package io.github.ydonghao.yarch.examples.ddd.api.controller;

import io.github.ydonghao.yarch.auth.AuthContext;
import io.github.ydonghao.yarch.auth.RequireRoles;
import io.github.ydonghao.yarch.common.web.RestResponse;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 鉴权机制件演示（E3）：Bearer + 角色——账号体系归业务仓，这里只演 2xxx 映射链路 */
@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {

    @GetMapping("/whoami")
    @RequireRoles({"admin"})
    public RestResponse<Map<String, Object>> whoami() {
        return RestResponse.ok(Map.of("subject", String.valueOf(AuthContext.currentSubject())));
    }
}
