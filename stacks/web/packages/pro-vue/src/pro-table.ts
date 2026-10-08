/**
 * ProTable：查询表格一体（ElementPlus Table 薄语义层）。
 * 数据流：request(PageQuery & filters) → PageData<T>（@yarch/contract 信封分页负载，五-1）；
 * ApiError → 表格错误态（message + 重试）；翻页/筛选由组件持有，业务侧只供 request 与 columns。
 * 形态为 h() 渲染函数纯 .ts——源码直出（SFC 在 node_modules 不被消费方默认编译，见 README）。
 */
import { ElAlert, ElButton, ElInput, ElPagination, ElTable, ElTableColumn } from "element-plus";
import { defineComponent, h, ref, shallowRef, watch, withKeys } from "vue";
import type { PropType, VNodeChild } from "vue";

import { ApiError, type PageData, type PageQuery } from "@yarch/contract";

export interface TableQuery extends PageQuery {
  keyword?: string;
}

/** 列描述（ElementPlus 方言：prop/label + 薄透传，自定义渲染走 formatter） */
export interface ProTableColumn {
  prop: string;
  label: string;
  width?: string | number;
  align?: "left" | "center" | "right";
  /** 自定义单元格渲染（薄语义层：文本节点或单个 VNode） */
  formatter?: (row: Record<string, unknown>) => VNodeChild;
}

export interface ProTableProps {
  /** 数据源：业务侧组装（内部走 useApiQuery/useApiMutation 或 client） */
  request: (q: TableQuery) => Promise<PageData<Record<string, unknown>>>;
  columns: ProTableColumn[];
  /** 内置关键词搜索框（回车触发，重置到第 1 页） */
  searchable?: boolean;
  searchPlaceholder?: string;
  rowKey?: string;
}

function toApiError(e: unknown): ApiError {
  if (e instanceof ApiError) return e;
  return new ApiError(-1, e instanceof Error ? e.message : String(e));
}

export const ProTable = defineComponent({
  name: "YarchProTable",
  props: {
    request: { type: Function as PropType<ProTableProps["request"]>, required: true },
    columns: { type: Array as PropType<ProTableColumn[]>, required: true },
    searchable: { type: Boolean, default: false },
    searchPlaceholder: { type: String, default: "搜索" },
    rowKey: { type: String, default: "id" },
  },
  setup(props) {
    const page = ref(1);
    const keyword = ref("");
    const pending = ref("");
    const data = shallowRef<PageData<Record<string, unknown>> | null>(null);
    const loading = ref(false);
    const error = shallowRef<ApiError | null>(null);
    const reloadTick = ref(0);

    watch(
      () => [reloadTick.value, page.value, keyword.value, props.request],
      (_next, _prev, onCleanup) => {
        let cancelled = false;
        onCleanup(() => {
          cancelled = true;
        });
        loading.value = true;
        error.value = null;
        props
          .request({ page: page.value, pageSize: 20, keyword: keyword.value || undefined })
          .then((d) => {
            if (!cancelled) data.value = d;
          })
          .catch((e: unknown) => {
            if (!cancelled) error.value = toApiError(e);
          })
          .finally(() => {
            if (!cancelled) loading.value = false;
          });
      },
      { immediate: true },
    );

    const applySearch = () => {
      page.value = 1;
      keyword.value = pending.value;
    };

    return () => {
      if (error.value) {
        const e = error.value;
        return h(ElAlert, { type: "error", showIcon: true, closable: false }, () => [
          h("div", { style: "display:flex;align-items:center;gap:12px;flex-wrap:wrap" }, [
            h("span", null, [
              `加载失败（code ${e.code}）——${e.message}`,
              e.traceId ? ` · traceId ${e.traceId}` : "",
            ]),
            h(
              ElButton,
              { size: "small", onClick: () => (reloadTick.value += 1) },
              () => "重试",
            ),
          ]),
        ]);
      }

      const rows = data.value?.list ?? [];
      const children: VNodeChild[] = [];

      if (props.searchable) {
        children.push(
          h("div", { style: "display:flex;gap:8px;margin-bottom:12px" }, [
            h(ElInput, {
              modelValue: pending.value,
              "onUpdate:modelValue": (v: string) => (pending.value = v),
              placeholder: props.searchPlaceholder,
              clearable: true,
              style: "width:320px",
              onKeyup: withKeys(applySearch, ["enter"]),
            }),
            h(ElButton, { type: "primary", onClick: applySearch }, () => "查询"),
          ]),
        );
      }

      children.push(
        h(
          ElTable,
          {
            data: rows,
            rowKey: props.rowKey,
            emptyText: loading.value ? "加载中…" : "暂无数据",
          },
          () =>
            props.columns.map((c) =>
              h(ElTableColumn, {
                key: c.prop,
                prop: c.prop,
                label: c.label,
                width: c.width,
                align: c.align,
                formatter: c.formatter
                  ? (row: Record<string, unknown>) => c.formatter!(row) as never
                  : undefined,
              }),
            ),
        ),
        h(ElPagination, {
          style: "margin-top:12px;justify-content:flex-end",
          layout: "total, prev, pager, next",
          currentPage: data.value?.page ?? page.value,
          pageSize: data.value?.pageSize ?? 20,
          total: data.value?.total ?? 0,
          onCurrentChange: (p: number) => (page.value = p),
        }),
      );

      return h("div", null, children);
    };
  },
});
