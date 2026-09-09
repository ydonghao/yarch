/**
 * 基座布局与外壳（三-2）：菜单/导航树 = 基座自有项 + manifest 声明式装配的子应用项（三-4）。
 * 主题与 UI 档在此定义并下发（三-2/六-1/六-5）。
 */
import { useEffect } from "react";
import { Layout, Nav, Toast } from "@douyinfe/semi-ui";
import { Outlet, useLocation, useNavigate } from "react-router-dom";
import { getAppEventBus } from "@yarch/contract";
import { microApps } from "../app/micro-apps.config";

export default function BasicLayout() {
  const navigate = useNavigate();
  const location = useLocation();
  const { Sider, Header, Content } = Layout;

  // 事件上行订阅挂常驻布局壳（九-1/九-3）：路由切换不卸载，子应用事件随时可达（七-4 订阅与移除成对）
  useEffect(() => {
    const offList = microApps.map((reg) =>
      getAppEventBus().on(`${reg.name}:export-done`, (payload) => {
        Toast.success(`${reg.name} 事件上行：export-done ${JSON.stringify(payload)}`);
      }),
    );
    return () => offList.forEach((off) => off());
  }, []);

  const items = [
    { itemKey: "/", text: "首页" },
    ...microApps.flatMap((reg) =>
      reg.menu.map((m) => ({ itemKey: `${reg.routePrefix}${m.path}`, text: m.title })),
    ),
  ];

  return (
    <Layout style={{ minHeight: "100vh" }}>
      <Sider>
        <Nav
          selectedKeys={[location.pathname]}
          header={{ text: "{{appName}}" }}
          items={items}
          onSelect={(data) => navigate(String(data.itemKey))}
        />
      </Sider>
      <Layout>
        <Header style={{ background: "#fff", padding: "0 24px" }} />
        <Content style={{ padding: 24 }}>
          <Outlet />
        </Content>
      </Layout>
    </Layout>
  );
}
