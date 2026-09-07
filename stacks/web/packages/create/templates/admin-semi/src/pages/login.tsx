import { Layout, Nav, Table, Button } from "@douyinfe/semi-ui";
import { useNavigate } from "react-router-dom";
import { authStore } from "../stores/auth";

export default function Login() {
  const navigate = useNavigate();
  return (
    <div style={{ display: "flex", justifyContent: "center", alignItems: "center", height: "100vh" }}>
      <form onSubmit={(e: React.FormEvent) => { e.preventDefault(); authStore.token = "demo"; navigate("/"); }}>
        <h2>登录</h2>
        <p>演示——真实认证走 yarch-auth JWT</p>
        <Button theme="solid" type="primary" htmlType="submit" block>登录</Button>
      </form>
    </div>
  );
}
