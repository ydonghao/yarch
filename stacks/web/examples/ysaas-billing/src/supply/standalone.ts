/**
 * 独立运行供给（十一-1/十-6）：登录态 mock/开发态注入，路由 base 从环境取同值（四-2）。
 * 本模块（含 mock 外壳）不进集成产物——集成构建经 vite alias 指向 integrated.ts。
 */
import { createAppStorage, type SubAppMountProps } from "@yarch/contract";
import { setupReactNavigator } from "@yarch/react";
import type { Supply } from "./types";

const storage = createAppStorage("ysaas-billing"); // 二-2：独立模式 storage 同样带 {应用名}: 前缀，无例外
if (storage.get<string>("mock-token") === null) {
  storage.set("mock-token", "standalone-dev"); // 十-6：独立模式登录态以开发态注入
}

// vite base = "/ysaas-billing/"，与集成模式基座注入的路由前缀同值（四-2 同值口径）
const baseURL = import.meta.env.BASE_URL.replace(/\/$/, "");

export const supply: Supply = {
  getMountProps(): SubAppMountProps {
    return {
      container: document.getElementById("root")!,
      base: baseURL,
      user: { id: "dev", name: "独立模式" },
      getToken: () => storage.get<string>("mock-token"),
    };
  },
  getAccessToken() {
    return storage.get<string>("mock-token");
  },
  setupAppNavigator(navigate) {
    setupReactNavigator(navigate); // 独立模式由本应用路由实现导航端口（401→/login）
  },
  onUnmount() {
    return () => {}; // 独立模式无卸载方
  },
  onRemount() {
    return () => {};
  },
};
