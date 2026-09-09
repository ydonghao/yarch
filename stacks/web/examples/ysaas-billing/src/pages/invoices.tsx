import { useState } from "react";
import { Button, Modal, Table, Typography } from "@douyinfe/semi-ui";
import { useInvoices } from "../features/invoices/hooks/use-invoices";
import { appEvents } from "../events";
import { getPopupContainer } from "../ui/popup";

/**
 * 域内示例页：列表 + 导出。
 * 导出完成后事件上行（九-1/九-3）：appEvents.emit("export-done", …) → 基座 Toast；
 * Modal 指定挂载容器为本应用容器（六-3）。
 */
export default function Invoices() {
  const { invoices, loading } = useInvoices();
  const [open, setOpen] = useState(false);

  const columns = [
    { title: "账单号", dataIndex: "id" },
    { title: "金额", dataIndex: "amount" },
    { title: "状态", dataIndex: "status" },
  ];

  return (
    <div>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 12 }}>
        <h3>账单列表</h3>
        <Button theme="solid" onClick={() => setOpen(true)}>导出</Button>
      </div>
      <Table
        columns={columns}
        dataSource={invoices}
        loading={loading}
        pagination={{ pageSize: 10 }}
        empty={<Typography.Text type="tertiary">暂无数据（后端未起或未登录——独立模式为 mock token）</Typography.Text>}
      />
      <Modal
        title="确认导出"
        visible={open}
        getPopupContainer={getPopupContainer}
        onOk={() => {
          setOpen(false);
          appEvents.emit("export-done", { count: invoices.length }); // 九-3：{应用名}:export-done
        }}
        onCancel={() => setOpen(false)}
      >
        将导出 {invoices.length} 条账单，完成后基座将收到事件上行通知。
      </Modal>
    </div>
  );
}
