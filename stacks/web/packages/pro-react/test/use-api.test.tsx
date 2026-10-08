import { act, renderHook, waitFor } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";

import { ApiError } from "@yarch/contract";

import { useApiMutation, useApiQuery } from "../src/use-api";

describe("useApiQuery / useApiMutation（五-1 数据流红线）", () => {
  it("查询成功收敛 data，失败收敛 ApiError", async () => {
    const { result, rerender } = renderHook(() => useApiQuery(async () => 42));
    await waitFor(() => expect(result.current.data).toBe(42));
    expect(result.current.error).toBeNull();

    rerender(); // 稳态重渲不重复请求（deps 未变）
    const { result: errResult } = renderHook(() =>
      useApiQuery(async () => {
        throw new ApiError(1004, "资源不存在", "t-1");
      }),
    );
    await waitFor(() => expect(errResult.current.error?.code).toBe(1004));
    expect(errResult.current.error?.traceId).toBe("t-1");
  });

  it("reload 触发重查", async () => {
    const fn = vi.fn(async () => 1);
    const { result } = renderHook(() => useApiQuery(fn));
    await waitFor(() => expect(fn).toHaveBeenCalledTimes(1));
    act(() => result.current.reload());
    await waitFor(() => expect(fn).toHaveBeenCalledTimes(2));
  });

  it("变更错误不抛出，收敛到 error 态", async () => {
    const { result } = renderHook(() =>
      useApiMutation(async () => {
        throw new ApiError(3001, "用户名已存在");
      }),
    );
    let returned: unknown;
    await act(async () => {
      returned = await result.current.run();
    });
    expect(returned).toBeNull();
    expect(result.current.error?.code).toBe(3001);
    expect(result.current.loading).toBe(false);
  });

  it("非 ApiError 异常兜底为传输错误 -1", async () => {
    const { result } = renderHook(() =>
      useApiQuery(async () => {
        throw new TypeError("network boom");
      }),
    );
    await waitFor(() => expect(result.current.error?.code).toBe(-1));
  });
});
