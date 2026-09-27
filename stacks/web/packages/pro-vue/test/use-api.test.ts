import { mount } from "@vue/test-utils";
import { defineComponent, h, ref } from "vue";
import { describe, expect, it, vi } from "vitest";

import { ApiError } from "@yarch/contract";

import { useApiMutation, useApiQuery } from "../src/use-api";

/** 测试 harness：把 hook 状态平铺渲染成文本，便于断言与卸载 */
function withSetup<T>(setup: () => T, render: (s: T) => ReturnType<typeof h>) {
  let state: T;
  const wrapper = mount(
    defineComponent({
      setup() {
        state = setup();
        return () => render(state);
      },
    }),
  );
  return { wrapper, get state() { return state!; } };
}

describe("useApiQuery / useApiMutation（五-1 信封 hooks）", () => {
  it("首次执行：loading → data 落位", async () => {
    const fn = vi.fn(async () => ({ list: [1], total: 1, page: 1, pageSize: 20 }));
    const { wrapper, state } = withSetup(
      () => useApiQuery(fn),
      (s) => h("div", null, `${String(s.data.value?.total)}|${String(s.loading.value)}|${String(s.error.value)}`),
    );

    expect(state.loading.value).toBe(true);
    await vi.waitFor(() => expect(state.data.value?.total).toBe(1));
    expect(state.loading.value).toBe(false);
    expect(state.error.value).toBeNull();
    wrapper.unmount();
  });

  it("错误归一为 ApiError（非 ApiError 抛出兜底 -1）", async () => {
    const { state, wrapper } = withSetup(
      () => useApiQuery(() => Promise.reject(new Error("网络中断"))),
      () => h("div"),
    );
    await vi.waitFor(() => expect(state.error.value).toBeInstanceOf(ApiError));
    expect(state.error.value!.code).toBe(-1);
    expect(state.error.value!.message).toBe("网络中断");
    wrapper.unmount();
  });

  it("reload 重查；deps 里响应式源变化重查", async () => {
    const kw = ref("a");
    const fn = vi.fn(async () => ({ list: [], total: 0, page: 1, pageSize: 20 }));
    const { state, wrapper } = withSetup(
      () => useApiQuery(fn, [kw]),
      () => h("div"),
    );

    await vi.waitFor(() => expect(fn).toHaveBeenCalledTimes(1));
    state.reload();
    await vi.waitFor(() => expect(fn).toHaveBeenCalledTimes(2));
    kw.value = "b";
    await vi.waitFor(() => expect(fn).toHaveBeenCalledTimes(3));
    wrapper.unmount();
  });

  it("useApiMutation：错误收敛 error 态不抛出，返回 null", async () => {
    const fn = vi.fn(async (x: number) => {
      if (x === 0) throw new ApiError(1001, "参数校验失败", "tid-1", 400);
      return x * 2;
    });
    const { state, wrapper } = withSetup(
      () => useApiMutation(fn),
      () => h("div"),
    );

    await expect(state.run(0)).resolves.toBeNull();
    expect(state.error.value?.code).toBe(1001);
    expect(state.loading.value).toBe(false);

    state.error.value = null;
    await expect(state.run(3)).resolves.toBe(6);
    expect(state.error.value).toBeNull();
    wrapper.unmount();
  });
});
