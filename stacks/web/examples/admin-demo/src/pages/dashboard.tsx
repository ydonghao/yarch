import { Card, Col, Row, Statistic } from "antd";
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

  return (
    <div>
      <h2>Dashboard</h2>
      <Row gutter={16}>
        <Col span={6}>
          <Card>
            <Statistic title="后端连通" value={backendUp === null ? "检测中…" : backendUp ? "✅ 正常" : "❌ 不可达"}
              valueStyle={{ fontSize: 20 }} />
            <p style={{ fontSize: 12, color: "#999" }}>java examples-ddd :8081</p>
          </Card>
        </Col>
        <Col span={6}>
          <Card>
            <Statistic title="商品总数" value={productTotal ?? "—"} valueStyle={{ fontSize: 20 }} />
            <p style={{ fontSize: 12, color: "#999" }}>契约分页 PageData.total</p>
          </Card>
        </Col>
        <Col span={6}>
          <Card>
            <Statistic title="Token 状态" value={authStore.token ? "已登录" : "未登录"} valueStyle={{ fontSize: 20 }} />
            <p style={{ fontSize: 12, color: "#999" }}>authStore（演示 JWT）</p>
          </Card>
        </Col>
        <Col span={6}>
          <Card>
            <Statistic title="契约 SDK" value="@yarch/contract" valueStyle={{ fontSize: 16 }} />
            <p style={{ fontSize: 12, color: "#999" }}>信封解包 · 错误码 · traceId</p>
          </Card>
        </Col>
      </Row>
    </div>
  );
}
