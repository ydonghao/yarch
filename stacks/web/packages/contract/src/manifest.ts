/**
 * 基座子应用入口登记表（micro-frontend.md 三-4/十二-2）：声明式 manifest，进 git 纳管，
 * 入口变更只改此表。跨环境（test/staging/prod）入口 URL 由环境配置注入（十二-7），禁硬编码。
 */
export interface SubAppMenuMeta {
  /** 菜单键：`{应用名}:{路径}`，与前缀哲学同源 */
  key: string;
  /** 子应用内部路由路径（kebab-case，四-4）；基座按 `路由前缀 + path` 装配 */
  path: string;
  title: string;
  icon?: string;
}

export interface SubAppRegistration {
  /** 应用名（二-1：首段 = registry 已登记服务名，两段及以上） */
  name: string;
  /** 入口 URL（dev=子应用 dev server，prod=静态目录/CDN，内容寻址产物） */
  entry: string;
  version?: string;
  /** 路由前缀（四-1：恒等于 `/{name}`，字段化以对表十二-2；基座装配时校验一致） */
  routePrefix: string;
  /** 菜单元数据（三-4：基座读取装配，禁硬编码子应用内部路由） */
  menu: SubAppMenuMeta[];
}

export type MicroAppsConfig = SubAppRegistration[];

/** 路由前缀唯一写法（四-1）：子应用路由前缀 = 应用名 */
export function subAppRoutePrefix(appName: string): string {
  return `/${appName}`;
}
