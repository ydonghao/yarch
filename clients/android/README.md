# android/

Android 原生客户端落位（规约：[../../contract/clients/android.md](../../contract/clients/android.md)，前置 [client-shared](../../contract/clients/client-shared.md)；施工计划：[../PLAN.md](../PLAN.md)）。

## yarch-client-android（第一批已交付）

纯 Kotlin/JVM 契约内核库——信封解包 / 错误三分类 / traceId 注入 / 超时单点 / 401 单点刷新重放，零 Android API（无 SDK 环境可构建测试）。

- **坐标**：`io.github.ydonghao.yarch:yarch-client-android`（发版走 Central，复用 java 栈发版设施；tag `clients/android/vX.Y.Z`）
- **依赖**：OkHttp + Retrofit（方言表锁定）+ kotlinx-serialization；版本收敛在 `gradle/libs.versions.toml`
- **API 面**：`model.RestResponse/Page` · `error.ApiError/NetworkError(-1)` · `http.{HttpConfig, TraceIdInterceptor, AuthInterceptor, RefreshAuthenticator, Envelope, YarchHttp}`
- **构建**：`./gradlew test`（JDK 17+；wrapper 下载慢时可直接用本机 gradle 同版本）

```
Envelope.call { api.users(1) }   // 业务代码唯一入口：成功给 data，失败给 ApiError/NetworkError，取消原生传导
```

## 后续批次（见 PLAN）

- 第二批：`minsdk26/24` 双基线模板（build-logic 四 convention plugins + `:app/:core:*/:feature:*` 骨架 + ktlint/detekt/Lint 预接线；验证走 CI 装 cmdline-tools）
- 第三批：`yarch init app --platform android|both`（clients/create 统一生成器）
