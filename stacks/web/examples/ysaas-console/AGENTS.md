# AGENTS.md — ysaas-console（微前端基座）工程守则

> 本工程由 yarch 脚手架生成（`--micro base`），基于跨栈统一契约（yarch 仓 `contract/`）与微前端规约 `contract/web/micro-frontend.md`。
> 任何 AI 编码代理改码前先读本文件；条文与直觉冲突时以本文件为准，红线改动直接拒绝——机检与 CR 均按此执行。

## 基座五权（只归基座，条文 micro-frontend.md 一-1）

登录态唯一持有（十-1）· 导航端口唯一注册（十-2）· 菜单与路由分发（三-4）· 主题下发（六-1）· 错误兜底（四-5）。子应用索要任何一项 = 架构违规。

## 契约红线（违反 = 机检失败 / CR 必拒）

| 红线 | 规则 | 出处 |
|---|---|---|
| manifest 装配 | 子应用接入/下线**只改 `src/app/micro-apps.config.ts` 登记表**（entry/version/routePrefix/menu）；禁在任何组件里硬编码子应用路由或菜单 | 十二-2 / 三-4 |
| 登记先行 | 新子应用应用名须先在 yarch 仓 `contract/registry.md` 四节登记（两段式、首段=已登记服务名、全局不重名），再入本工程登记表 | registry.md 四 |
| 共享运行时 | `src/micro/shared.ts` 注册 `__YARCH_REACT__ / __YARCH_CONTRACT__ / __YARCH_SUPPLY__` 后才能启动载器；新增共享成员须同步子应用供给层口径 | 八-1 / 八-2 |
| 事件订阅宿主 | 子应用事件（`{应用名}:{动词-名词}`）的订阅挂**常驻布局壳**（`layouts/basic-layout.tsx`），不挂会随路由卸载的页面；订阅与退订成对 | 九-1 / 七-4 |
| 统一信封 / 错误码 / traceId | 同单应用档：判错只看 `code`，错误码只用常量，报障必附 traceId | contract/api/* |
| 底座升级 | `@yarch/contract`·`@yarch/react`·`@micro-zoe/micro-app` 一律 `pnpm update`；禁 patch / 内联拷贝 | — |

## 验证（改完必跑）

```bash
pnpm build       # tsc --noEmit + vite build
pnpm build:micro 同款子应用联调：入口 URL 经 VITE_SUB_*_URL 注入，禁改死代码
```

## 完成定义（DoD）

tsc 零错 · build 通过 · 新子应用接入只动了登记表 · 基座在子应用全部下线后仍可独立运行（三-1）。
