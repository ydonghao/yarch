# ysaas-billing · 微前端子应用

> ysaas-billing：基于 yarch web 脚手架生成的微前端子应用工程。规约依据：[contract/web/micro-frontend.md](https://github.com/ydonghao/yarch/blob/main/contract/web/micro-frontend.md) v1.0。

## 双模式（十一）

| 模式 | 命令 | 说明 |
|---|---|---|
| 独立运行 | `pnpm dev` / `pnpm build` | 全量自足，浏览器直开 http://localhost:5174/ysaas-billing/…（URL 形态与集成一致，四-1） |
| 集成运行 | `pnpm dev:micro` / `pnpm build:micro` | 供给层切 `__YARCH_SUPPLY__` 实现；react 系与 @yarch/contract 经 window shim 由基座提供（八-1/八-2）——集成产物 `dist-micro/` 中框架与 contract 体积消失 |

双模式差异全部收敛在 `src/supply/`（十一-2 环境供给层）；业务代码出现 `if (isMicro)` 即设计缺陷。

## 纪律速查（详见规约）

- token 不落本应用（十-1）：`supply.getAccessToken()` 注入 createClient；401 只抛 `ApiError`，跳登录归基座（十-2）。
- storage/事件一律前缀（二-2/九-3）：`createAppStorage` / `defineAppEvents`（见 `src/events.ts`），禁裸 key、裸事件名。
- 弹层挂本应用容器（六-3）：`getPopupContainer`（见 `src/ui/popup.ts`）。
- 全局监听成对移除（七-4/五-2）：卸载清理由供给层 `onUnmount` 统一挂接。
- UI 档与基座一致（六-1）：semi（本模板档固化）；主题 token 由基座下发（六-5）。

## 接入基座

在基座仓 `src/app/micro-apps.config.ts` 登记本应用（十二-2）：`name: "ysaas-billing"`、入口 URL（跨环境 env 注入，十二-7）、菜单元数据。

## 登记提醒

应用名 `ysaas-billing`（首段服务名 `ysaas`）须在 yarch 仓 `contract/registry.md` 四节前端应用名登记表登记（二-1/二-4）。
