# @yarch/pro-vue

yarch Vue 业务组件库（ElementPlus 基线，规约锚点 [contract/web/component-library.md](../../../../contract/web/component-library.md)；CL6 基线锁定——基座定 ElementPlus 系的体系用本包，React/antd 体系用 `@yarch/pro-react`，跨档混用按 micro-frontend.md 六-1 否决）。

## 组件

| 导出 | 用途 |
|---|---|
| `ProTable` | 查询表格一体：分页 + 关键词搜索 + ApiError 错误态（code/message/traceId 报障凭证）+ 重试；数据源消费 `@yarch/contract` 的 `PageData<T>` |
| `ProForm` | 声明式表单（input/password/textarea/select）；submit 抛 ApiError 呈全局错误态 |
| `useApiQuery` / `useApiMutation` | 信封 hooks：错误三分类收敛为 `ApiError`，业务侧只处理业务分支 |
| `@yarch/pro-vue/demos` | demo manifest（平台预览沙箱消费；应用构建可整棵摇掉） |

## 用法

```vue
<script setup lang="ts">
import { ProTable } from "@yarch/pro-vue";
import { client } from "./api"; // @yarch/contract createClient

const columns = [{ prop: "name", label: "姓名" }];
const request = (q: { page: number; pageSize: number; keyword?: string }) =>
  client.get(`/api/v1/users?page=${q.page}&pageSize=${q.pageSize}`);
</script>

<template>
  <ProTable row-key="id" searchable :columns="columns" :request="request" />
</template>
```

## 形态说明

- 组件为 `defineComponent + h()` 渲染函数纯 `.ts` 源码直出（`main: src/index.ts`）——SFC/JSX 在 `node_modules` 不被消费方默认编译，纯 `.ts` 保持生成工程零配置消费。
- ElementPlus 样式由消费方自理（全量引入或 unplugin 按需），本包不带样式。
- `useApiQuery(fn, deps)` 的 `deps` 传响应式源（ref/computed）才会触发重查，普通值不追踪——与 React 版 deps 语义对齐。

## 验证

```bash
pnpm test    # vitest + jsdom（组件渲染/翻页/错误态/manifest 结构/render 可重入）
```
