import { Typography } from "@douyinfe/semi-ui";
import { Outlet } from "react-router-dom";

/** 域内布局（一-1：子应用只做域内页面；外壳/菜单/登录态全部归基座，三-2） */
export default function SubLayout() {
  return (
    <div style={{ minHeight: "100vh" }}>
      <Typography.Title heading={5}>{{appName}}</Typography.Title>
      <Outlet />
    </div>
  );
}
