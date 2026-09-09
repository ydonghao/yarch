/**
 * 子应用入口登记表（十二-2 manifest，git 声明式纳管）：应用名 → 入口/版本/路由前缀/菜单元数据。
 * 子应用接入只改此表；跨环境入口 URL 由环境配置注入（十二-7，.env.local 覆盖），禁硬编码。
 */
import type { MicroAppsConfig } from "@yarch/contract";
import { subAppRoutePrefix } from "@yarch/contract";

export const microApps: MicroAppsConfig = [
  {
    name: "ysaas-billing",
    entry: import.meta.env.VITE_SUB_BILLING_URL ?? "http://127.0.0.1:5174/ysaas-billing/",
    version: "0.1.0",
    routePrefix: subAppRoutePrefix("ysaas-billing"),
    menu: [{ key: "ysaas-billing:invoices", path: "/invoices", title: "账单" }],
  },
];
