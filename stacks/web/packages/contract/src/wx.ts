/**
 * wx.request 适配器（miniprogram.md 三的载体 / MP4 三端同源的小程序方言）：
 * 超时单点 / X-Trace-Id 会话注入 / 错误三分类 / 认证失效刷新重放 / 取消原样传导。
 * 消费：`import { createWxClient, createWxStorageBackend } from "@yarch/contract/wx"`——
 * **禁从主入口引**（主出口含微前端运行时件，会把 window 语义带进小程序 bundle）。
 * 本文件零 DOM 依赖；wx 全局以最小接口形状解析（不依赖 miniprogram-api-typings）。
 */
import { createCoreClient, CancelledError, TransportError } from "./transport";
import type {
  AbortSignalLike,
  CoreClient,
  CoreClientOptions,
  HttpTransport,
  TransportRequest,
  TransportResponse,
} from "./transport";
import type { StorageLike } from "./storage";

type WxRequestMethod = "GET" | "POST" | "PUT" | "DELETE" | "HEAD" | "OPTIONS" | "PATCH";

interface WxRequestFail {
  errMsg?: string;
}

interface WxLike {
  request(options: {
    url: string;
    method?: WxRequestMethod;
    header?: Record<string, string>;
    data?: string;
    timeout?: number;
    success?(res: { statusCode: number; data: unknown; header?: Record<string, unknown> }): void;
    fail?(err: WxRequestFail): void;
  }): { abort(): void };
  setStorageSync(key: string, value: string): void;
  getStorageSync(key: string): unknown;
  removeStorageSync(key: string): void;
}

function resolveWx(): WxLike {
  const impl = (globalThis as { wx?: WxLike }).wx;
  if (!impl) {
    throw new Error("createWxClient / createWxStorageBackend 须在微信小程序/小游戏环境调用（globalThis.wx 不存在）");
  }
  return impl;
}

/** AbortSignal 可选支持（基础库版本差异，不支持时取消降级为不取消——不违反取消语义：宁可多收一次响应） */
function hookSignal(task: { abort(): void }, signal: AbortSignalLike | undefined): void {
  if (!signal || typeof signal.addEventListener !== "function") return;
  if (signal.aborted) {
    task.abort();
    return;
  }
  signal.addEventListener("abort", () => task.abort());
}

/** wx 响应头键大小写不定、值可能 string|string[]：归一为小写键逗号_join 单值 */
function normalizeHeaders(header: Record<string, unknown> | undefined): Record<string, string> {
  const out: Record<string, string> = {};
  if (!header) return out;
  for (const [key, value] of Object.entries(header)) {
    out[key.toLowerCase()] = Array.isArray(value) ? value.join(",") : String(value);
  }
  return out;
}

export function createWxTransport(): HttpTransport {
  const wx = resolveWx();
  return {
    send(req: TransportRequest): Promise<TransportResponse> {
      return new Promise((resolve, reject) => {
        const task = wx.request({
          url: req.url,
          method: req.method as WxRequestMethod,
          header: req.headers,
          // body 已由内核序列化为 JSON 字符串；wx 对 string data 原样发送（禁再对象化触发双重序列化）
          data: req.body,
          timeout: req.timeoutMs,
          success: (res) => {
            resolve({
              status: res.statusCode,
              headers: normalizeHeaders(res.header),
              // wx 按 content-type 预解析 res.data：已是对象时还原为字符串供内核统一 JSON.parse；
              // 空体（data 为 ""/undefined）归一为空串 → 内核按信封不可解析处理
              text: async () =>
                typeof res.data === "string" ? res.data : JSON.stringify(res.data ?? ""),
            });
          },
          fail: (err) => {
            const msg = typeof err?.errMsg === "string" ? err.errMsg : String(err);
            if (msg.includes("abort")) {
              // 取消不是错误：专用异常原样上抛，禁转译为 ApiError（client-shared 一-4）
              reject(new CancelledError(msg));
            } else {
              reject(new TransportError(msg));
            }
          },
        });
        hookSignal(task, req.signal);
      });
    },
  };
}

export function createWxClient(options: CoreClientOptions = {}): CoreClient {
  return createCoreClient(createWxTransport(), options);
}

/** wx storage 适配（miniprogram.md 三-6：凭证存储 key 以已登记服务名前缀隔离，防宿主生态串号） */
export function createWxStorageBackend(): StorageLike {
  const wx = resolveWx();
  return {
    getItem(key: string): string | null {
      const value = wx.getStorageSync(key);
      return value === "" || value === null || value === undefined ? null : (value as string);
    },
    setItem(key: string, value: string): void {
      wx.setStorageSync(key, value);
    },
    removeItem(key: string): void {
      wx.removeStorageSync(key);
    },
  };
}

export { CancelledError, TransportError };
