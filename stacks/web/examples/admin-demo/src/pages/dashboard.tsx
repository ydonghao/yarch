import { Card, Col, Row } from "@douyinfe/semi-ui";
import { useEffect, useState } from "react";
import type { PageData } from "@yarch/contract";
import type { Product } from "../types";
import { authStore } from "../stores/auth";

/** Dashboard：契约 SDK 状态一览 + 后端连通探测 */
export default function Dashboard() {
  const [productTotal, setProductTotal] = useState<number | null>(null);
  const [backendUp, setBackendUp] = useState<boolean | null>(null);

  useEffect(() => {
    fetch("/api/v1/products?pageSize=1")
      .then((res) => res.json())
      .then((envelope) => {
        if (envelope.code === 0) {
          const page = envelope.data as PageData<Product>;
          setProductTotal(page.total);
          setBackendUp(true);
        }
      })
      .catch(() => setBackendUp(false));
  }, []);

  const cell = (title: string, value: string, note: string, fontSize = 20) => (
    <Card>
      <p style={{ fontSize: 12, color: "#999", margin: "0 0 4px" }}>{title}</p>
      <p style={{ fontSize, margin: 0, fontWeight: 600 }}>{value}</p>
      <p style={{ fontSize: 12, color: "#999", margin: "4px 0 0" }}>{note}</p>
    </Card>
  );

  return (
    <div>
      <h2>Dashboard</h2>
      <Row gutter={[16, 16]}>
        <Col span={6}>{cell("后端连通", backendUp === null ? "检测中…" : backendUp ? "✅ 正常" : "❌ 不可达", "java examples-ddd :8081")}</Col>
        <Col span={6}>{cell("商品总数", productTotal === null ? "—" : String(productTotal), "契约分页 PageData.total")}</Col>
        <Col span={6}>{cell("Token 状态", authStore.token ? "已登录" : "未登录", "authStore（演示 JWT）")}</Col>
        <Col span={6}>{cell("契约 SDK", "@yarch/contract", "信封解包 · 错误码 · traceId", 16)}</Col>
      </Row>
    </div>
  );
}
