/**
 * Cocos Creator 适配器（game.md 三-2 的 Cocos 档载体）：
 * 运行时环境检测——微信小游戏环境用 wx.request（./wx），H5/浏览器环境用 fetch（./http）。
 * 游戏代码只引本出口，构建目标切换（小游戏↔H5）无需改业务代码。
 */
import { createCoreClient } from "./transport";
import { createWxClient, createWxStorageBackend } from "./wx";
import { createClient, fetchTransport } from "./http";
import type { CoreClient, CoreClientOptions } from "./transport";
import { createAppStorage } from "./storage";

/** 当前运行时环境（game.md 二-1 Cocos 默认档面向双构建目标） */
export type CocosRuntime = "wechat-minigame" | "h5";

export function detectCocosRuntime(): CocosRuntime {
  if (typeof (globalThis as { wx?: { request?: unknown } }).wx?.request === "function") {
    return "wechat-minigame";
  }
  if (typeof (globalThis as { fetch?: unknown }).fetch === "function") {
    return "h5";
  }
  throw new Error(
    "Cocos 运行时不可识别：既无 globalThis.wx.request（微信小游戏）也无 fetch（H5）——" +
      "请确认构建目标平台；Cocos 原生（非 H5/非小游戏）档暂不支持，须自行注入 HttpTransport",
  );
}

/**
 * 创建 Cocos 契约客户端（自动检测运行时）。
 * - 微信小游戏：createWxClient（wx.request transport + wx storage）
 * - H5：createClient（fetch transport + localStorage）
 * options 与 web/wx 完全一致（client-shared 口径，照搬不另设——game.md 三-2）。
 */
export function createCocosClient(options: CoreClientOptions = {}): CoreClient {
  const runtime = detectCocosRuntime();
  if (runtime === "wechat-minigame") return createWxClient(options);
  return createClient(options);
}

/**
 * 创建 Cocos 端 storage（自动检测运行时，key 强制服务名前缀隔离——game.md 三-3 / miniprogram.md 三-6）。
 */
export function createCocosStorage(appName: string) {
  const runtime = detectCocosRuntime();
  if (runtime === "wechat-minigame") {
    return createAppStorage(appName, createWxStorageBackend());
  }
  return createAppStorage(appName);
}

export { createCoreClient, fetchTransport, createWxClient, createWxStorageBackend };
