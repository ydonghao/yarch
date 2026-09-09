import { createClient, type PageData } from "@yarch/contract";
import { supply } from "@supply";
import type { Invoice } from "../../types";

/**
 * 域内 API（十-1/十-3）：token 经供给层取（独立=mock，集成=基座下发函数），
 * 401 时只抛 ApiError——跳登录唯一归基座（十-2），本应用不做任何跳转。
 */
const api = createClient({
  baseUrl: "/api/v1",
  getHeaders: (): Record<string, string> => {
    const token = supply.getAccessToken();
    return token ? { Authorization: `Bearer ${token}` } : {};
  },
});

export function fetchInvoices(page = 1, pageSize = 20): Promise<PageData<Invoice>> {
  return api.get<PageData<Invoice>>(`/invoices?page=${page}&pageSize=${pageSize}`);
}
