import { useCallback, useEffect, useState } from "react";
import type { Order } from "../../../types";
import { cancelOrder, fetchOrders, payOrder, placeOrder } from "../api";
import type { CreateOrderRequest } from "../api";

/** 订单列表（状态机演示页数据源） */
export function useOrders() {
  const [orders, setOrders] = useState<Order[]>([]);
  const [loading, setLoading] = useState(false);

  const reload = useCallback(async () => {
    setLoading(true);
    try {
      setOrders(await fetchOrders());
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { reload(); }, [reload]);
  return { orders, loading, reload, pay: payOrder, cancel: cancelOrder };
}

/** 下单动作（按钮级 loading 自管；幂等键由调用方生成，Toast/错误分型留在页面） */
export function usePlaceOrder() {
  const [ordering, setOrdering] = useState(false);

  const place = useCallback(async (req: CreateOrderRequest, idempotencyKey: string) => {
    setOrdering(true);
    try {
      return await placeOrder(req, idempotencyKey);
    } finally {
      setOrdering(false);
    }
  }, []);

  return { ordering, place };
}
