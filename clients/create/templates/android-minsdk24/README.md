# {{packageName}}（yarch Android 模板 · minsdk24 扩展档）

yarch Android 工程模板（[contract/clients/android.md](../../../../contract/clients/android.md) 全条文载体；本档 minSdk 24（26+ API 强制 Build.VERSION guard + desugaring），默认档见 `../android-minsdk26/`）。

## 结构（Now in Android 范式）

```
:app                 # 组装壳（禁业务逻辑）：入口/导航/base URL 注入
:core:common         # Dispatchers 注入等横切
:core:designsystem   # 设计 token 唯一落点（色值字面量只许在此）
:core:network        # 契约内核接线：YarchHttp 装配 + SampleApi + TokenStore
:feature:login       # UDF 样板：UiState + 一次性事件 + mock Repository
:feature:users       # 信封消费样板：Envelope.call + ApiError/NetworkError 分流
build-logic/         # yarch.android.{application,library,compose,hilt} 四 convention 插件
```

## 起步

```bash
./gradlew :app:assembleDebug     # 构建（需 Android SDK：platforms;android-36 + build-tools）
./gradlew test                   # JVM 单测（JUnit5，convention 已开 useJUnitPlatform）
```

- 版本唯一收敛 `gradle/libs.versions.toml`；共享构建配置唯一在 `build-logic/`（模块 build.gradle.kts 只 apply + 依赖）。
- `settings.gradle.kts` 的 `includeBuild("{{yarchClientPath}}")` 为发版前本地源码消费（对偶 golang replace 行）；yarch-client-android 上 Central 后删该行，依赖改正式版本。
- 机检三重：ktlint（`.editorconfig` 定 `android_studio` code style）/ detekt（`config/detekt/detekt.yml`）/ Android Lint（fatal 即败）。

## 与 minsdk24 档的差异

| 项 | 本档（26 默认） | minsdk24（扩展） |
|---|---|---|
| minSdk | 26 | 24 |
| 26+ API 调用 | 直接使用 | `Build.VERSION` guard 强制（lint NewApi fatal） |
| core library desugaring | 关 | 开（`java.time` 等下探） |
