import { Button, Space, Table, Tag, Toast } from "@douyinfe/semi-ui";
import { useCallback, useEffect, useState } from "react";
import { useProducts } from "../features/products/hooks/use-products";
import { placeOrder, payOrder, cancelOrder } from "../features/products/api";
import { ApiError } from "@yarch/contract";
import type { Order } from "../types";

/** 商品列表 + 下单/支付/取消（契约全链路演示：分页/幂等/状态机/错误码） */
export default function Products() {
  const { data, loading, error, page, setPage, reload } = useProducts();
  const [ordering, setOrdering] = useState(false);

  const columns = [
    { title: "ID", dataIndex: "id", key: "id", width: 60 },
    { title: "名称", dataIndex: "name", key: "name" },
    { title: "价格(分)", dataIndex: "priceCents", key: "priceCents", width: 100 },
    { title: "库存", dataIndex: "stock", key: "stock", width: 80 },
    {
      title: "操作",
      key: "action",
      render: (_: unknown, record: { id: number; name: string; stock: number }) => (
        <Space>
          <Button
            size="small"
            theme="solid"
            loading={ordering}
            disabled={record.stock <= 0}
            onClick={async () => {
              setOrdering(true);
              try {
                // 幂等键 = UUID：同一次点击重试不会二次扣库存
                const idemKey = `demo-${Date.now()}-${record.id}`;
                const order = await placeOrder(
                  { productId: record.id, buyerEmail: "demo@yarch.dev", quantity: 1 },
                  idemKey
                );
                Toast.success(`下单成功 #${order.id}（库存 ${record.stock - 1}）`);
                reload();
              } catch (e) {
                const err = e as ApiError;
                if (err.code === 3002) {
                  Toast.warning(`库存不足 (code=${err.code}, traceId=${err.traceId?.slice(0, 8)}…)`);
                } else {
                  Toast.error(`下单失败: ${err.message} (code=${err.code})`);
                }
              } finally {
                setOrdering(false);
              }
            }}
          >
            下单
          </Button>
        </Space>
      ),
    },
  ];

  return (
    <div>
      <h2>商品列表（契约分页 PageData）</h2>
      {error && (
        <p style={{ color: "red" }}>
          加载失败: {error.message} {error instanceof ApiError && `(code=${error.code}, traceId=${error.traceId})`}
        </p>
      )}
      <Table
        columns={columns}
        dataSource={data?.list ?? []}
        rowKey="id"
        loading={loading}
        pagination={{
          currentPage: page,
          total: data?.total ?? 0,
          pageSize: data?.pageSize ?? 20,
          onPageChange: setPage,
        }}
      />
    </div>
  );
}

/** 订单管理（状态机演示页） */
export function Orders() {
  const [orders, setOrders] = useState<Order[]>([]);
  const [loading, setLoading] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const res = await fetch("/api/v1/orders?pageSize=50");
      const envelope = await res.json();
      if (envelope.code === 0 && envelope.data?.list) {
        setOrders(envelope.data.list);
      }
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { load(); }, [load]);

  const statusTag = (status: string) => {
    const colors = { pending: "orange", paid: "green", cancelled: "red" } as const;
    return <Tag color={colors[status as keyof typeof colors] ?? "grey"}>{status}</Tag>;
  };

  const columns = [
    { title: "订单号", dataIndex: "id", key: "id", width: 80 },
    { title: "商品ID", dataIndex: "productId", key: "productId", width: 80 },
    { title: "买家", dataIndex: "buyerEmail", key: "buyerEmail" },
    { title: "数量", dataIndex: "quantity", key: "quantity", width: 60 },
    { title: "状态", dataIndex: "status", key: "status", render: statusTag },
    {
      title: "操作",
      key: "action",
      render: (_: unknown, record: Order) => (
        <Space>
          {record.status === "pending" && (
            <>
              <Button size="small" theme="solid" type="primary" onClick={() => doTransition(record.id, "pay")}>支付</Button>
              <Button size="small" type="danger" onClick={() => doTransition(record.id, "cancel")}>取消</Button>
            </>
          )}
        </Space>
      ),
    },
  ];

  async function doTransition(id: number, action: "pay" | "cancel") {
    try {
      if (action === "pay") await payOrder(id);
      else await cancelOrder(id);
      Toast.success(`${action} 成功`);
      load();
    } catch (e) {
      const err = e as ApiError;
      if (err.code === 3004) Toast.warning(`非法状态迁移 (code=3004)`);
      else Toast.error(`${action} 失败: ${err.message}`);
    }
  }

  return (
    <div>
      <h2>订单管理（状态机 pending → paid / cancelled）</h2>
      <Table columns={columns} dataSource={orders} rowKey="id" loading={loading} pagination={false} />
    </div>
  );
}
