# AGENTS.md — {{packageName}} 工程守则

> 本工程由 yarch yarch-init-app（miniprogram）生成，契约唯一权威 = yarch 仓 contract/（clients/miniprogram.md + client-shared.md + api 四件套）。
> 任何 AI 编码代理改码前先读本文件；条文与直觉冲突时以契约为准，红线改动直接拒绝——机检与 CR 均按此执行。

## 工程地图

| 目录 | 职责 |
|---|---|
| miniprogram/pages | 主包页面：登录 / 订单（取数走契约客户端 hooks） |
| miniprogram/subpackages | 分包（profile 等）——主包 2MB 硬约束，新页面优先入分包 |
| miniprogram/app.* | 全局：登录态 / 事件总线 / 主题 |
| scripts/ci.js | miniprogram-ci 发布管线（预览 / 上传） |

## 命令表

| 场景 | 命令 |
|---|---|
| 验证（改完必跑） | npm install && npx tsc --noEmit |
| 构建 npm | 微信开发者工具 → 工具 → 构建 npm（packNpm，装新依赖后必跑） |
| 发布 | node scripts/ci.js（miniprogram-ci，AppID 私钥环境变量） |

## 红线清单

| 红线 | 规则 | 出处 |
|---|---|---|
| 契约内核单点 | 网络请求一律经 @yarch/contract 客户端（wx.request 适配器注入）；**禁页面直调 wx.request 业务接口** | miniprogram.md 三 |
| 埋点与长连接 | 能力由内核 telemetry 模块单点提供（onHide flush 纪律） | client-shared 六/七 |
| 包体积 | 主包 2MB / 整包 30MB；新页面优先分包，图片走 CDN 不入包 | miniprogram.md 四 |
| setData | 最小化 diff 传输，禁整对象回传（性能评分口径 ≥90） | miniprogram.md |
| 发布 | 只走 miniprogram-ci 管线；体验评分达标再发 | miniprogram.md 五 |
| 命名 | 主体类型与 AppID 登记一致（registry 六节）；改类型 = 换 AppID = 新登记 | registry.md |

## 契约锚点

yarch 仓 contract/clients/miniprogram.md · client-shared.md · contract/api/——实现与本仓不一致 = 实现 bug。
