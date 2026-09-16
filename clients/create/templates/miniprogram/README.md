# {{packageName}}

{{description}}

yarch 微信小程序工程——原生 + TypeScript + Vant Weapp，契约内核 `@yarch/contract/wx`。
规约：[contract/clients/miniprogram.md](../../../contract/clients/miniprogram.md) v1.0。

## 结构

```
miniprogram/
  app.ts               # 契约内核单点装配（信封/traceId/超时/401 刷新/GET 重试）
  app.json             # 页面注册 + 分包 + Vant Weapp 组件
  pages/login/         # 登录页（POST /auth/login → 存 token → 跳订单）
  pages/orders/        # 列表页（GET /orders → 分页解包 → 展示）
  subpackages/profile/ # 示例分包（启动非必需页面下沉分包，miniprogram.md 二-1）
scripts/ci.js          # miniprogram-ci 预览/上传（禁开发者工具手工上传，一-2）
```

## 本地开发

```bash
npm install                           # 安装 @yarch/contract + Vant Weapp
npx tsc --noEmit                      # 类型检查（miniprogram.md 五-1 strict）
# 微信开发者工具 → 打开本目录 → 工具 → 构建 npm（packNpm，一-5）
```

## CI 发布

```bash
export WX_APPID={{appId}}
export WX_PRIVATE_KEY=/path/to/private.key
npm run ci:preview    # 预览（生成二维码）
npm run ci:upload     # 上传
```

## 契约接入

信封解包 / traceId / 超时 / 401 刷新重放全部收口在 `app.ts` 的 `createWxClient`——
业务页面只见强类型结果 / `ApiError`，禁手解 `code/message/data`（miniprogram.md 三-1）。
