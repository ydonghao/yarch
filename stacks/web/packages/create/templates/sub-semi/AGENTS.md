# AGENTS.md — {{packageName}}（微前端子应用）工程守则

> 本工程由 yarch 脚手架生成（`--micro sub`），独立·集成双运行形态（yarch 仓 `contract/web/micro-frontend.md` 十一）。
> 任何 AI 编码代理改码前先读本文件；条文与直觉冲突时以本文件为准，红线改动直接拒绝——机检与 CR 均按此执行。

## 双模式铁律（条文十一-2）

一切运行期差异**只进 `src/supply/` 供给层**（standalone.ts / integrated.ts），业务代码零分支；`vite --mode integrated` 构建时 react 系与 `@yarch/contract` 由基座 window 全局提供（八-1/八-2），产物 `dist-micro/` 里框架体积必须消失。

## 契约红线（违反 = 机检失败 / CR 必拒）

| 红线 | 规则 | 出处 |
|---|---|---|
| 禁载器 | 子应用**禁 import `@micro-zoe/micro-app`**——载器只属基座（depcruise 规则 micro-loader-only-in-base） | 三-2 |
| 导航注册 | `@yarch/react` 只允许在 `src/supply/` 出现（独立模式注册导航）；业务层禁 import（十-2） | 十-2 |
| 禁连基座 | 禁 import 基座工程源码——共享只经基座 window 全局与挂载 props（五-5） | 十二-2 |
| 事件名 | 自定义事件一律 `{应用名}:{动词-名词}`（如 `{{packageName}}:export-done`）；用 `defineAppEvents` 封装，禁裸事件名 | 九-3 |
| storage key | 一律 `{应用名}:` 前缀（`createAppStorage` 已内置）；**禁裸 key 直写 localStorage**（跨应用覆写事故） | 二-2 / 七-3 |
| 登录态 | token 只从 `__YARCH_SUPPLY__.getToken` / 供给层取；**禁自行持久化登录态**（唯一归基座） | 十-1 |
| 路由 | 路由 base = 应用名前缀（供给层注入）；基座 manifest 里声明的每个 menu path 在本工程路由表必须有对应路由 | 四-1 / 四-2 |
| 卸载清理 | unmount 彻底清理（root.unmount / 定时器 / 监听 / 动态 DOM）；重挂幂等，来回切换不崩不白屏 | 五-2 / 五-3 |
| 弹层容器 | Modal/Toast 挂本应用容器（六-3）；React 19 下命令式弹层须注入 `semiGlobal.config.createRoot`（入口已预置，勿删） | 六-3 |

## 验证（改完必跑）

```bash
pnpm build        # 独立模式：tsc --noEmit + vite build
pnpm build:micro  # 集成模式：产物 dist-micro/（框架体积消失是八-2 的构建口径）
```

## 完成定义（DoD）

双模式 build 全绿 · 双模式运行可进可出 · 新页面在基座集成档与独立直开档行为一致。
