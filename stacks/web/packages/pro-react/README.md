# @yarch/pro-react

yarch React 业务组件库（antd v5 基线，规约锚点 [contract/web/component-library.md](../../../../contract/web/component-library.md)）。

## 组件

| 导出 | 用途 |
|---|---|
| `ProTable` | 查询表格一体：分页 + 关键词搜索 + ApiError 错误态（code/message/traceId 报障凭证）+ 重试；数据源消费 `@yarch/contract` 的 `PageData<T>` |
| `ProForm` | 声明式表单（input/password/textarea/select）；submit 抛 ApiError 呈全局错误态 |
| `useApiQuery` / `useApiMutation` | 信封 hooks：错误三分类收敛为 `ApiError`，业务侧只处理业务分支 |
| `@yarch/pro-react/demos` | demo manifest（平台预览沙箱消费；应用构建可整棵摇掉） |

## 用法

```tsx
import { ProTable } from "@yarch/pro-react";
import { client } from "./api"; // @yarch/contract createClient

<ProTable
  rowKey="id"
  searchable
  columns={[{ title: "姓名", dataIndex: "name" }]}
  request={(q) => client.get<PageData<User>>(`/api/v1/users?page=${q.page}&pageSize=${q.pageSize}`)}
/>;
```

## 验证

```bash
pnpm test    # vitest + jsdom（组件渲染/翻页/错误态/manifest 结构/render 可重入）
```
