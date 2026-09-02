import { Button, Card, Form, Input, message } from "antd";
import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { authStore } from "../stores/auth";
import { fetchCaptcha } from "../features/products/api";

/** 登录页：验证码 + JWT 演示（真实认证走 yarch-auth） */
export default function Login() {
  const navigate = useNavigate();
  const [captchaImg, setCaptchaImg] = useState<string>("");
  const [captchaKey, setCaptchaKey] = useState<string>("");
  const [loading, setLoading] = useState(false);

  async function refreshCaptcha() {
    try {
      const captcha = await fetchCaptcha();
      setCaptchaKey(captcha.key);
      setCaptchaImg(`data:image/png;base64,${captcha.imageBase64}`);
    } catch {
      // 验证码不可用时不阻断登录演示
    }
  }

  // 页面加载时获取验证码
  useState(() => { refreshCaptcha(); });

  async function handleSubmit(values: { username: string; captcha: string }) {
    setLoading(true);
    try {
      // 演示：真实场景 POST /api/v1/auth/login → 后端验码 → 签发 JWT
      authStore.token = "demo-jwt-" + Date.now();
      message.success(`欢迎 ${values.username}（验证码 key=${captchaKey.slice(0, 8)}…）`);
      navigate("/");
    } finally {
      setLoading(false);
    }
  }

  return (
    <div style={{ display: "flex", justifyContent: "center", alignItems: "center", height: "100vh", background: "#f0f2f5" }}>
      <Card title="yarch admin-demo 登录" style={{ width: 380 }}>
        <Form onFinish={handleSubmit} layout="vertical">
          <Form.Item name="username" label="用户名" rules={[{ required: true, message: "请输入用户名" }]}>
            <Input placeholder="admin" />
          </Form.Item>
          <Form.Item label="密码（演示）">
            <Input.Password placeholder="任意" />
          </Form.Item>
          <Form.Item label="验证码">
            <div style={{ display: "flex", gap: 8, alignItems: "center" }}>
              <Input placeholder="输入图中字符" style={{ flex: 1 }} />
              {captchaImg ? (
                <img
                  src={captchaImg}
                  alt="captcha"
                  onClick={refreshCaptcha}
                  style={{ cursor: "pointer", borderRadius: 4, height: 36 }}
                  title="点击刷新"
                />
              ) : (
                <Button size="small" onClick={refreshCaptcha}>获取验证码</Button>
              )}
            </div>
          </Form.Item>
          <Button type="primary" htmlType="submit" block loading={loading}>
            登录
          </Button>
        </Form>
      </Card>
    </div>
  );
}
