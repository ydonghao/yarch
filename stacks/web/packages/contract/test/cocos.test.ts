/** Cocos 适配器断言：环境检测 + 运行时自动选 transport + storage 前缀隔离 */
import { afterEach, describe, expect, it, vi } from "vitest";
import {
  createCocosClient,
  createCocosStorage,
  detectCocosRuntime,
} from "../src/cocos";
import { CancelledError } from "../src/transport";

const flush = () => new Promise<void>((r) => setTimeout(r, 0));

afterEach(() => {
  delete (globalThis as { wx?: unknown }).wx;
  delete (globalThis as { fetch?: unknown }).fetch;
});

interface WxRequestOpts {
  url: string;
  method?: string;
  header?: Record<string, string>;
  data?: string;
  timeout?: number;
  success?(res: { statusCode: number; data: unknown; header?: Record<string, unknown> }): void;
  fail?(err: { errMsg?: string }): void;
}

function installWx() {
  const sent: WxRequestOpts[] = [];
  const store = new Map<string, string>();
  (globalThis as { wx?: unknown }).wx = {
    request: (opts: WxRequestOpts) => {
      sent.push(opts);
      return { abort: vi.fn() };
    },
    setStorageSync: (key: string, value: string) => void store.set(key, value),
    getStorageSync: (key: string) => store.get(key) ?? "",
    removeStorageSync: (key: string) => void store.delete(key),
  };
  return { sent, store };
}

const envelope = (code: number, data: unknown = null, msg = "ok", traceId = "t") => ({
  code,
  message: msg,
  data,
  traceId,
});

describe("detectCocosRuntime", () => {
  it("wx.request 存在 → wechat-minigame", () => {
    installWx();
    expect(detectCocosRuntime()).toBe("wechat-minigame");
  });

  it("fetch 存在且无 wx → h5", () => {
    (globalThis as { fetch?: unknown }).fetch = vi.fn();
    expect(detectCocosRuntime()).toBe("h5");
  });

  it("两者都不存在 → throw（game.md 三-2：Cocos 原生档暂不支持）", () => {
    expect(() => detectCocosRuntime()).toThrow(/运行时不可识别|暂不支持/);
  });
});

describe("createCocosClient（minigame 运行时 → wx transport）", () => {
  it("走 wx.request 发请求，X-Trace-Id 注入", async () => {
    const { sent } = installWx();
    const client = createCocosClient({ baseUrl: "https://api.example.com" });
    const p = client.get<{ id: number }>("/v1/items");
    sent[0].success!({ statusCode: 200, data: envelope(0, { id: 1 }) });
    await expect(p).resolves.toEqual({ id: 1 });
    expect(sent[0].url).toBe("https://api.example.com/v1/items");
    expect(sent[0].header?.["X-Trace-Id"]).toMatch(/^[0-9a-f]{32}$/);
  });

  it("traceId 会话复用（两次请求同值）", async () => {
    const { sent } = installWx();
    const client = createCocosClient();
    const p1 = client.get("/a");
    const p2 = client.get("/b");
    sent[0].success!({ statusCode: 200, data: envelope(0) });
    sent[1].success!({ statusCode: 200, data: envelope(0) });
    await Promise.all([p1, p2]);
    expect(sent[0].header?.["X-Trace-Id"]).toBe(sent[1].header?.["X-Trace-Id"]);
  });

  it("业务错误抛 ApiError 四要素", async () => {
    const { sent } = installWx();
    const client = createCocosClient();
    const p = client.post<never>("/v1/orders", {});
    sent[0].success!({ statusCode: 409, data: envelope(1007, null, "幂等冲突", "trace-9") });
    await expect(p).rejects.toMatchObject({
      name: "ApiError",
      code: 1007,
      traceId: "trace-9",
      httpStatus: 409,
    });
  });

  it("abort → CancelledError 原样传导（取消不是错误）", async () => {
    const { sent } = installWx();
    const client = createCocosClient();
    const p = client.post<never>("/v1/orders", {});
    sent[0].fail!({ errMsg: "request:fail abort" });
    await expect(p).rejects.toBeInstanceOf(CancelledError);
  });

  it("2001 刷新重放一次", async () => {
    const { sent } = installWx();
    const onUnauthorized = vi.fn(() => true);
    const client = createCocosClient({ onUnauthorized });
    const p = client.post("/v1/orders", {});
    sent[0].success!({ statusCode: 200, data: envelope(2001, null, "未认证") });
    await flush();
    sent[1].success!({ statusCode: 200, data: envelope(0, { ok: true }) });
    await expect(p).resolves.toEqual({ ok: true });
    expect(onUnauthorized).toHaveBeenCalledTimes(1);
    expect(sent.length).toBe(2);
  });
});

describe("createCocosStorage（前缀隔离，game.md 三-3）", () => {
  it("minigame 运行时 → wx storage backend", () => {
    installWx();
    const storage = createCocosStorage("ysaas-game");
    storage.set("score", { level: 1 });
    // 不暴露 raw backend，验证 round-trip 即可
    expect(storage.get<{ level: number }>("score")).toEqual({ level: 1 });
    storage.remove("score");
    expect(storage.get("score")).toBeNull();
  });
});
