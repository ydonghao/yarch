import { Layout, Menu } from "antd";
import { Outlet, useLocation, useNavigate } from "react-router-dom";

export default function BasicLayout() {
  const navigate = useNavigate();
  const location = useLocation();

  return (
    <Layout style={{ minHeight: "100vh" }}>
      <Layout.Sider>
        <Menu
          selectedKeys={[location.pathname]}
          items={[
            { key: "/", label: "Dashboard" },
            { key: "/products", label: "商品" },
            { key: "/orders", label: "订单" },
          ]}
          onClick={({ key }) => navigate(key)}
        />
      </Layout.Sider>
      <Layout>
        <Layout.Header style={{ background: "#fff", padding: "0 24px" }}>
          <span style={{ fontSize: 14, color: "#666" }}>yarch admin-demo（antd 档）</span>
        </Layout.Header>
        <Layout.Content style={{ padding: 24, background: "#f0f2f5" }}>
          <Outlet />
        </Layout.Content>
      </Layout>
    </Layout>
  );
}
