import { Layout, Menu, Table, Button } from "@arco-design/web-react";
import { useProducts } from "../features/products/hooks/use-products";

export default function Products() {
  const { data, loading, page, setPage } = useProducts();
  const columns = [
    { title: "ID", dataIndex: "id", key: "id" },
    { title: "名称", dataIndex: "name", key: "name" },
    { title: "价格(分)", dataIndex: "priceCents", key: "priceCents" },
    { title: "库存", dataIndex: "stock", key: "stock" },
  ];

  return (
    <div>
      <h2>商品列表</h2>
      <Table columns={columns} data={data?.list ?? []} rowKey="id" loading={loading} pagination={{ current: page, total: data?.total ?? 0, onChange: setPage }} />
    </div>
  );
}
