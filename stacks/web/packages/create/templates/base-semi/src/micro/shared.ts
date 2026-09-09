/**
 * 共享运行时注册（八-1/八-2）：整个微前端体系只允许一份 @yarch/contract 与框架运行时，
 * 由基座注册到 window 全局；子应用集成构建（--mode integrated）经同名 shim 取用，
 * 产物中框架与 contract 体积消失（十三表八-1/八-2 机检的运行时基础）。
 */
import * as contract from "@yarch/contract";
import * as react from "react";
import * as reactDom from "react-dom";
import * as reactDomClient from "react-dom/client";
import * as reactJsxRuntime from "react/jsx-runtime";
import * as reactJsxDevRuntime from "react/jsx-dev-runtime";
import * as reactRouter from "react-router";
import * as reactRouterDom from "react-router-dom";
import type { SubAppUser } from "@yarch/contract";
import { authStore } from "../stores/auth";
import { themeToken } from "../ui/theme";

export const SHARED_REACT_KEY = "__YARCH_REACT__";
export const SUPPLY_KEY = "__YARCH_SUPPLY__";

export function registerSharedRuntimes(): void {
  const g = globalThis as Record<string, unknown>;
  g[SHARED_REACT_KEY] = {
    "react": react,
    "react-dom": reactDom,
    "react-dom/client": reactDomClient,
    "react/jsx-runtime": reactJsxRuntime,
    "react/jsx-dev-runtime": reactJsxDevRuntime,
    "react-router": reactRouter,
    "react-router-dom": reactRouterDom,
  };
  // 八-1：contract 单例标记（子应用 shim 与 e2e instanceof 断言的取用点）
  contract.setSharedContract(contract);

  // props 下行供给（五-5 最小集；getter 保证子应用每次取值都是最新登录态，十-1 token 只归基座）
  g[SUPPLY_KEY] = {
    getToken: (): string | null => authStore.token || null,
    get user(): SubAppUser | null {
      return authStore.token ? { id: "demo", name: "演示用户" } : null;
    },
    themeToken,
  };
}
