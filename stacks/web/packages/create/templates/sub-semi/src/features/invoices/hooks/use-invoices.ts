import { useEffect, useState } from "react";
import { fetchInvoices } from "../api";
import type { Invoice } from "../../../types";

export function useInvoices() {
  const [invoices, setInvoices] = useState<Invoice[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let alive = true; // 异步回写防卸载后 setState（五-2 清理纪律的邻接面）
    fetchInvoices()
      .then((page) => { if (alive) { setInvoices(page.list); setLoading(false); } })
      .catch(() => { if (alive) setLoading(false); }); // 十-2：错误只上抛不跳转，列表态由页面兜底
    return () => { alive = false; };
  }, []);

  return { invoices, loading };
}
