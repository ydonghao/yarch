import { Layout, Nav } from "@douyinfe/semi-ui";
import { Outlet, useLocation, useNavigate } from "react-router-dom";

export default function BasicLayout() {
  const navigate = useNavigate();
  const location = useLocation();
  const { Sider, Header, Content } = Layout;

  return (
    <Layout style={{ minHeight: "100vh" }}>
      <Sider>
        <Nav
          selectedKeys={[location.pathname]}
          items={[
            { itemKey: "/", text: "Dashboard" },
            { itemKey: "/products", text: "商品" },
            { itemKey: "/orders", text: "订单" },
          ]}
          onSelect={(data) => navigate(String(data.itemKey))}
        />
      </Sider>
      <Layout>
        <Header style={{ background: "#fff", padding: "0 24px" }}>
          <span style={{ fontSize: 14, color: "#666" }}>yarch admin-demo（semi 档）</span>
        </Header>
        <Content style={{ padding: 24, background: "#f0f2f5" }}>
          <Outlet />
        </Content>
      </Layout>
    </Layout>
  );
}
