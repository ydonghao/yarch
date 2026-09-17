/**
 * 契约断言前端版：13 码全表唯一权威 = contract/dist/error-codes.json（由 error-codes.md
 * 派生，CI 拒双向漂移）——四栈读同一份 json 断言，不再各养手抄表（P1 契约机器可读出口）。
 * dist 文件不在场（消费方独立环境）则跳过表断言，CI 仓内必跑。
 */
import { existsSync, readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { describe, expect, it } from "vitest";
import { errorCodes, CODE_SUCCESS } from "../src/error-codes";
import { unwrap } from "../src/rest-response";
import { ApiError } from "../src/api-error";

const DIST_PATH = fileURLToPath(new URL("../../../../../contract/dist/error-codes.json", import.meta.url));

function distTable(): Record<string, number> {
  if (!existsSync(DIST_PATH)) return {};
  const dist = JSON.parse(readFileSync(DIST_PATH, "utf8")) as {
    codes: { key: string; code: number }[];
  };
  const table: Record<string, number> = {};
  for (const c of dist.codes) table[c.key] = c.code;
  return table;
}

const CONTRACT_TABLE: Record<string, number> = distTable();
const DIST_PRESENT = Object.keys(CONTRACT_TABLE).length > 0;

describe.skipIf(!DIST_PRESENT)("dist 同源断言（contract/dist/error-codes.json）", () => {
  it("错误码表与 dist 契约逐码一致（13 码）", () => {
    expect(Object.keys(CONTRACT_TABLE)).toHaveLength(13);
    expect(Object.keys(errorCodes).sort()).toEqual(Object.keys(CONTRACT_TABLE).sort());
    for (const [name, code] of Object.entries(CONTRACT_TABLE)) {
      expect(errorCodes[name as keyof typeof errorCodes]).toBe(code);
    }
  });
});

describe("@yarch/contract", () => {
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
