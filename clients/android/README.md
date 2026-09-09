# android/

Android 原生客户端落位（规约：[../../contract/clients/android.md](../../contract/clients/android.md)，前置 [client-shared](../../contract/clients/client-shared.md)）：

- **栈**：Kotlin + Jetpack Compose + Material 3；UDF / ViewModel / Repository；DI = Hilt（M5）。
- **契约内核**：`yarch-client-android`（Kotlin 库）——信封解包 / 错误三分类 / traceId 注入，发 Maven Central（复用 java 栈发版基础设施：GPG keyid 2AD37B86、CI secrets）。
- **工程范式**：version catalog + `build-logic/` convention plugins（`yarch-android-*` 四件）+ `:app / :core:* / :feature:*` 模块分组（Now in Android 范式）。
- **双基线模板**：`minsdk26`（默认）/ `minsdk24`（扩展，26+ API 强制 guard + desugaring）分文件夹；targetSdk ≥ 36 硬线（Play 2026-08-31 起）。
- **一行命令**：`yarch init android <app名>`（App 名按 registry 五节登记）。
