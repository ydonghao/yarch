# @yarch/contract

yarch 前端契约 SDK——契约唯一权威为 [yarch 仓 contract/](https://github.com/ydonghao/yarch/tree/main/contract)，本包是其 TypeScript 方言实现。**三端同源（MP4，0.3.0 起）**：同一内核服务 web / 微信小程序 / 小游戏，transport 可插拔。

## 出口（按端选用）

| 出口 | 内容 | 适用端 |
|---|---|---|
| `@yarch/contract`（主入口） | core + 微前端运行时件（event-bus / sub-app / manifest / navigator / shared-contract）+ fetch 封装 | web（微前端体系） |
| `@yarch/contract/core` | **零环境依赖内核**：信封类型与解包 / 错误码表 / ApiError（四要素）/ traceId / transport 骨架接口 | 三端通用（含 Node 测试） |
| `@yarch/contract/fetch` | fetch transport + `createClient`（web/H5/Node） | web 单页 / H5 |
| `@yarch/contract/wx` | **wx.request transport + wx storage 适配**：小程序域禁从主入口引（会带进微前端件） | 微信小程序 / 小游戏 |
| `@yarch/contract/cocos` | **运行时自动检测**：微信小游戏环境用 wx、H5 环境用 fetch——游戏代码不感知构建目标 | Cocos Creator 游戏（小游戏 + H5） |

## 内核语义（client-shared 协作参考级，对齐 [miniprogram.md](https://github.com/ydonghao/yarch/tree/main/contract/clients) 三）

- 信封解包单点：`code != 0` 抛 `ApiError`（`code / message / traceId / httpStatus` 四要素）
- 错误三分类：业务 `ApiError` · 传输错误本地保留码 `-1` · **取消不是错误**（`CancelledError` 原样传导）
- traceId 会话复用：同 client 全部请求携带同一 `X-Trace-Id`（32 位小写 hex）
- 超时单配置点：默认 30s，业务侧无 per-request 入口
- 认证失效（2001/2002）：`onUnauthorized` 刷新 → 重放一次（防环、并发合并一次刷新）；终态 `onSessionExpired` 单点回调
- GET 传输错误自动重试一次；写操作永不自动重试（防双写）

## 用法

```ts
// web（React/Vue 通吃，零框架依赖）
import { createClient } from "@yarch/contract";
const api = createClient({ baseUrl, getHeaders });
const page = await api.get<PageData<Item>>(`/v1/items?page=1`);

// 微信小程序 / 小游戏
import { createWxClient, createWxStorageBackend } from "@yarch/contract/wx";
import { createAppStorage } from "@yarch/contract/core";
const api = createWxClient({ baseUrl, onUnauthorized, onSessionExpired });
const storage = createAppStorage("ysaas-companion", createWxStorageBackend()); // key 强制前缀隔离
```

配套：web 工程脚手架 `npm create @yarch/admin@latest <name>`（[@yarch/create-admin](https://www.npmjs.com/package/@yarch/create-admin)）。
