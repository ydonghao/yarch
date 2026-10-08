import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";

import { ApiError } from "@yarch/contract";

const BTN_RETRY = /重\s*试/;

import { ProTable, type TableQuery } from "../src/pro-table";

vi.stubGlobal(
  "matchMedia",
  vi.fn().mockReturnValue({ matches: false, addListener: vi.fn(), removeListener: vi.fn(), addEventListener: vi.fn(), removeEventListener: vi.fn() }),
);

interface Row {
  id: number;
  name: string;
}

const columns = [
  { title: "ID", dataIndex: "id" },
  { title: "姓名", dataIndex: "name" },
];

function makeData(page: number, total = 45): { rows: Row[]; total: number } {
  return {
    rows: Array.from({ length: 20 }, (_, i) => ({ id: (page - 1) * 20 + i + 1, name: `用户${(page - 1) * 20 + i + 1}` })),
    total,
  };
}

describe("ProTable（component-library.md 五）", () => {
  it("渲染分页数据并随翻页重新拉取", async () => {
    const request = vi.fn(async (q: TableQuery) => {
      const { rows, total } = makeData(q.page);
      return { list: rows, total, page: q.page, pageSize: q.pageSize };
    });
    render(<ProTable<Row> rowKey="id" columns={columns} request={request} />);

    await waitFor(() => expect(request).toHaveBeenCalledTimes(1));
    expect(request).toHaveBeenCalledWith(expect.objectContaining({ page: 1, pageSize: 20 }));
    expect(await screen.findByText("用户1")).toBeDefined();

    // 翻到第 2 页
    const page2 = screen.getByTitle("2");
    await userEvent.click(page2);
    await waitFor(() => expect(request).toHaveBeenLastCalledWith(expect.objectContaining({ page: 2 })));
  });

  it("关键词搜索重置到第 1 页并传 keyword", async () => {
    const request = vi.fn(async (q: TableQuery) => {
      const { rows, total } = makeData(q.page);
      const list = q.keyword ? rows.filter((r) => r.name.includes(q.keyword!)) : rows;
      return { list, total: list.length, page: q.page, pageSize: q.pageSize };
    });
    render(<ProTable<Row> rowKey="id" columns={columns} request={request} searchable searchPlaceholder="搜索" />);

    const input = await screen.findByPlaceholderText("搜索");
    await userEvent.type(input, "用户1{Enter}");
    await waitFor(() =>
      expect(request).toHaveBeenLastCalledWith(expect.objectContaining({ page: 1, keyword: "用户1" })),
    );
  });

  it("ApiError 呈现错误态（code/message/traceId）并提供重试", async () => {
    const request = vi.fn()
      .mockRejectedValueOnce(new ApiError(1001, "参数校验失败", "tid-123", 400))
      .mockResolvedValueOnce({ list: makeData(1).rows, total: 45, page: 1, pageSize: 20 });
    render(<ProTable<Row> rowKey="id" columns={columns} request={request} />);

    expect(await screen.findByText("加载失败（code 1001）")).toBeDefined();
    expect(await screen.findByText(/参数校验失败 · traceId tid-123/)).toBeDefined();

    await userEvent.click(await screen.findByText(BTN_RETRY));
    await waitFor(() => expect(screen.getByText("用户1")).toBeDefined());
  });
});
