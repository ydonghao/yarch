// pages/login/login.ts —— 登录页（走契约全链路：解包 / traceId / 401 刷新重放）
import type { ApiError } from "@yarch/contract/core";
import type { PageData } from "@yarch/contract/core";

interface LoginResult {
  accessToken: string;
}

Page({
  data: {
    username: "",
    password: "",
    loading: false,
  },

  onUsernameInput(e: WechatMiniprogram.Input) {
    this.setData({ username: e.detail.value });
  },

  onPasswordInput(e: WechatMiniprogram.Input) {
    this.setData({ password: e.detail.value });
  },

  async onLogin() {
    if (!this.data.username || !this.data.password) {
      wx.showToast({ title: "请输入账号和密码", icon: "none" });
      return;
    }
    this.setData({ loading: true });
    const app = getApp<{ globalData: { client: { post: <T>(p: string, b: unknown) => Promise<T> } } }>();
    try {
      const result = await app.globalData.client.post<LoginResult>(
        "/api/v1/auth/login",
        { username: this.data.username, password: this.data.password },
      );
      // 存 token 到前缀化 storage（miniprogram.md 三-6：key 以已登记服务名前缀隔离）
      const storage = getApp<{ globalData: { storage: { set: (k: string, v: unknown) => void } } }>();
      storage.globalData.storage.set("token", result.accessToken);
      wx.reLaunch({ url: "/pages/orders/orders" });
    } catch (e) {
      const err = e as ApiError;
      if (err.code === -1) {
        // 传输错误：统一网络类文案（client-shared 一-4 传输分类）
        wx.showToast({ title: "网络异常，请稍后重试", icon: "none" });
      } else {
        // 业务错误：服务端 message 可直接展示（client-shared 一-4 业务分类）
        wx.showToast({ title: err.message || "登录失败", icon: "none" });
      }
    } finally {
      this.setData({ loading: false });
    }
  },
});
