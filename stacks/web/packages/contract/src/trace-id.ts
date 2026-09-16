/** traceId（logging-trace.md 前端行）：生成/透传 X-Trace-Id，错误对象暴露 traceId 供报障 */
const TRACE_HEADER = "X-Trace-Id";

export function newTraceId(): string {
  // 优先密码学随机（浏览器/Node）；小程序/小游戏无 crypto 全局时回退 Math.random——
  // traceId 是排障关联标识而非安全凭证，熵需求以碰撞 rare 为准
  const cryptoApi = (globalThis as { crypto?: Crypto }).crypto;
  if (cryptoApi?.getRandomValues) {
    const bytes = new Uint8Array(16);
    cryptoApi.getRandomValues(bytes);
    return Array.from(bytes, (b) => b.toString(16).padStart(2, "0")).join("");
  }
  let hex = "";
  for (let i = 0; i < 16; i++) hex += Math.floor(Math.random() * 256).toString(16).padStart(2, "0");
  return hex;
}

export function traceHeader(): Record<string, string> {
  return { [TRACE_HEADER]: newTraceId() };
}

export const TRACE_ID_HEADER = TRACE_HEADER;
