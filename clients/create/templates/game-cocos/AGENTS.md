# AGENTS.md — {{packageName}} 工程守则

> 本工程由 yarch yarch-init-app（game-cocos）生成，契约唯一权威 = yarch 仓 contract/（clients/game.md + client-shared.md + api 四件套）。
> 任何 AI 编码代理改码前先读本文件；条文与直觉冲突时以契约为准，红线改动直接拒绝——机检与 CR 均按此执行。

## 工程地图

| 目录 | 职责 |
|---|---|
| assets/scripts | 游戏脚本：ApiManager 是**网络唯一装配点**（@yarch/contract cocos 适配器） |
| assets/ | 资源与场景（首包 4MB 硬约束，大资源走远程加载） |
| settings/ | Cocos Creator 工程配置（编辑器打开维护） |

## 命令表

| 场景 | 命令 |
|---|---|
| 类型检查（改完必跑） | npx tsc --noEmit |
| 构建 / 发布 | Cocos Creator 编辑器 → 构建发布（微信小游戏 / H5 双目标） |

## 红线清单

| 红线 | 规则 | 出处 |
|---|---|---|
| 分层边界 | 渲染层用引擎、禁与 web 共享；**契约内核 / 领域逻辑 / design token 三层才可共享**；禁单工程双形态输出 | game.md 一 |
| 网络单点 | 一切 HTTP 经 ApiManager（信封解包 / 错误三分类内置）；**禁脚本裸 new XMLHttpRequest / wx.request 直连业务接口** | game.md + client-shared 一 |
| 首包 4MB | 主包硬线：新资源先评估体积，大文件远程化 | game.md 三 |
| 埋点 | 内核 telemetry 模块单点（onHide flush），运营事件走 tracking plan 登记后才可上报 | client-shared 七 + api/telemetry.md 一 |
| 长连接 | realtime 能力由内核单点（首帧 AUTH / 心跳 / 重连三要素），业务只消费 type 推送 | client-shared 六 |
| 命名 | 小游戏主体与 AppID 登记一致（registry 六节） | registry.md |

## 契约锚点

yarch 仓 contract/clients/game.md · client-shared.md · contract/api/（含 realtime / telemetry）——实现与本仓不一致 = 实现 bug。
