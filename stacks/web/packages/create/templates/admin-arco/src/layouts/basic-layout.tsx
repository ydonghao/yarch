import { Layout, Menu } from "@arco-design/web-react";
import { Outlet, useLocation, useNavigate } from "react-router-dom";

const MenuItem = Menu.Item;

export default function BasicLayout() {
  const navigate = useNavigate();
  const location = useLocation();

  return (
    <Layout style={{ minHeight: "100vh" }}>
      <Layout.Sider>
        <Menu selectedKeys={[location.pathname]} onClickMenuItem={(key) => navigate(key)}>
          <MenuItem key="/">首页</MenuItem>
          <MenuItem key="/products">商品</MenuItem>
        </Menu>
      </Layout.Sider>
      <Layout>
        <Layout.Header style={{ background: "#fff", padding: "0 24px" }} />
        <Layout.Content style={{ padding: 24 }}>
          <Outlet />
        </Layout.Content>
      </Layout>
    </Layout>
  );
}
