/**
 * demo manifest（component-library.md 三-1：./demos 子路径导出）。
 * 平台沙箱以 render(container) 注入渲染；source 为展示/复制用源码字符串。
 */
import { createApp, h } from "vue";

import { ProForm, type ProField } from "./pro-form";
import { ProTable, type ProTableColumn, type TableQuery } from "./pro-table";

export interface ComponentDemo {
  /** kebab-case，包内唯一 */
  id: string;
  title: string;
  description?: string;
  /** demo 源码字符串——平台预览页展示与复制用 */
  source: string;
  /** 渲染入口：返回清理函数（unmount 收尾，三-2） */
  render: (container: HTMLElement) => void | (() => void);
}

type Row = {
  id: number;
  name: string;
  email: string;
};

const columns: ProTableColumn[] = [
  { prop: "id", label: "ID" },
  { prop: "name", label: "姓名" },
  { prop: "email", label: "邮箱" },
];

const tableSource = `import { ProTable } from "@yarch/pro-vue";
import { client } from "./api"; // @yarch/contract createClient

const request = (q) => client.get("/api/v1/users", { params: q });

<ProTable
  row-key="id"
  searchable
  :columns="[
    { prop: 'id', label: 'ID' },
    { prop: 'name', label: '姓名' },
    { prop: 'email', label: '邮箱' },
  ]"
  :request="request"
/>;`;

const formSource = `import { ProForm } from "@yarch/pro-vue";
import { client } from "./api";

<ProForm
  :fields="[
    { name: 'name', label: '姓名', required: true },
    { name: 'email', label: '邮箱', required: true },
    { name: 'role', label: '角色', widget: 'select', options: [{ label: '管理员', value: 1 }] },
  ]"
  :submit="(values) => client.post('/api/v1/users', values)"
/>;`;

const tableRequest = async (q: TableQuery) => {
  // 预览演示数据（真实场景走 @yarch/contract client）
  const all: Row[] = Array.from({ length: 23 }, (_, i) => ({
    id: i + 1,
    name: `用户${i + 1}`,
    email: `user${i + 1}@example.com`,
  }));
  const filtered = q.keyword ? all.filter((r) => r.name.includes(q.keyword!)) : all;
  await new Promise((r) => setTimeout(r, 200));
  return {
    list: filtered.slice((q.page - 1) * q.pageSize, q.page * q.pageSize),
    total: filtered.length,
    page: q.page,
    pageSize: q.pageSize,
  };
};

const formFields: ProField[] = [
  { name: "name", label: "姓名", required: true, placeholder: "真实姓名" },
  { name: "email", label: "邮箱", required: true },
  {
    name: "role",
    label: "角色",
    widget: "select",
    options: [
      { label: "管理员", value: 1 },
      { label: "成员", value: 2 },
    ],
  },
];

/** mount：渲染节点 → (container) 渲染并返回清理函数（app.unmount 收尾，三-2） */
function mount(node: () => VNodeLike) {
  return (container: HTMLElement) => {
    const app = createApp({ render: () => node() });
    app.mount(container);
    return () => app.unmount();
  };
}

type VNodeLike = ReturnType<typeof h>;

export const demos: ComponentDemo[] = [
  {
    id: "pro-table-basic",
    title: "ProTable · 查询表格",
    description: "分页 + 关键词搜索 + ApiError 错误态一体；数据源走信封解包",
    source: tableSource,
    render: mount(() =>
      h(ProTable, {
        rowKey: "id",
        searchable: true,
        searchPlaceholder: "搜索姓名",
        columns,
        request: tableRequest,
      }),
    ),
  },
  {
    id: "pro-form-basic",
    title: "ProForm · 表单",
    description: "声明式字段 + ApiError 全局错误态（traceId 报障凭证）",
    source: formSource,
    render: mount(() =>
      h(ProForm, {
        fields: formFields,
        submit: async () => {
          await new Promise((r) => setTimeout(r, 300));
        },
      }),
    ),
  },
];
