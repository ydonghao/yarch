/** 框架无关 fetch 封装：traceId 透传 + 信封解包 + 错误映射（401/402→重登录端口） */
import { ApiError } from "./api-error";
import { RestResponse } from "./rest-response";
import { newTraceId, TRACE_ID_HEADER } from "./trace-id";
import { navigateToLogin } from "./navigator";

export interface HttpOptions {
  baseUrl?: string;
  getHeaders?: () => Record<string, string>;
}

export function createClient(options: HttpOptions = {}) {
  const baseUrl = options.baseUrl ?? "";

  async function request<T>(method: string, path: string, body?: unknown): Promise<T> {
    const headers: Record<string, string> = {
      "Content-Type": "application/json",
      [TRACE_ID_HEADER]: newTraceId(),
      ...(options.getHeaders?.() ?? {}),
    };
    let response: Response;
    try {
      response = await fetch(baseUrl + path, {
        method,
        headers,
        body: body === undefined ? undefined : JSON.stringify(body),
      });
    } catch (cause) {
      throw new ApiError(-1, `网络错误：${String(cause)}`);
    }

    if (response.status === 401) {
      navigateToLogin("unauthorized");
      throw new ApiError(2001, "未认证", response.headers.get(TRACE_ID_HEADER) ?? "");
    }

    const envelope = (await response.json()) as RestResponse<T>;
    if (envelope.code !== 0) {
      const apiError = new ApiError(envelope.code, envelope.message, envelope.traceId);
      if (apiError.shouldRedirectToLogin) {
        navigateToLogin(`code ${envelope.code}`);
      }
      throw apiError;
    }
    return envelope.data as T;
  }

  return {
    get: <T>(path: string) => request<T>("GET", path),
    post: <T>(path: string, body: unknown) => request<T>("POST", path, body),
    put: <T>(path: string, body: unknown) => request<T>("PUT", path, body),
    delete: <T>(path: string) => request<T>("DELETE", path),
  };
}
