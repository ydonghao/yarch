/** Vue 适配层：把导航端口（跨应用跳转 四-3 + 401→重登录 十-2）接到 vue-router */
import type { YarchNavigator } from "@yarch/contract";
import { setNavigator } from "@yarch/contract";

export function setupVueNavigator(push: (path: string) => void): void {
  const impl: YarchNavigator = {
    navigate(path: string) {
      push(path);
    },
    redirectToLogin(reason: string) {
      push(`/login?reason=${encodeURIComponent(reason)}`);
    },
  };
  setNavigator(impl);
}

export * from "@yarch/contract";
