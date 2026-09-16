/** wx.request 适配器断言（miniprogram.md 三）：header/timeout 映射、三分类、刷新重放、storage 前缀隔离 */
import { afterEach, describe, expect, it, vi } from "vitest";
import { createWxClient, createWxStorageBackend, createWxTransport } from "../src/wx";
import { createAppStorage } from "../src/storage";
import { CancelledError } from "../src/transport";

interface SentRequest {
  url: string;
  method?: string;
  header?: Record<string, string>;
  data?: string;
  timeout?: number;
  success?(res: { statusCode: number; data: unknown; header?: Record<string, unknown> }): void;
  fail?(err: { errMsg?: string }): void;
}

function installWx() {
  const sent: SentRequest[] = [];
  const storage = new Map<string, string>();
  const wxImpl = {
    request(opts: SentRequest) {
      sent.push(opts);
      return { abort: vi.fn() };
    },
    setStorageSync: (key: string, value: string) => void storage.set(key, value),
    getStorageSync: (key: string) => storage.get(key) ?? "",
    removeStorageSync: (key: string) => void storage.delete(key),
  };
  (globalThis as { wx?: unknown }).wx = wxImpl;
  return { sent, storage };
}

const envelope = (code: number, data: unknown = null, message = "ok", traceId = "t") => ({
  code,
  message,
  data,
  traceId,
});

const flush = () => new Promise<void>((resolve) => setTimeout(resolve, 0));

afterEach(() => {
  delete (globalThis as { wx?: unknown }).wx;
});

describe("createWxClient（wx.request 适配）", () => {
  it("成功解包：url 拼接、method、X-Trace-Id 32 位 hex 注入（header 字段名对齐 wx）", async () => {
    const { sent } = installWx();
    const client = createWxClient({ baseUrl: "https://api.example.com" });
    const p = client.get<{ id: number }>("/v1/items");
    sent[0].success!({ statusCode: 200, data: envelope(0, { id: 1 }), header: { "X-Trace-Id": "t" } });
    await expect(p).resolves.toEqual({ id: 1 });
    expect(sent[0].url).toBe("https://api.example.com/v1/items");
    expect(sent[0].method).toBe("GET");
    expect(sent[0].header?.["X-Trace-Id"]).toMatch(/^[0-9a-f]{32}$/);
  });

  it("body 序列化为 JSON 字符串原样发送（禁双重序列化）", async () => {
    const { sent } = installWx();
    const client = createWxClient();
    const p = client.post("/v1/orders", { sku: "a", qty: 2 });
    sent[0].success!({ statusCode: 200, data: envelope(0, { ok: true }) });
    await expect(p).resolves.toEqual({ ok: true });
    expect(sent[0].data).toBe(JSON.stringify({ sku: "a", qty: 2 }));
    expect(sent[0].header?.["Content-Type"]).toBe("application/json");
  });

  it("超时单配置点：默认 30000，自定义透传", async () => {
    const { sent } = installWx();
    const client = createWxClient({ timeoutMs: 8000 });
    const p = client.post("/v1/x", {});
    const p2 = createWxClient().post("/v1/y", {});
    sent[0].success!({ statusCode: 200, data: envelope(0) });
    sent[1].success!({ statusCode: 200, data: envelope(0) });
    await Promise.all([p, p2]);
    expect(sent[0].timeout).toBe(8000);
    expect(sent[1].timeout).toBe(30000);
  });

  it("traceId 会话复用：两次请求同值（client-shared 二-1）", async () => {
    const { sent } = installWx();
    const client = createWxClient();
    const p1 = client.get("/a");
    const p2 = client.get("/b");
    sent[0].success!({ statusCode: 200, data: envelope(0) });
    sent[1].success!({ statusCode: 200, data: envelope(0) });
    await Promise.all([p1, p2]);
    expect(sent[0].header?.["X-Trace-Id"]).toBe(sent[1].header?.["X-Trace-Id"]);
  });

  it("业务错误抛 ApiError 四要素（httpStatus 来自 statusCode）", async () => {
    const { sent } = installWx();
    const client = createWxClient();
    const p = client.post<never>("/v1/orders", {});
    sent[0].success!({ statusCode: 409, data: envelope(1007, null, "幂等冲突", "trace-9") });
    await expect(p).rejects.toMatchObject({
      name: "ApiError",
      code: 1007,
      message: "幂等冲突",
      traceId: "trace-9",
      httpStatus: 409,
    });
  });

  it("网络层失败映射 -1（写操作不自动重试）", async () => {
    const { sent } = installWx();
    const client = createWxClient();
    const p = client.post<never>("/v1/orders", {});
    sent[0].fail!({ errMsg: "request:fail timeout" });
    await expect(p).rejects.toMatchObject({ code: -1 });
    expect(sent.length).toBe(1);
  });

  it("abort 映射 CancelledError：取消不是错误，原样传导禁转译（client-shared 一-4）", async () => {
    const { sent } = installWx();
    const client = createWxClient();
    const p = client.post<never>("/v1/orders", {});
    sent[0].fail!({ errMsg: "request:fail abort" });
    await expect(p).rejects.toBeInstanceOf(CancelledError);
  });

  it("wx 预解析的 res.data（对象形态）还原解包等价", async () => {
    const { sent } = installWx();
    const client = createWxClient();
    const p = client.get("/items");
    // wx 按 content-type 已 JSON.parse：data 是对象而非字符串
    sent[0].success!({ statusCode: 200, data: envelope(0, [1, 2]) });
    await expect(p).resolves.toEqual([1, 2]);
  });

  it("2001 触发 onUnauthorized 刷新重放一次（client-shared 一-6）", async () => {
    const { sent } = installWx();
    const onUnauthorized = vi.fn(() => true);
    const client = createWxClient({ onUnauthorized, getHeaders: () => ({ Authorization: "Bearer t" }) });
    const p = client.post("/v1/orders", {});
    sent[0].success!({ statusCode: 200, data: envelope(2001, null, "未认证") });
    await flush();
    expect(sent.length).toBe(2);
    sent[1].success!({ statusCode: 200, data: envelope(0, { id: 9 }) });
    await expect(p).resolves.toEqual({ id: 9 });
    expect(onUnauthorized).toHaveBeenCalledTimes(1);
  });

  it("无 globalThis.wx 时 fail-fast", () => {
    expect(() => createWxClient()).toThrow(/小程序|小游戏环境/);
  });
});

describe("createWxStorageBackend（miniprogram.md 三-6 前缀隔离）", () => {
  it("经 createAppStorage 强制服务名前缀，裸 key 不落 wx storage", () => {
    const { storage } = installWx();
    const appStorage = createAppStorage("ysaas-companion", createWxStorageBackend());
    appStorage.set("token", { accessToken: "x" });
    appStorage.set("filter-state", { page: 2 });
    expect([...storage.keys()].sort()).toEqual(["ysaas-companion:filter-state", "ysaas-companion:token"]);
    expect(appStorage.get<{ accessToken: string }>("token")).toEqual({ accessToken: "x" });
    appStorage.remove("token");
    expect(appStorage.get("token")).toBeNull();
  });

  it("createWxTransport 同源可用（transport 直连形态）", async () => {
    const { sent } = installWx();
    const transport = createWxTransport();
    const p = transport.send({ method: "GET", url: "/x", headers: {}, timeoutMs: 1000 });
    sent[0].success!({ statusCode: 200, data: envelope(0), header: { "X-Trace-Id": "t" } });
    const response = await p;
    expect(response.status).toBe(200);
    expect(response.headers["x-trace-id"]).toBe("t");
    expect(await response.text()).toBe(JSON.stringify(envelope(0)));
  });
});
