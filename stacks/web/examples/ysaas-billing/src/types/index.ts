/** 域内类型（billing 域示例；路径段 kebab-case，四-4） */
export interface Invoice {
  id: string;
  amount: number;
  status: "open" | "paid";
}
