/** 信封与分页负载（rest-response.md v1.0）：code/message/data/traceId 四字段，前端只认这个形状 */
import { ApiError } from "./api-error";

export interface RestResponse<T> {
  code: number;
  message: string;
  data: T | null;
  traceId: string;
}

export interface PageData<T> {
  list: T[];
  total: number;
  page: number;
  pageSize: number;
  nextCursor?: string;
}

/** 解包：code != 0 抛 ApiError（含 traceId 报障凭证）；data 为 null 抛 ApiError */
export function unwrap<T>(envelope: RestResponse<T>): T {
  if (envelope.code !== 0) {
    throw new ApiError(envelope.code, envelope.message, envelope.traceId);
  }
  if (envelope.data === null) {
    throw new ApiError(-1, "信封 data 为 null（成功无负载场景请用 unwrapAllowNull）", envelope.traceId);
  }
  return envelope.data as T;
}

export function unwrapAllowNull<T>(envelope: RestResponse<T>): T | null {
  if (envelope.code !== 0) {
    throw new ApiError(envelope.code, envelope.message, envelope.traceId);
  }
  return envelope.data;
}
