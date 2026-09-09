/**
 * 集成运行供给（十一-2）：登录态/主题/token 全部由基座经 __YARCH_SUPPLY__ 下发（五-5/十-1）；
 * 路由 base = 应用名前缀（四-1/四-2 由基座口径注入，本档直接取同值）。
 * 仅存在于集成构建（vite --mode integrated alias 到此）。
 */
import type { SubAppMountProps, SubAppUser } from "@yarch/contract";
import type { Supply } from "./types";

interface BaseSupply {
  getToken(): string | null;
  user: SubAppUser | null;
  themeToken?: Record<string, string>;
}

function baseSupply(): BaseSupply {
  const s = (globalThis as Record<string, unknown>).__YARCH_SUPPLY__ as BaseSupply | undefined;
  if (!s) {
    throw new Error("基座未注册 __YARCH_SUPPLY__（五-5 props 下行）——集成构建必须在基座内运行");
  }
  return s;
}

export const supply: Supply = {
  getMountProps(): SubAppMountProps {
    const s = baseSupply();
    return {
      container: document.getElementById("root")!, // micro-app 沙箱内自取（五-1 形式由模板档固化）
      base: "/ysaas-billing", // 四-1：路由前缀 = 应用名
      user: s.user,
      getToken: () => s.getToken(),
      themeToken: s.themeToken,
    };
  },
  getAccessToken() {
    return baseSupply().getToken();
  },
  setupAppNavigator() {
    // 十-2：导航端口与 401 跳转唯一归基座（contract 单例已由基座注册），子应用不注册
  },
  onUnmount(cb) {
    window.addEventListener("unmount", cb); // micro-app 卸载事件（五-2：定时器/监听/DOM 彻底清理）
    return () => window.removeEventListener("unmount", cb);
  },
  onRemount(cb) {
    window.addEventListener("mounted", cb); // 五-3：脚本缓存复用下的重挂渲染
    return () => window.removeEventListener("mounted", cb);
  },
};
