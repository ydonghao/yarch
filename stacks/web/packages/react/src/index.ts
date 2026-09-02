/** React 适配层：把 401→重登录 的导航端口接到 react-router */
import type { YarchNavigator } from "@yarch/contract";
import { setNavigator } from "@yarch/contract";

export function setupReactNavigator(navigate: (path: string) => void): void {
  const impl: YarchNavigator = {
    redirectToLogin(reason: string) {
      navigate(`/login?reason=${encodeURIComponent(reason)}`);
    },
  };
  setNavigator(impl);
}

export * from "@yarch/contract";
