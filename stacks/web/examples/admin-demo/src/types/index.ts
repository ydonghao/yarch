export interface Product {
  id: number;
  name: string;
  priceCents: number;
  stock: number;
  createdAt: string;
}

export interface Order {
  id: number;
  productId: number;
  buyerEmail: string;
  quantity: number;
  status: "pending" | "paid" | "cancelled";
  createdAt: string;
}
