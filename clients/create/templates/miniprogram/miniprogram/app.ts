// app.ts —— 小程序入口（miniprogram.md 一-1 原生+TS 唯一入册栈）
import { createWxClient, createWxStorageBackend } from "@yarch/contract/wx";
import { createAppStorage, type CoreClient } from "@yarch/contract/core";

export interface AppData {
  client: CoreClient;
  storage: ReturnType<typeof createAppStorage>;
}

App<{
  client: CoreClient;
  storage: ReturnType<typeof createAppStorage>;
}>({
  globalData: {} as AppData,

  onLaunch() {
    // 契约内核单点装配（miniprogram.md 三-1/三-2/三-4/三-5）：
    // 信封解包 + traceId 会话复用 + 超时单点 10/30/30 + 401 刷新重放 + GET 重试
    const storage = createAppStorage("{{packageName}}", createWxStorageBackend());

    const refreshPromise = { p: null as Promise<boolean> | null };
    const client = createWxClient({
      baseUrl: "{{baseUrl}}",
      // 凭证注入（client-shared 二-1：每请求注入 Authorization）
      getHeaders: () => {
        const token = storage.get<string>("token");
        return token ? { Authorization: `Bearer ${token}` } : {};
      },
      // 认证失效单点：刷新 → 重放一次；终态清态 + 跳登录（client-shared 一-6）
      onUnauthorized: async () => {
        if (refreshPromise.p) return refreshPromise.p;
        refreshPromise.p = (async () => {
          try {
            const res = await wx.request({
              url: "{{baseUrl}}/api/v1/auth/refresh",
              method: "POST",
              header: { "Content-Type": "application/json" },
            });
            // @ts-expect-error wx.request success 返回非 typed
            const data = res.data;
            if (data && typeof data === "object" && "data" in data) {
              // @ts-expect-error 信封形状
              storage.set("token", data.data.accessToken);
              return true;
            }
            return false;
          } catch {
            return false;
          }
        })();
        const result = await refreshPromise.p;
        refreshPromise.p = null;
        return result;
      },
      onSessionExpired: () => {
        storage.remove("token");
        wx.reLaunch({ url: "/pages/login/login" });
      },
    });

    this.globalData = { client, storage };
  },
});
