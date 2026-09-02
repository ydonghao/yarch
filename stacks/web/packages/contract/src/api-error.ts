/** ApiError：业务错误的统一形态 —— code 对应错误码表，traceId 是报障凭证 */
export class ApiError extends Error {
  readonly code: number;
  readonly traceId: string;

  constructor(code: number, message: string, traceId = "") {
    super(message);
    this.name = "ApiError";
    this.code = code;
    this.traceId = traceId;
  }

  /** 契约：token 过期引导重登录，禁无脑重试（rest-conventions.md 认证段） */
  get shouldRedirectToLogin(): boolean {
    return this.code === 2001 || this.code === 2002;
  }

  get isForbidden(): boolean {
    return this.code === 2003 || this.code === 2004;
  }
}
