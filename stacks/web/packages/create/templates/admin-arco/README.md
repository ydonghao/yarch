# {{appName}}

> {{description}}
>
> 由 `@yarch/create-admin` 从 **admin-arco 档**（Vite + React + Arco Design）生成。

## 起跑

```bash
pnpm install
pnpm dev        # http://localhost:{{port}}
pnpm build      # tsc --noEmit + vite build
```

## 与后端联调

`vite.config.ts` 已把 `/api/v1` 反代到 `{{proxyTarget}}`，改 target 即可指向你的后端。
接口走 `@yarch/contract` 的信封解包（RestResponse / 错误码 / traceId / 401 跳转）。

## 登记与升级

1. **服务名登记**：`{{packageName}}` 须去 yarch 仓 `contract/registry.md` 二、服务名登记表登记（PR 即登记）；
2. **@yarch 底座依赖**：若 package.json 中为 `^x.y.z` 版本依赖——升级底座 = `pnpm update @yarch/contract @yarch/react`；若为 `file:…` 本地路径（yarch 发版前的过渡形态）——正式发版后替换为 `^x.y.z` 并重跑 `pnpm install`。

## 结构纪律（depcruise 同款口径）

- `pages/` 薄入口：只组合 `features/`，不得直调 `features/*/api.ts`；
- `ui/`、`components/` 不得反向依赖 `features/`、`pages/`；
- 有 JSX 用 `.tsx`，纯逻辑/类型 `.ts`。
