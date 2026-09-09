import { Button, Typography } from "@douyinfe/semi-ui";
import { useNavigate } from "react-router-dom";

/** 四-5：未匹配路由兜底 404，禁白屏 */
export default function NotFound() {
  const navigate = useNavigate();
  return (
    <div style={{ display: "flex", flexDirection: "column", alignItems: "center", gap: 12, padding: 48 }}>
      <Typography.Title heading={3}>404 · 页面不存在</Typography.Title>
      <Button theme="solid" onClick={() => navigate("/")}>回首页</Button>
    </div>
  );
}
