/** 契约断言前端版：错误码表与 contract/api/error-codes.md v1.0 逐码核对（防漂移） */
import { describe, expect, it } from "vitest";
import { errorCodes, CODE_SUCCESS } from "../src/error-codes";
import { unwrap } from "../src/rest-response";
import { ApiError } from "../src/api-error";

const CONTRACT_TABLE: Record<string, number> = {
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
};

describe("@yarch/contract", () => {
  it("错误码表与契约逐码一致", () => {
    expect(Object.keys(errorCodes).sort()).toEqual(Object.keys(CONTRACT_TABLE).sort());
    for (const [name, code] of Object.entries(CONTRACT_TABLE)) {
      expect(errorCodes[name as keyof typeof errorCodes]).toBe(code);
    }
  });

  it("成功信封解包 data", () => {
    const data = unwrap({ code: CODE_SUCCESS, message: "成功", data: { id: 1 }, traceId: "t" });
    expect(data).toEqual({ id: 1 });
  });

  it("失败信封抛 ApiError 且携带 traceId 报障凭证", () => {
    try {
      unwrap({ code: 1004, message: "资源不存在：user 1", data: null, traceId: "abc123" });
      expect.unreachable("应抛 ApiError");
    } catch (e) {
      const apiError = e as ApiError;
      expect(apiError.name).toBe("ApiError");
      expect(apiError.code).toBe(1004);
      expect(apiError.traceId).toBe("abc123");
      expect(apiError.shouldRedirectToLogin).toBe(false);
    }
  });

  it("2001/2002 判定引导重登录", () => {
    expect(new ApiError(2001, "未认证").shouldRedirectToLogin).toBe(true);
    expect(new ApiError(2002, "凭证已过期").shouldRedirectToLogin).toBe(true);
    expect(new ApiError(2003, "权限不足").isForbidden).toBe(true);
  });
});
