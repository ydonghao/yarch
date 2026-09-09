import type { SubAppMountProps } from "@yarch/contract";

/**
 * 环境供给层端口（micro-frontend.md 十一-2）：独立/集成双模式差异的唯一容身处——
 * 业务代码只消费本接口，出现 `if (isMicro)` 分支即设计缺陷。
 */
export interface Supply {
  /** 挂载参数（五-5 最小集：容器/base/用户上下文/取 token/主题 token） */
  getMountProps(): SubAppMountProps;
  /** 取 token（十-1：独立=开发态 mock 注入；集成=基座下发函数引用） */
  getAccessToken(): string | null;
  /** 导航端口注册（四-3/十-2：独立=注册本应用路由跳转；集成=noop——导航权与 401 跳转唯一归基座） */
  setupAppNavigator(navigate: (path: string) => void): void;
  /** 卸载清理钩（五-2：micro-app unmount 事件触发；返回取消函数，七-4 成对） */
  onUnmount(cb: () => void): () => void;
  /** 重新挂载钩（五-3 mount/unmount 幂等：micro-app 复用脚本缓存重挂时重渲染） */
  onRemount(cb: () => void): () => void;
}
