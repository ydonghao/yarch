/**
 * ProTable：查询表格一体（antd Table 薄语义层）。
 * 数据流：request(PageQuery & filters) → PageData<T>（@yarch/contract 信封分页负载，五-1）；
 * ApiError → 表格错误态（message + 重试）；翻页/筛选由组件持有，业务侧只供 request 与 columns。
 */
import { Alert, Button, Input, Space, Table } from "antd";
import type { TableProps } from "antd";
import type { ColumnsType } from "antd/es/table";
import { useEffect, useState } from "react";

import { ApiError, type PageData, type PageQuery } from "@yarch/contract";

export interface TableQuery extends PageQuery {
  keyword?: string;
}

export interface ProTableProps<T> {
  /** 数据源：业务侧组装（内部走 useApiQuery/useApiMutation 或 client） */
  request: (q: TableQuery) => Promise<PageData<T>>;
  columns: ColumnsType<T>;
  /** 内置关键词搜索框（回车触发，重置到第 1 页） */
  searchable?: boolean;
  searchPlaceholder?: string;
  rowKey?: keyof T | ((record: T) => string);
  /** 透传 antd Table 其余 props（五-3：薄透传 + 少数语义 props） */
  tableProps?: Omit<TableProps<T>, "columns" | "dataSource" | "pagination" | "loading">;
}

export function ProTable<T>(props: ProTableProps<T>) {
  const { request, columns, searchable = false, searchPlaceholder, rowKey, tableProps } = props;
  const [page, setPage] = useState(1);
  const [keyword, setKeyword] = useState("");
  const [pending, setPending] = useState("");
  const [data, setData] = useState<PageData<T> | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<ApiError | null>(null);
  const [reloadTick, setReloadTick] = useState(0);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError(null);
    request({ page, pageSize: 20, keyword: keyword || undefined })
      .then((d) => {
        if (!cancelled) setData(d);
      })
      .catch((e: unknown) => {
        if (!cancelled) setError(e instanceof ApiError ? e : new ApiError(-1, String(e)));
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [request, page, keyword, reloadTick]);

  if (error) {
    return (
      <Alert
        type="error"
        showIcon
        message={`加载失败（code ${error.code}）`}
        description={`${error.message}${error.traceId ? ` · traceId ${error.traceId}` : ""}`}
        action={
          <Button size="small" onClick={() => setReloadTick((t) => t + 1)}>
            重试
          </Button>
        }
      />
    );
  }

  return (
    <Space direction="vertical" style={{ width: "100%" }} size="middle">
      {searchable && (
        <Space.Compact style={{ width: 320 }}>
          <Input
            placeholder={searchPlaceholder ?? "搜索"}
            value={pending}
            onChange={(e) => setPending(e.target.value)}
            onPressEnter={() => {
              setPage(1);
              setKeyword(pending);
            }}
            allowClear
          />
          <Button
            type="primary"
            onClick={() => {
              setPage(1);
              setKeyword(pending);
            }}
          >
            查询
          </Button>
        </Space.Compact>
      )}
      <Table<T>
        rowKey={rowKey as never}
        columns={columns}
        dataSource={data?.list ?? []}
        loading={loading}
        pagination={{
          current: data?.page ?? page,
          pageSize: data?.pageSize ?? 20,
          total: data?.total ?? 0,
          onChange: setPage,
          showSizeChanger: false,
        }}
        {...tableProps}
      />
    </Space>
  );
}
