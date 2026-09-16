/**
 * 环境无关契约内核骨架（miniprogram.md 三 / client-shared 一·二·三的落点）：
 * 信封解包单点 / 错误三分类 / traceId 会话复用 / 认证失效刷新重放一次 / GET 幂等读重试一次 / 取消原样传导。
 * Transport 由各端注入：fetch（web/H5/Node）见 ./http.ts，wx.request（小程序/小游戏）见 ./wx.ts。
 * 本文件零 DOM、零 Node、零 wx 依赖——AbortSignal 用自制最小形状（小程序工程 tsconfig 无 DOM lib 也可编译）。
 */
import { ApiError } from "./api-error";
import { CODE_SUCCESS, errorCodes } from "./error-codes";
import type { RestResponse } from "./rest-response";
import { newTraceId, TRACE_ID_HEADER } from "./trace-id";

/** 取消不是错误（client-shared 一-4）：不是 ApiError、不落 -1，调用方 catch 后按取消收尾（禁转译禁吞） */
export class CancelledError extends Error {
  constructor(reason = "request cancelled") {
    super(reason);
    this.name = "CancelledError";
  }
}

/** 传输层失败信号（断网/超时/TLS——各端 Transport 抛出，骨架统一映射为 ApiError(-1)） */
export class TransportError extends Error {
  constructor(reason: string) {
    super(reason);
    this.name = "TransportError";
  }
}

/** AbortSignal 最小形状（结构兼容 DOM AbortSignal；wx 基础库版本不支持时由适配器静默降级） */
export interface AbortSignalLike {
  readonly aborted: boolean;
  addEventListener(type: "abort", listener: () => void): void;
  removeEventListener?(type: "abort", listener: () => void): void;
}

export interface TransportRequest {
  method: string;
  url: string;
  headers: Record<string, string>;
  /** 已序列化的 JSON body（undefined = 无 body） */
  body?: string;
  /** 超时单配置点（client-shared 三-1）：由客户端持有，业务侧无 per-request 入口 */
  timeoutMs: number;
  signal?: AbortSignalLike;
}

export interface TransportResponse {
  status: number;
  /** 响应头，键一律小写（各端适配器负责归一） */
  headers: Record<string, string>;
  text(): Promise<string>;
}

export interface HttpTransport {
  send(req: TransportRequest): Promise<TransportResponse>;
}

export interface CoreClientOptions {
  baseUrl?: string;
  /** 超时默认 30s（client-shared 三-1 读写档；连接档由各端网络栈处理） */
  timeoutMs?: number;
  /** 凭证注入（Authorization 等），每请求调用 */
  getHeaders?: () => Record<string, string>;
  /** 会话 traceId（client-shared 二-1 本地生成一次、会话内复用）；不传则内核生成 */
  traceId?: string;
  /**
   * 认证失效（2001/2002）刷新钩子（client-shared 一-6）：返回 true 触发原请求重放一次（防环），
   * 抛错或返回 false 走 onSessionExpired 终态。并发失效合并为一次刷新。
   */
  onUnauthorized?: () => Promise<boolean> | boolean;
  /** 会话终态回调：清态 + 路由登录（跳转权唯一，禁业务层各自捕获 401 跳登录） */
  onSessionExpired?: (reason: string) => void;
  /** 幂等读（GET）传输失败自动重试一次（client-shared 三-2：最多 1 次），默认开；写操作永不自动重试（防双写） */
  retryGetOnTransportError?: boolean;
}

const DEFAULT_TIMEOUT_MS = 30_000;
const RETRY_DELAY_MS = 600;
const TRACE_ID_HEADER_LOWER = "x-trace-id";

const delay = (ms: number) => new Promise<void>((resolve) => setTimeout(resolve, ms));

export function createCoreClient(transport: HttpTransport, options: CoreClientOptions = {}) {
  const baseUrl = options.baseUrl ?? "";
  const timeoutMs = options.timeoutMs ?? DEFAULT_TIMEOUT_MS;
  const retryGet = options.retryGetOnTransportError ?? true;
  const sessionTraceId = options.traceId ?? newTraceId();
  let refreshing: Promise<boolean> | null = null;

  function tryRefresh(): Promise<boolean> {
    if (!options.onUnauthorized) return Promise.resolve(false);
    // 并发 401 合并一次刷新；settle 后清空，允许会话内后续再次过期时重新刷新
    refreshing ??= Promise.resolve(options.onUnauthorized()).finally(() => {
      refreshing = null;
    });
    return refreshing;
  }

  async function sendOnce<T>(
    method: string,
    path: string,
    body: unknown,
    signal: AbortSignalLike | undefined,
  ): Promise<T> {
    const headers: Record<string, string> = {
      "Content-Type": "application/json",
      [TRACE_ID_HEADER]: sessionTraceId,
      ...(options.getHeaders?.() ?? {}),
    };
    let response: TransportResponse;
    try {
      response = await transport.send({
        method,
        url: baseUrl + path,
        headers,
        body: body === undefined ? undefined : JSON.stringify(body),
        timeoutMs,
        signal,
      });
    } catch (cause) {
      if (cause instanceof CancelledError) throw cause; // 取消原样传导，禁转译禁吞
      throw new ApiError(
        -1,
        `网络错误：${cause instanceof Error ? cause.message : String(cause)}`,
        "",
        0,
      );
    }

    // HTTP 状态只是传输信号（client-shared 一-2）：2xx/4xx/5xx 一律解析 body，网关故障面也保证信封
    let raw: string;
    try {
      raw = await response.text();
    } catch (cause) {
      throw new ApiError(-1, `响应读取失败：${String(cause)}`, "", response.status);
    }
    let envelope: RestResponse<T>;
    try {
      envelope = JSON.parse(raw) as RestResponse<T>;
      if (typeof envelope?.code !== "number") throw new Error("非信封形状");
    } catch {
      if (response.status === 401) {
        throw new ApiError(
          errorCodes.UNAUTHORIZED,
          "未认证（网关 401 无信封）",
          response.headers[TRACE_ID_HEADER_LOWER] ?? "",
          401,
        );
      }
      throw new ApiError(-1, `信封不可解析（HTTP ${response.status}）`, "", response.status);
    }

    if (envelope.code !== CODE_SUCCESS) {
      throw new ApiError(envelope.code, envelope.message, envelope.traceId, response.status);
    }
    return envelope.data as T;
  }

  async function request<T>(
    method: string,
    path: string,
    body?: unknown,
    signal?: AbortSignalLike,
    isReplay = false,
  ): Promise<T> {
    try {
      return await sendOnce<T>(method, path, body, signal);
    } catch (cause) {
      if (cause instanceof CancelledError) throw cause;

      // 传输错误且幂等读：自动重试一次（client-shared 三-2；写操作永不自动重试）
      if (retryGet && method === "GET" && cause instanceof ApiError && cause.code === -1 && !signal?.aborted) {
        await delay(RETRY_DELAY_MS);
        if (signal?.aborted) throw new CancelledError();
        try {
          return await sendOnce<T>(method, path, body, signal);
        } catch (retryCause) {
          if (retryCause instanceof CancelledError) throw retryCause;
          if (retryCause instanceof ApiError && retryCause.code === -1) {
            throw new ApiError(-1, `网络错误（已重试）：${retryCause.message}`, "", 0);
          }
          throw retryCause;
        }
      }

      // 认证失效单点：刷新 → 重放一次；终态走 onSessionExpired（client-shared 一-6，防环）
      if (cause instanceof ApiError && cause.shouldRedirectToLogin) {
        if (!isReplay) {
          if (await tryRefresh()) return request<T>(method, path, body, signal, true);
        }
        options.onSessionExpired?.(`code ${cause.code}${isReplay ? "（重放后仍失效）" : ""}`);
        throw cause;
      }

      throw cause;
    }
  }

  return {
    get: <T>(path: string, signal?: AbortSignalLike) => request<T>("GET", path, undefined, signal),
    post: <T>(path: string, body: unknown, signal?: AbortSignalLike) =>
      request<T>("POST", path, body, signal),
    put: <T>(path: string, body: unknown, signal?: AbortSignalLike) =>
      request<T>("PUT", path, body, signal),
    delete: <T>(path: string, signal?: AbortSignalLike) => request<T>("DELETE", path, undefined, signal),
  };
}

export type CoreClient = ReturnType<typeof createCoreClient>;
