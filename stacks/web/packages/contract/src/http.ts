/**
 * fetch transport（web/H5/Node）+ 兼容旧签名的 createClient（micro-frontend.md / client-shared 协作参考的 web 方言）。
 * 小程序/小游戏域禁用本入口（用 @yarch/contract/wx）。
 * 行为口径（0.3.0 起对齐 client-shared）：traceId 会话内复用（二-1）、GET 传输错误自动重试一次（三-2）、
 * 2001/2002 走 onUnauthorized 刷新重放一次（一-6，无钩子时维持 navigateToLogin 终态）。
 */
import { CancelledError, TransportError, createCoreClient } from "./transport";
import type { CoreClientOptions, HttpTransport, TransportRequest, TransportResponse } from "./transport";
import { navigateToLogin } from "./navigator";

export type HttpOptions = CoreClientOptions;

export const fetchTransport: HttpTransport = {
  async send(req: TransportRequest): Promise<TransportResponse> {
    const controller = new AbortController();
    let timedOut = false;
    const timer = setTimeout(() => {
      timedOut = true;
      controller.abort();
    }, req.timeoutMs);
    const onOuterAbort = () => controller.abort();
    if (req.signal) {
      if (req.signal.aborted) controller.abort();
      else req.signal.addEventListener("abort", onOuterAbort);
    }
    try {
      const response = await fetch(req.url, {
        method: req.method,
        headers: req.headers,
        body: req.body,
        signal: controller.signal,
      });
      const headers: Record<string, string> = {};
      response.headers.forEach((value, key) => {
        headers[key.toLowerCase()] = value;
      });
      return { status: response.status, headers, text: () => response.text() };
    } catch (cause) {
      // 判定顺序：外部取消优先（原样传导），其次超时（传输错误），最后其他网络错误
      if (req.signal?.aborted) throw new CancelledError("request cancelled");
      if (timedOut) throw new TransportError(`请求超时（${req.timeoutMs}ms）`);
      throw new TransportError(cause instanceof Error ? cause.message : String(cause));
    } finally {
      clearTimeout(timer);
      req.signal?.removeEventListener?.("abort", onOuterAbort);
    }
  },
};

export function createClient(options: HttpOptions = {}) {
  return createCoreClient(fetchTransport, {
    onSessionExpired: (reason) => navigateToLogin(reason),
    ...options,
  });
}
