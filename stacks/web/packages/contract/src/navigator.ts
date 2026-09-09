/**
 * 导航端口（依赖倒置）：共享包零框架依赖，路由跳转由各框架适配层注入实现
 * （对应 java 侧 ProductCache 端口的同一手法）。
 * 微前端条文：跨应用跳转必须走本端口（micro-frontend.md 四-3）；
 * 401 跳登录权唯一归基座（十-2）——子应用只抛 ApiError，禁自行跳转。
 */
export interface YarchNavigator {
  /** 应用内/跨应用路径跳转（如 /ysaas-billing/invoices）；微前端下由基座实现，按路由前缀分发 */
  navigate(path: string): void;
  /** 401/2002 时引导到登录页（记录回跳地址） */
  redirectToLogin(reason: string): void;
}

let navigator: YarchNavigator | null = null;

export function setNavigator(impl: YarchNavigator): void {
  navigator = impl;
}

export function navigateTo(path: string): void {
  navigator?.navigate(path);
}

export function navigateToLogin(reason: string): void {
  navigator?.redirectToLogin(reason);
}
