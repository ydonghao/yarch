/** React 适配层：把导航端口（跨应用跳转 四-3 + 401→重登录 十-2）接到 react-router */
import type { YarchNavigator } from "@yarch/contract";
import { setNavigator } from "@yarch/contract";

export function setupReactNavigator(navigate: (path: string) => void): void {
  const impl: YarchNavigator = {
    navigate(path: string) {
      navigate(path);
    },
    redirectToLogin(reason: string) {
      navigate(`/login?reason=${encodeURIComponent(reason)}`);
    },
  };
  setNavigator(impl);
}

export * from "@yarch/contract";
