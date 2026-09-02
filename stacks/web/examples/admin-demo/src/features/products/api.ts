import { ApiError, createClient } from "@yarch/contract";
import type { PageData, RestResponse } from "@yarch/contract";
import { authStore } from "../../stores/auth";
import type { Product, Order } from "../../types";

const api = createClient({
  baseUrl: "/api/v1",
  getHeaders: () => ({ Authorization: `Bearer ${authStore.token}` }),
});

// ---- 商品 ----
export function fetchProducts(page = 1, pageSize = 20): Promise<PageData<Product>> {
  return api.get<PageData<Product>>(`/products?page=${page}&pageSize=${pageSize}`);
}

export function fetchProduct(id: number): Promise<Product> {
  return api.get<Product>(`/products/${id}`);
}

// ---- 订单 ----
export interface CreateOrderRequest {
  productId: number;
  buyerEmail: string;
  quantity: number;
}

/** 幂等下单：同键同参回放原响应（库存只扣一次）——契约幂等总则-1 */
export function placeOrder(body: CreateOrderRequest, idempotencyKey: string): Promise<Order> {
  const headers: Record<string, string> = {
    "Content-Type": "application/json",
    Authorization: `Bearer ${authStore.token}`,
    "Idempotency-Key": idempotencyKey,
  };
  return fetch("/api/v1/orders", {
    method: "POST",
    headers,
    body: JSON.stringify(body),
  }).then(async (res) => {
    const envelope: RestResponse<Order> = await res.json();
    if (envelope.code !== 0) throw new ApiError(envelope.code, envelope.message, envelope.traceId);
    return envelope.data!;
  });
}

export function payOrder(orderId: number): Promise<Order> {
  return api.post<Order>(`/orders/${orderId}/pay`, {});
}

export function cancelOrder(orderId: number): Promise<Order> {
  return api.post<Order>(`/orders/${orderId}/cancel`, {});
}

// ---- 验证码 ----
export interface CaptchaData {
  key: string;
  imageBase64: string;
}

export async function fetchCaptcha(): Promise<CaptchaData> {
  const res = await fetch("/api/v1/captcha");
  const envelope: RestResponse<CaptchaData> = await res.json();
  if (envelope.code !== 0) throw new ApiError(envelope.code, envelope.message, envelope.traceId);
  return envelope.data!;
}
