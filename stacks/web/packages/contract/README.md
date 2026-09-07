# @yarch/contract

yarch 前端契约 SDK——契约唯一权威为 [yarch 仓 contract/](https://github.com/ydonghao/yarch/tree/main/contract)，本包是其 TypeScript 方言实现（React/Vue 通吃，零框架依赖）。

- `rest-response`：`RestResponse<T>` 信封类型 + 解包（失败抛 `ApiError`）
- `error-codes`：13 码常量表（0 成功 · 1xxx 通用 · 2xxx 认证 · 3xxx+ 业务注册段）
- `api-error`：traceId 报障凭证 · `shouldRedirectToLogin`
- `trace-id`：`X-Trace-Id` 生成/透传（W3C traceparent 对齐）
- `http`：fetch 封装 + 401 映射
- `navigator`：导航端口（依赖倒置——框架适配包 `@yarch/react` / `@yarch/vue` 注入实现）

用法：`createClient({ baseUrl, getHeaders })` → `api.get<PageData<T>>("/items?page=1")`。

配套：工程脚手架 `npm create @yarch/admin@latest <name>`（[@yarch/create-admin](https://www.npmjs.com/package/@yarch/create-admin)）。
