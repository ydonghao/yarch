/** 错误码常量表 —— 与 contract/api/error-codes.md v1.0 同源（0/1xxx/2xxx 归 yarch；3xxx+ 业务侧扩展） */
export const CODE_SUCCESS = 0;

export const errorCodes = {
  INTERNAL_ERROR: 1000,
  INVALID_ARGUMENT: 1001,
  MALFORMED_BODY: 1002,
  NOT_FOUND: 1004,
  CONFLICT: 1005,
  RATE_LIMITED: 1006,
  IDEMPOTENCY_CONFLICT: 1007,
  UPSTREAM_TIMEOUT: 1008,
  UNAVAILABLE: 1009,
  UNAUTHORIZED: 2001,
  CREDENTIALS_EXPIRED: 2002,
  FORBIDDEN: 2003,
  ACCOUNT_DISABLED: 2004,
} as const;

export type YarchErrorCode = (typeof errorCodes)[keyof typeof errorCodes];
