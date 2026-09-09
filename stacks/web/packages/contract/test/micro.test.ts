/** 微前端契约断言（micro-frontend.md v1.0 对表）：事件总线/前缀 storage/导航端口/共享单例 */
import { describe, expect, it, vi } from "vitest";
import {
  assertAppEventName,
  createAppEventBus,
  defineAppEvents,
  getAppEventBus,
} from "../src/event-bus";
import { createAppStorage } from "../src/storage";
import { setNavigator, navigateTo, navigateToLogin } from "../src/navigator";
import { getSharedContract, setSharedContract, SHARED_CONTRACT_KEY } from "../src/shared-contract";
import { subAppRoutePrefix } from "../src/manifest";

class MemoryStorage implements Storage {
  private map = new Map<string, string>();
  get length() {
    return this.map.size;
  }
  getItem(key: string) {
    return this.map.get(key) ?? null;
  }
  setItem(key: string, value: string) {
    this.map.set(key, value);
  }
  removeItem(key: string) {
    this.map.delete(key);
  }
  clear() {
    this.map.clear();
  }
  key(index: number) {
    return [...this.map.keys()][index] ?? null;
  }
}

describe("微前端事件通道（九）", () => {
  it("事件名九-3 格式校验：合法通过，裸事件名/大写/缺段拒绝", () => {
    expect(() => assertAppEventName("ysaas-billing:export-done")).not.toThrow();
    expect(() => assertAppEventName("refresh")).toThrow();
    expect(() => assertAppEventName("ysaas-billing:Refresh")).toThrow();
    expect(() => assertAppEventName("ysaas-billing:export")).toThrow();
  });

  it("处理方异常不中断发布方（九-4）", () => {
    const onError = vi.fn();
    const bus = createAppEventBus(onError);
    const boom = vi.fn(() => {
      throw new Error("handler 崩了");
    });
    const ok = vi.fn();
    bus.on("ysaas-billing:export-done", boom);
    bus.on("ysaas-billing:export-done", ok);
    bus.emit("ysaas-billing:export-done", { count: 3 });
    expect(boom).toHaveBeenCalled();
    expect(ok).toHaveBeenCalledWith({ count: 3 });
    expect(onError).toHaveBeenCalledWith("ysaas-billing:export-done", expect.any(Error));
  });

  it("defineAppEvents 前缀封装：emit/on 自动拼应用名前缀且互通", () => {
    const billing = defineAppEvents("ysaas-billing");
    const received: unknown[] = [];
    const off = billing.on("export-done", (payload) => received.push(payload));
    billing.emit("export-done", { rows: 10 });
    expect(received).toEqual([{ rows: 10 }]);
    expect(() => billing.emit("refresh", 1)).toThrow(); // 缺名词段
    off();
    billing.emit("export-done", { rows: 11 });
    expect(received).toHaveLength(1); // 取消订阅生效（七-4 成对）
  });

  it("getAppEventBus 体系单例：同一 contract 实例下全等", () => {
    expect(getAppEventBus()).toBe(getAppEventBus());
  });
});

describe("前缀 storage（二-2/七-3）", () => {
  it("key 自动拼 {应用名}: 前缀且 JSON 往返", () => {
    const backend = new MemoryStorage();
    const storage = createAppStorage("ysaas-billing", backend);
    storage.set("filter-state", { page: 2 });
    expect(backend.getItem("ysaas-billing:filter-state")).toBe('{"page":2}');
    expect(storage.get<{ page: number }>("filter-state")).toEqual({ page: 2 });
    storage.remove("filter-state");
    expect(storage.get("filter-state")).toBeNull();
  });

  it("非法 key（驼峰/冒号空段）与非法应用名拒绝", () => {
    const storage = createAppStorage("ysaas-billing", new MemoryStorage());
    expect(() => storage.get("FilterState")).toThrow();
    expect(() => storage.get("a:")).toThrow();
    expect(() => createAppStorage("Billing", new MemoryStorage())).toThrow();
  });
});

describe("导航端口（四-3/十-2）", () => {
  it("navigateTo/redirectToLogin 均走注入实现", () => {
    const navigate = vi.fn();
    const redirectToLogin = vi.fn();
    setNavigator({ navigate, redirectToLogin });
    navigateTo("/ysaas-billing/invoices");
    navigateToLogin("unauthorized");
    expect(navigate).toHaveBeenCalledWith("/ysaas-billing/invoices");
    expect(redirectToLogin).toHaveBeenCalledWith("unauthorized");
  });
});

describe("contract 运行时单例标记（八-1）", () => {
  it("setSharedContract/getSharedContract 全局往返", () => {
    const marker = { ApiError: class {} };
    setSharedContract(marker);
    expect(getSharedContract()).toBe(marker);
    delete (globalThis as Record<string, unknown>)[SHARED_CONTRACT_KEY];
    expect(getSharedContract()).toBeUndefined();
  });
});

describe("manifest 形状（三-4/十二-2）", () => {
  it("路由前缀唯一写法 = /{应用名}（四-1）", () => {
    expect(subAppRoutePrefix("ysaas-billing")).toBe("/ysaas-billing");
  });
});
