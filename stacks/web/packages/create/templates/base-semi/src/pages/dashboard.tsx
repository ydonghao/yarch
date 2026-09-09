/**
 * 基座自有首页（三-1：去掉全部子应用后基座仍能运行——登录/菜单框架/404/错误页齐全）。
 * 事件上行订阅挂常驻布局壳（layouts/basic-layout.tsx）——页面切换不丢订阅。
 */
import { Typography } from "@douyinfe/semi-ui";
import { microApps } from "../app/micro-apps.config";

export default function Dashboard() {
  return (
    <div>
      <h2>基座首页</h2>
      <p>已登记子应用：{microApps.map((r) => r.name).join("、") || "（无）"}</p>
      <Typography.Text type="tertiary">菜单由 micro-apps.config.ts 声明式装配（三-4）</Typography.Text>
    </div>
  );
}
