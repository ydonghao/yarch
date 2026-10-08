import { mount } from "@vue/test-utils";
import { describe, expect, it, vi } from "vitest";

import { ApiError } from "@yarch/contract";

import { ProTable, type TableQuery } from "../src/pro-table";

// ElementPlus Table 在 jsdom 下依赖的浏览器 API stub
class FakeResizeObserver {
  observe() {}
  unobserve() {}
  disconnect() {}
}
vi.stubGlobal("ResizeObserver", FakeResizeObserver);
vi.stubGlobal(
  "matchMedia",
  vi.fn().mockReturnValue({ matches: false, addListener: vi.fn(), removeListener: vi.fn(), addEventListener: vi.fn(), removeEventListener: vi.fn() }),
);

interface Row {
  id: number;
  name: string;
}

const columns = [
  { prop: "id", label: "ID" },
  { prop: "name", label: "姓名" },
];

function makeData(page: number, total = 45) {
  return {
    list: Array.from({ length: 20 }, (_, i) => ({ id: (page - 1) * 20 + i + 1, name: `用户${(page - 1) * 20 + i + 1}` })),
    total,
    page,
    pageSize: 20,
  };
}

function makeWrapper(request: (q: TableQuery) => Promise<ReturnType<typeof makeData>>) {
  return mount(ProTable, {
    props: { request, columns, rowKey: "id" },
    global: { stubs: { transition: false } },
  });
}

describe("ProTable（component-library.md 五）", () => {
  it("渲染分页数据并随翻页重新拉取", async () => {
    const request = vi.fn(async (q: TableQuery) => makeData(q.page));
    const wrapper = makeWrapper(request);

    await vi.waitFor(() => expect(request).toHaveBeenCalledTimes(1));
    expect(request).toHaveBeenCalledWith(expect.objectContaining({ page: 1, pageSize: 20 }));
    await vi.waitFor(() => expect(wrapper.text()).toContain("用户1"));

    // 翻到第 2 页（ElementPlus pager 数字项）
    const page2 = wrapper.findAll(".el-pager li").find((li) => li.text() === "2");
    expect(page2).toBeDefined();
    await page2!.trigger("click");
    await vi.waitFor(() => expect(request).toHaveBeenLastCalledWith(expect.objectContaining({ page: 2 })));
    await vi.waitFor(() => expect(wrapper.text()).toContain("用户21"));
  });

  it("关键词搜索重置到第 1 页并传 keyword", async () => {
    const request = vi.fn(async (q: TableQuery) => {
      const d = makeData(q.page);
      const list = q.keyword ? d.list.filter((r) => r.name.includes(q.keyword!)) : d.list;
      return { ...d, list, total: q.keyword ? list.length : 45 };
    });
    const wrapper = mount(ProTable, {
      props: { request, columns, searchable: true, searchPlaceholder: "搜索" },
    });

    // 先翻到第 2 页，再搜索——应重置回第 1 页
    await vi.waitFor(() => expect(request).toHaveBeenCalledTimes(1));
    const page2 = wrapper.findAll(".el-pager li").find((li) => li.text() === "2");
    await page2!.trigger("click");
    await vi.waitFor(() => expect(request).toHaveBeenLastCalledWith(expect.objectContaining({ page: 2 })));

    const input = wrapper.find('input[placeholder="搜索"]');
    await input.setValue("用户21");
    await input.trigger("keyup", { key: "Enter" });
    await vi.waitFor(() =>
      expect(request).toHaveBeenLastCalledWith(expect.objectContaining({ page: 1, keyword: "用户21" })),
    );
  });

  it("ApiError 呈现错误态（code/message/traceId）并提供重试", async () => {
    const request = vi
      .fn()
      .mockRejectedValueOnce(new ApiError(1001, "参数校验失败", "tid-123", 400))
      .mockResolvedValueOnce(makeData(1));
    const wrapper = makeWrapper(request as never);

    await vi.waitFor(() => expect(wrapper.text()).toContain("加载失败（code 1001）"));
    expect(wrapper.text()).toContain("参数校验失败 · traceId tid-123");

    await wrapper.find("button").trigger("click");
    await vi.waitFor(() => expect(wrapper.text()).toContain("用户1"));
  });
});
