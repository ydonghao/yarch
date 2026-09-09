# ysaas-console · 微前端基座

> ysaas-console：基于 yarch web 脚手架生成的微前端基座工程。规约依据：[contract/web/micro-frontend.md](https://github.com/ydonghao/yarch/blob/main/contract/web/micro-frontend.md) v1.0；
> 载器 micro-app（contract/README.md 关键架构决策登记表默认档，观察哨：停更切 qiankun）。

## 五权归基座（三-2/十-1/十-2）

登录态与 token、菜单/路由分发、主题与 UI 档定义、全局错误兜底、布局外壳——全部在本工程；
子应用经 `__YARCH_SUPPLY__`（props 下行，五-5）与共享 contract 单例（八-1）取用，禁子应用自建。

## 开发

```bash
pnpm install && pnpm dev            # http://localhost:5173
```

## 子应用接入三步（十二-2：入口变更只改 manifest）

1. 生成子应用：`npm create @yarch/admin ysaas-billing -- --micro sub --registry <contract/registry.md>`
2. 在 `src/app/micro-apps.config.ts` 登记入口/路由前缀/菜单元数据（跨环境入口用 `.env.local` 覆盖，十二-7）
3. 子应用侧起 `pnpm dev:micro`（集成模式 dev server，本基座即可加载）

## 双模式冒烟（十一-3，CI 口径）

- 子应用独立：`pnpm build`（自足产物）
- 子应用集成：`pnpm build:micro`（框架/contract 经 window 全局 shim，产物体积消失——八-1/八-2 机检对象）

## 部署（十二-3/十二-4）

构建产物内容寻址（hash 文件名）；nginx 一应用一 root + `try_files` 兜底——样例见 `conf/nginx.conf.example`。

## 登记提醒

应用名 `ysaas-console`（首段服务名 `ysaas`）须在 yarch 仓 `contract/registry.md` 四节前端应用名登记表登记（二-1/二-4）；
storage key / 事件名 / 路由前缀均以应用名为前缀（二-2 一名三用）。
