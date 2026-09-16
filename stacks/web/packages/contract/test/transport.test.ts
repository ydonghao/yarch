/** 契约内核骨架语义断言（client-shared 一·二·三 / miniprogram.md 三的机检单测落点） */
import { describe, expect, it, vi } from "vitest";
import {
  CancelledError,
  TransportError,
  createCoreClient,
  type HttpTransport,
  type TransportRequest,
  type TransportResponse,
} from "../src/transport";
import { ApiError } from "../src/api-error";

function fakeTransport(
  handler: (req: TransportRequest, callNo: number) => Promise<TransportResponse> | TransportResponse,
) {
  const sent: TransportRequest[] = [];
  const transport: HttpTransport = {
    send: (req) => {
      sent.push(req);
      return Promise.resolve(handler(req, sent.length));
    },
  };
  return { transport, sent };
}

const res = (status: number, body: unknown, headers: Record<string, string> = {}): TransportResponse => ({
  status,
  headers,
  text: async () => (typeof body === "string" ? body : JSON.stringify(body)),
});

const envelope = (code: number, data: unknown = null, message = "业务拒绝", traceId = "tid-1") => ({
  code,
  message,
  data,
  traceId,
});

const flush = () => new Promise<void>((resolve) => setTimeout(resolve, 0));

describe("createCoreClient 骨架", () => {
  it("成功信封解包 data", async () => {
    const { transport } = fakeTransport(() => res(200, envelope(0, { id: 1 })));
    const client = createCoreClient(transport);
    await expect(client.get("/items")).resolves.toEqual({ id: 1 });
  });

  it("业务错误抛 ApiError 四要素（code/message/traceId/httpStatus）", async () => {
    const { transport } = fakeTransport(() => res(409, envelope(1007, null, "幂等冲突", "trace-x")));
    const client = createCoreClient(transport);
    const p = client.post<never>("/orders", {});
    await expect(p).rejects.toMatchObject({
      name: "ApiError",
      code: 1007,
      traceId: "trace-x",
      httpStatus: 409,
      message: "幂等冲突",
    });
  });

  it("traceId 会话复用：同 client 两次请求同值且 32 位小写 hex（client-shared 二-1）", async () => {
    const { transport, sent } = fakeTransport(() => res(200, envelope(0, 1)));
    const client = createCoreClient(transport);
    await client.get("/a");
    await client.get("/b");
    const first = sent[0].headers["X-Trace-Id"];
    expect(first).toMatch(/^[0-9a-f]{32}$/);
    expect(sent[1].headers["X-Trace-Id"]).toBe(first);
  });

  it("传输错误映射本地保留码 -1", async () => {
    const { transport } = fakeTransport(() => {
      throw new TransportError("request:fail timeout");
    });
    const client = createCoreClient(transport, { retryGetOnTransportError: false });
    await expect(client.get("/items")).rejects.toMatchObject({ name: "ApiError", code: -1, httpStatus: 0 });
  });

  it("GET 传输错误自动重试一次成功（client-shared 三-2）", async () => {
    const { transport, sent } = fakeTransport((_req, callNo) =>
      callNo === 1 ? Promise.reject(new TransportError("断网")) : res(200, envelope(0, "ok")),
    );
    const client = createCoreClient(transport);
    await expect(client.get("/items")).resolves.toBe("ok");
    expect(sent.length).toBe(2);
  });

  it("POST 传输错误永不自动重试（防双写）", async () => {
    const { transport, sent } = fakeTransport(() => {
      throw new TransportError("断网");
    });
    const client = createCoreClient(transport);
    await expect(client.post("/orders", {})).rejects.toMatchObject({ code: -1 });
    expect(sent.length).toBe(1);
  });

  it("重试仍失败：-1 且 message 标注已重试", async () => {
    const { transport } = fakeTransport(() => {
      throw new TransportError("断网");
    });
    const client = createCoreClient(transport);
    await expect(client.get("/items")).rejects.toMatchObject({
      code: -1,
      message: expect.stringContaining("已重试"),
    });
  });

  it("取消原样传导：CancelledError 不转译为 ApiError 也不吞（client-shared 一-4）", async () => {
    const { transport } = fakeTransport(() => {
      throw new CancelledError("request:fail abort");
    });
    const client = createCoreClient(transport);
    await expect(client.get("/items")).rejects.toBeInstanceOf(CancelledError);
  });

  it("2001 触发 onUnauthorized 刷新成功后重放一次（client-shared 一-6）", async () => {
    const { transport, sent } = fakeTransport((_req, callNo) =>
      callNo === 1 ? res(200, envelope(2001, null, "未认证")) : res(200, envelope(0, { ok: true })),
    );
    const onUnauthorized = vi.fn(() => true);
    const client = createCoreClient(transport, { onUnauthorized });
    await expect(client.post("/orders", {})).resolves.toEqual({ ok: true });
    expect(sent.length).toBe(2);
    expect(onUnauthorized).toHaveBeenCalledTimes(1);
  });

  it("2001 刷新失败：抛终态 ApiError 且 onSessionExpired 单点回调", async () => {
    const { transport } = fakeTransport(() => res(200, envelope(2002, null, "凭证已过期")));
    const onSessionExpired = vi.fn();
    const client = createCoreClient(transport, { onUnauthorized: () => false, onSessionExpired });
    await expect(client.get("/items")).rejects.toMatchObject({ code: 2002 });
    expect(onSessionExpired).toHaveBeenCalledWith("code 2002");
  });

  it("重放后仍 2001：不再二次刷新（防环），终态标注", async () => {
    const { transport } = fakeTransport(() => res(200, envelope(2001, null, "未认证")));
    const onUnauthorized = vi.fn(() => true);
    const onSessionExpired = vi.fn();
    const client = createCoreClient(transport, { onUnauthorized, onSessionExpired });
    await expect(client.get("/items")).rejects.toMatchObject({ code: 2001 });
    expect(onUnauthorized).toHaveBeenCalledTimes(1);
    expect(onSessionExpired).toHaveBeenCalledWith(expect.stringContaining("重放后仍失效"));
  });

  it("并发失效合并为一次刷新", async () => {
    const { transport, sent } = fakeTransport((_req, callNo) =>
      callNo <= 2 ? res(200, envelope(2001, null, "未认证")) : res(200, envelope(0, "ok")),
    );
    const onUnauthorized = vi.fn(() => true);
    const client = createCoreClient(transport, { onUnauthorized });
    const p1 = client.get("/a");
    const p2 = client.get("/b");
    await flush();
    await Promise.all([expect(p1).resolves.toBe("ok"), expect(p2).resolves.toBe("ok")]);
    expect(onUnauthorized).toHaveBeenCalledTimes(1);
    expect(sent.length).toBe(4);
  });

  it("HTTP 401 无信封（网关故障面兜底）：映射 2001 且带响应头 traceId", async () => {
    const { transport } = fakeTransport(() => res(401, "<html>denied</html>", { "x-trace-id": "gw-1" }));
    const client = createCoreClient(transport);
    const p = client.get<never>("/items");
    await expect(p).rejects.toMatchObject({ code: 2001, traceId: "gw-1", httpStatus: 401 });
  });

  it("5xx 无信封：传输层错误 -1（client-shared 一-2）", async () => {
    const { transport } = fakeTransport(() => res(502, "Bad Gateway"));
    const client = createCoreClient(transport);
    await expect(client.post("/x", {})).rejects.toMatchObject({ code: -1, httpStatus: 502 });
  });

  it("getHeaders 每请求注入凭证", async () => {
    const { transport, sent } = fakeTransport(() => res(200, envelope(0, 1)));
    let token = "t1";
    const client = createCoreClient(transport, { getHeaders: () => ({ Authorization: `Bearer ${token}` }) });
    const p1 = client.get("/a");
    token = "t2";
    const p2 = client.get("/b");
    await Promise.all([p1, p2]);
    expect(sent[0].headers.Authorization).toBe("Bearer t1");
    expect(sent[1].headers.Authorization).toBe("Bearer t2");
  });

  it("超时单配置点：默认 30s，业务请求无 per-request 入口（client-shared 三-1）", async () => {
    const { transport, sent } = fakeTransport(() => res(200, envelope(0, 1)));
    const client = createCoreClient(transport);
    await client.get("/a");
    expect(sent[0].timeoutMs).toBe(30_000);
    const custom = createCoreClient(transport, { timeoutMs: 5_000 });
    await custom.get("/b");
    expect(sent[1].timeoutMs).toBe(5_000);
  });
});

describe("ApiError 四要素", () => {
  it("默认 httpStatus 0（未到达 HTTP 层），向后兼容三参构造", () => {
    const e = new ApiError(1004, "资源不存在");
    expect(e.httpStatus).toBe(0);
    expect(e).toBeInstanceOf(Error);
  });
});
