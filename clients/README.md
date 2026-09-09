# clients/ · 交互端

按项目需要生长，不预先铺摊子。各目录只保留定位说明，规约以 [../contract/README.md](../contract/README.md)「客户端规约」节为准（2026-09-09 定稿），实现按节奏落位。

| 目录 | 定位 | 状态 |
|---|---|---|
| `android/` | Android 原生（Kotlin + Compose + M3，Hilt；minsdk26 默认 / 24 扩展双档模板） | 规约已定稿，实现启动（自研 App，2026-09-09 起） |
| `ios/` | iOS 原生（Swift + SwiftUI，MVVM + @Observable，xcodegen + SPM；ios17 默认 / 16 扩展双档模板） | 规约已定稿，实现启动（自研 App，2026-09-09 起） |
| `miniprogram/` | 小程序 | 预留 |
| `desktop/` | Tauri（Rust + Web，兼 Rust 练手落点） | 预留 |

接入时同样遵守 [../contract/](../contract/README.md)：RestResponse 解包、错误码语义、X-Trace-Id 透传（条文见 [../contract/clients/client-shared.md](../contract/clients/client-shared.md)）。

> 移动端为原生双轨（2026-09-03 入册拍板；microduck 仅为 device 契约合体验证候选，移动端首消费者是自研 App）。
