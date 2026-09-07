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
            { itemKey: "/", text: "首页" },
            { itemKey: "/products", text: "商品" },
          ]}
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
