// pages/orders/orders.ts —— 列表页（走契约全链路：分页解包 / traceId / 401 刷新重放）
import type { ApiError, PageData } from "@yarch/contract/core";

interface OrderItem {
  id: number;
  title: string;
  status: string;
  amount: number;
}

Page({
  data: {
    orders: [] as OrderItem[],
    loading: false,
    page: 1,
    total: 0,
  },

  onShow() {
    this.refresh();
  },

  async refresh() {
    this.setData({ loading: true });
    const app = getApp<{ globalData: { client: { get: <T>(p: string) => Promise<T> } } }>();
    try {
      const page = await app.globalData.client.get<PageData<OrderItem>>(
        `/api/v1/orders?page=${this.data.page}`,
      );
      // 分页解包已由契约内核完成（miniprogram.md 三-1：禁手解 code/message/data）
      this.setData({
        orders: page.list,
        total: page.total,
      });
    } catch (e) {
      const err = e as ApiError;
      if (err.code === -1) {
        wx.showToast({ title: "网络异常，请下拉刷新", icon: "none" });
      } else {
        wx.showToast({ title: err.message || "加载失败", icon: "none" });
      }
    } finally {
      this.setData({ loading: false });
    }
  },

  onPullDownRefresh() {
    this.setData({ page: 1 });
    this.refresh().then(() => wx.stopPullDownRefresh());
  },
});
