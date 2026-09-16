/**
 * 契约客户端单点装配（game.md 三：照搬 miniprogram.md 三，不另设）。
 * @yarch/contract/cocos 自动检测运行时：微信小游戏→wx.request / H5→fetch。
 * 游戏代码只引本出口，构建目标切换（小游戏↔H5）无需改业务代码。
 *
 * 用法（Cocos 脚本中）：
 *   import { api } from "./ApiManager";
 *   const data = await api.get("/api/v1/items");
 */
import { createCocosClient, createCocosStorage } from "@yarch/contract/cocos";
import type { CoreClient } from "@yarch/contract/core";

let client: CoreClient | null = null;

function getClient(): CoreClient {
  if (!client) {
    const storage = createCocosStorage("{{packageName}}");
    client = createCocosClient({
      baseUrl: "{{baseUrl}}",
      getHeaders: () => {
        const token = storage.get<string>("token");
        return token ? { Authorization: `Bearer ${token}` } : {};
      },
      // 认证失效单点：刷新 → 重放一次（game.md 三-2，同 miniprogram.md 三-5）
      onUnauthorized: async () => {
        // 接入项目的刷新凭证逻辑（POST /auth/refresh），此处仅示意
        return false;
      },
      onSessionExpired: () => {
        storage.remove("token");
        // 接入项目的路由登录逻辑
        console.warn("[yarch] 会话过期，须重新登录");
      },
    });
  }
  return client;
}

export const api = {
  get: <T>(path: string) => getClient().get<T>(path),
  post: <T>(path: string, body: unknown) => getClient().post<T>(path, body),
  put: <T>(path: string, body: unknown) => getClient().put<T>(path, body),
  delete: <T>(path: string) => getClient().delete<T>(path),
};
