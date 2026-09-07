import { Button, Card, Form, Toast } from "@douyinfe/semi-ui";
import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { authStore } from "../stores/auth";
import { useCaptcha } from "../features/products/hooks/use-captcha";

/** 登录页：验证码 + JWT 演示（真实认证走 yarch-auth） */
export default function Login() {
  const navigate = useNavigate();
  const { image: captchaImg, key: captchaKey, refresh: refreshCaptcha } = useCaptcha();
  const [loading, setLoading] = useState(false);

  async function handleSubmit(values: { username?: string; captcha?: string }) {
    setLoading(true);
    try {
      // 演示：真实场景 POST /api/v1/auth/login → 后端验码 → 签发 JWT
      authStore.token = "demo-jwt-" + Date.now();
      Toast.success(`欢迎 ${values.username}（验证码 key=${captchaKey.slice(0, 8)}…）`);
      navigate("/");
    } finally {
      setLoading(false);
    }
  }

  return (
    <div style={{ display: "flex", justifyContent: "center", alignItems: "center", height: "100vh", background: "#f0f2f5" }}>
      <Card title="yarch admin-demo 登录" style={{ width: 380 }}>
        <Form onSubmit={handleSubmit}>
          <Form.Input field="username" label="用户名" placeholder="admin" rules={[{ required: true, message: "请输入用户名" }]} />
          <Form.Input field="password" label="密码（演示）" mode="password" placeholder="任意" />
          <div style={{ display: "flex", gap: 8, alignItems: "center", marginBottom: 12 }}>
            <div style={{ flex: 1 }}>
              <Form.Input field="captcha" label="验证码" placeholder="输入图中字符" noLabel />
            </div>
            {captchaImg ? (
              <img
                src={captchaImg}
                alt="captcha"
                onClick={refreshCaptcha}
                style={{ cursor: "pointer", borderRadius: 4, height: 32, marginTop: 4 }}
                title="点击刷新"
              />
            ) : (
              <Button size="small" onClick={refreshCaptcha}>获取验证码</Button>
            )}
          </div>
          <Button theme="solid" type="primary" htmlType="submit" block loading={loading}>
            登录
          </Button>
        </Form>
      </Card>
    </div>
  );
}
