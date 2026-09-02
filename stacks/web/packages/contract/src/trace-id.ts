/** traceId（logging-trace.md 前端行）：生成/透传 X-Trace-Id，错误对象暴露 traceId 供报障 */
const TRACE_HEADER = "X-Trace-Id";

export function newTraceId(): string {
  const bytes = new Uint8Array(16);
  crypto.getRandomValues(bytes);
  return Array.from(bytes, (b) => b.toString(16).padStart(2, "0")).join("");
}

export function traceHeader(): Record<string, string> {
  return { [TRACE_HEADER]: newTraceId() };
}

export const TRACE_ID_HEADER = TRACE_HEADER;
