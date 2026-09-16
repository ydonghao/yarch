# {{packageName}}

{{description}}

yarch Cocos Creator 游戏工程——TypeScript，契约内核 `@yarch/contract/cocos`（运行时自动检测：微信小游戏→wx.request / H5→fetch）。
规约：[contract/clients/game.md](../../../contract/clients/game.md) v1.0。

## 结构

```
assets/scripts/
  ApiManager.ts    # 契约客户端单点装配（信封/traceId/超时/401 刷新/GET 重试 + 运行时自动检测）
  MainScene.ts     # 示例场景：拉取列表 + 错误三分类（业务·传输·取消）
```

## 使用

1. `npm install`——安装 `@yarch/contract`
2. 用 Cocos Creator {{cocosVersion}} 打开本目录（编辑器自动生成 meta/settings/library）
3. 创建场景 → 添加 Node → 挂载 `MainScene` 脚本 → 绑定 Label 节点
4. 构建发布：编辑器 → 项目 → 构建发布 → 选择「微信小游戏」或「Web Mobile」

## 契约接入

信封解包 / traceId / 超时 / 401 刷新重放全部收口在 `ApiManager.ts` 的 `createCocosClient`——
游戏脚本只见强类型结果 / `ApiError`，禁手解 `code/message/data`（game.md 三-1）。
运行时检测由 `@yarch/contract/cocos` 完成：小游戏构建自动用 `wx.request`，H5 构建自动用 `fetch`。

## 首包预算

微信小游戏首包 ≤4MB 硬线（game.md 二-3）：首包外资源远程化（CDN + 版本化路径 + 热更清单）。
