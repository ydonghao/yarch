/**
 * @yarch/contract 运行时单例标记（micro-frontend.md 八-1）：整个微前端体系只允许一份实例，
 * 由基座注册共享（集成构建的子应用经 window 全局取用）；子应用私带第二份 =
 * 两个 ApiError 类，instanceof 全失效。
 */
export const SHARED_CONTRACT_KEY = "__YARCH_CONTRACT__";

/** 基座侧注册：传入 `import * as contract from "@yarch/contract"` 的命名空间 */
export function setSharedContract(namespace: object): void {
  (globalThis as Record<string, unknown>)[SHARED_CONTRACT_KEY] = namespace;
}

/** 子应用集成构建 shim / e2e 单例断言用；未注册返回 undefined */
export function getSharedContract<T = unknown>(): T | undefined {
  return (globalThis as Record<string, unknown>)[SHARED_CONTRACT_KEY] as T | undefined;
}
