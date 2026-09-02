import { createClient } from "@yarch/contract";
import type { PageData } from "@yarch/contract";
import type { Product } from "../../types";
import { authStore } from "../../stores/auth";

const api = createClient({
  baseUrl: "/api/v1",
  getHeaders: () => ({ Authorization: `Bearer ${authStore.token}` }),
});

export function fetchProducts(page = 1, pageSize = 20): Promise<PageData<Product>> {
  return api.get<PageData<Product>>(`/products?page=${page}&pageSize=${pageSize}`);
}
