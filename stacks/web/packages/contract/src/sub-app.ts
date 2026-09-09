/** 子应用挂载 props 最小集（micro-frontend.md 五-5）：禁把基座整个 store 实例扔给子应用。 */
import type { YarchNavigator } from "./navigator";

export interface SubAppUser {
  id: string;
  name: string;
}

export interface SubAppMountProps {
  /** 容器节点：子应用根挂载目标 */
  container: HTMLElement;
  /** 路由 base（四-2：由基座挂载时注入；独立运行时从环境变量取同值） */
  base: string;
  /** 导航端口实例（四-3；contract 单例已由基座注册时可缺省） */
  navigator?: YarchNavigator;
  /** 用户上下文（十-1：登录态归基座，子应用只读） */
  user: SubAppUser | null;
  /** 取 token 函数引用（十-1：子应用禁自行持久化 token） */
  getToken(): string | null;
  /** 主题 token（六-5：从基座下发，子应用禁自带第二套主题） */
  themeToken?: Record<string, string>;
}
