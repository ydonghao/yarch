# AGENTS.md — admin-demo 工程守则

> 本工程由 yarch 脚手架生成，基于跨栈统一契约（yarch 仓 `contract/`，全部 v1.0 定稿）。
> 任何 AI 编码代理改码前先读本文件；条文与直觉冲突时以本文件为准，红线改动直接拒绝——机检与 CR 均按此执行。

## 契约红线（违反 = 机检失败 / CR 必拒）

| 红线 | 规则 | 出处 |
|---|---|---|
| 统一信封 | 响应一律 `{code, message, data, traceId}` 四字段，`code === 0` 即成功；判错只看 `code`，HTTP 状态码不承载业务语义 | contract/api/rest-response.md |
| 错误码 | 只用 `@yarch/contract` 的 `errorCodes` 常量（1xxx 通用 / 2xxx 认证）；新业务码在业务仓登记 3xxx+ 段位；**禁裸数字错误码** | contract/api/error-codes.md |
| HTTP 封装 | 业务请求只经 `createClient` 产出的客户端（信封解包 / traceId 透传 / 401 跳登录已内置）；**禁裸 fetch/axios 直连业务接口** | 参考 `src/features/*/api.ts` 现状 |
| 分层方向 | `pages/` 只组合 `features/`（禁直调 `features/*/api.ts`，取数经 hooks）；`ui/`·`components/` 禁反向依赖 `features/`·`pages/` | yarch depcruise 同款规则 |
| traceId | 排查链路键即 `traceId`（W3C traceparent）；响应头/信封里的 traceId 禁丢弃，报障必附 | contract/api/logging-trace.md |
| 命名 | 文件/目录 kebab-case；工程名即服务名（小写短横线，禁裸通用词），改名 = registry 变更评审 | contract/registry.md 一 |
| 底座升级 | `@yarch/contract`·`@yarch/react` 一律 `pnpm update` 跟进版本；**禁 patch / 改 node_modules / 拷贝源码内联** | — |

## 验证（改完必跑，全绿才算完成）

```bash
pnpm build       # tsc --noEmit + vite build——生成工程的机检底线
```

平台侧还有契约断言测试与依赖方向机检（depcruise）；本工程若引入 CI，建议把两者加进来后再放开依赖方向的约束。

## 登记与升级

- 服务名 / 错误码段位 / 前端应用名登记：yarch 仓 `contract/registry.md`（PR 即登记）；
- 底座新版本：`pnpm update @yarch/contract @yarch/react`，只改 version 一行。

## 完成定义（DoD）

tsc 零错 · build 通过 · 新增接口全走统一信封与错误码常量 · 新增取数落在 features 层。
