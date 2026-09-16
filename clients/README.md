# clients/ · 交互端

按项目需要生长，不预先铺摊子。各目录只保留定位说明，规约以 [../contract/README.md](../contract/README.md)「客户端规约」节为准（2026-09-09 定稿），实现按节奏落位。

| 目录 | 定位 | 状态 |
|---|---|---|
| `android/` | Android 原生（Kotlin + Compose + M3，Hilt） | 契约内核 yarch-client-android 已交付（16 tests 绿）；模板在 create/templates |
| `ios/` | iOS 原生（Swift + SwiftUI，MVVM + @Observable） | 契约内核 yarch-client-ios（ContractKit）已交付（15 tests 绿）；模板在 create/templates |
| `create/` | 移动端+小程序+游戏端模板资产 + 统一生成器（`yarch-init-app --platform android\|ios\|both\|miniprogram\|game-cocos`） | 生成器已交付（单测 4/4 绿 + 三口径生成冒烟 ✓ + CI generator-smoke）；android/ios 四档 + miniprogram + game-cocos 模板齐备 |
| `miniprogram/` | 微信小程序（原生 + TypeScript；规约 [../contract/clients/miniprogram.md](../contract/clients/miniprogram.md)） | 预留 |
| `game/` | 游戏端（微信小游戏 + H5；渲染层与 web 分开、契约层共享；规约 [../contract/clients/game.md](../contract/clients/game.md)） | 预留 |
| `desktop/` | Tauri（Rust + Web，兼 Rust 练手落点） | 预留 |

接入时同样遵守 [../contract/](../contract/README.md)：RestResponse 解包、错误码语义、X-Trace-Id 透传（条文见 [../contract/clients/client-shared.md](../contract/clients/client-shared.md)）。

> 移动端为原生双轨（2026-09-03 入册拍板；microduck 仅为 device 契约合体验证候选，移动端首消费者是自研 App）。
