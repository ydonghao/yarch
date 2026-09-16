# create/

yarch 客户端工程生成器与模板资产（规约 [../../contract/clients/](../../contract/README.md)；施工计划见 [../PLAN.md](../PLAN.md)）。

## 生成器

```bash
# 本仓本地
node clients/create/bin/yarch-init-app.mjs <app名> --platform <miniprogram|game-cocos> --yes --deps file

# 发版后（npm create = npm exec）
npm create @yarch/app@latest <app名> -- --platform miniprogram
```

生成器复用 web 栈 `create-admin.mjs` 引擎范式：readline 交互问答 + `{{var}}` 通用渲染引擎 + archetype.json 变量声明 + 残留占位符扫描兜底 + registry 五/六节校验（android/ios 走五节、miniprogram/game 走六节）。

## 模板资产

| 模板 | 平台 | 状态 |
|---|---|---|
| `templates/miniprogram/` | 微信小程序（原生 + TS + Vant Weapp + 登录/列表走契约全链路） | 已交付（archetype.json + 22 文件；生成器冒烟 ✓） |
| `templates/game-cocos/` | Cocos Creator 游戏（TS + @yarch/contract/cocos 运行时自动检测） | 已交付（archetype.json + 6 文件；生成器冒烟 ✓） |
| `templates/android-minsdk26/` | Android 原生（Kotlin + Compose + M3，NIA 范式） | 已交付（archetype.json + 占位符化 ✓，生成器冒烟 48 文件，本地 + CI generator-smoke ✓） |
| `templates/android-minsdk24/` | Android 扩展档（min24 + desugaring） | 同上 |
| `templates/ios17/` | iOS 原生（Swift + SwiftUI，@Observable） | 已交付（archetype.json + 占位符化 ✓，生成器冒烟 21 文件，本地 + CI generator-smoke ✓） |
| `templates/ios16/` | iOS 扩展档（ObservableObject 降级） | 同上 |

android/ios 模板由生成器从 `{{packageName}}` 派生双端身份（`io.github.ydonghao.<seg>` applicationId = bundle id，`YsaasShop` PascalCase 类名），registry 五节一次登记（both 口径）。
生成器引擎级单测 `bin/create-app.test.mjs`（node:test，4 用例：registry 解析 / 三口径生成零残留 / 五-1 拒绝 / 裸词拒绝）。
发版前消费本仓 `@yarch/contract` 源码（file: 依赖），发版后切版本依赖（^0.3.0）。
