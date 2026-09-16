/** ApiError：业务错误的统一形态 —— code 对应错误码表，traceId 是报障凭证，httpStatus 是传输层佐证 */
export class ApiError extends Error {
  readonly code: number;
  readonly traceId: string;
  /** HTTP 状态码（client-shared 一-3 四要素；0 = 未到达 HTTP 层，如传输错误本地保留码 -1） */
  readonly httpStatus: number;

  constructor(code: number, message: string, traceId = "", httpStatus = 0) {
    super(message);
    this.name = "ApiError";
    this.code = code;
    this.traceId = traceId;
    this.httpStatus = httpStatus;
  }

  /** 契约：token 过期引导重登录，禁无脑重试（rest-conventions.md 认证段） */
  get shouldRedirectToLogin(): boolean {
    return this.code === 2001 || this.code === 2002;
  }

  get isForbidden(): boolean {
    return this.code === 2003 || this.code === 2004;
  }
}
