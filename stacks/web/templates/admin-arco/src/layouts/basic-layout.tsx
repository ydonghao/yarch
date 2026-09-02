import { Layout, Menu, Table, Button } from "@arco-design/web-react";
import { Outlet, useLocation, useNavigate } from "react-router-dom";

export default function BasicLayout() {
  const navigate = useNavigate();
  const location = useLocation();
  const { Sider, Header, Content } = Layout;

  return (
    <Layout style={{ minHeight: "100vh" }}>
      <Sider>
        <Menu selectedKeys={[location.pathname]}
          items={[
            { key: "/", label: "首页" },
            { key: "/products", label: "商品" },
          ]}
          onClick={({ key }: { key: string }) => navigate(key)} />
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
