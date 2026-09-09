import { Button } from "@douyinfe/semi-ui";
import { Navigate, useNavigate } from "react-router-dom";
import { authStore } from "../stores/auth";

export default function Login() {
  const navigate = useNavigate();
  // 已登录访问登录页一律回首页（十-1 登录态唯一归基座——覆盖会话恢复后直达 /login 的场景）
  if (authStore.token) return <Navigate to="/" replace />;
  return (
    <div style={{ display: "flex", justifyContent: "center", alignItems: "center", height: "100vh" }}>
      <form onSubmit={(e: React.FormEvent) => { e.preventDefault(); authStore.token = "demo"; navigate("/"); }}>
        <h2>{{appName}} · 登录</h2>
        <p>演示——真实认证走 yarch-auth JWT；登录态唯一归基座（十-1）</p>
        <Button theme="solid" type="primary" htmlType="submit" block>登录</Button>
      </form>
    </div>
  );
}
