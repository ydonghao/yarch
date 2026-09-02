/**
 * 导航端口（依赖倒置）：共享包零框架依赖，401→重登录 的路由跳转由各框架适配层注入实现
 * （对应 java 侧 ProductCache 端口的同一手法）。
 */
export interface YarchNavigator {
  /** 401/2002 时引导到登录页（记录回跳地址） */
  redirectToLogin(reason: string): void;
}

let navigator: YarchNavigator | null = null;

export function setNavigator(impl: YarchNavigator): void {
  navigator = impl;
}

export function navigateToLogin(reason: string): void {
  navigator?.redirectToLogin(reason);
}
