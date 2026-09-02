/** Vue 适配层：把 401→重登录 的导航端口接到 vue-router */
import type { YarchNavigator } from "@yarch/contract";
import { setNavigator } from "@yarch/contract";

export function setupVueNavigator(push: (path: string) => void): void {
  const impl: YarchNavigator = {
    redirectToLogin(reason: string) {
      push(`/login?reason=${encodeURIComponent(reason)}`);
    },
  };
  setNavigator(impl);
}

export * from "@yarch/contract";
