/**
 * 登录态持有与刷新（十-1：token 只由基座持有；二-2/七-3：storage key 带 {应用名}: 前缀）。
 * 子应用经 __YARCH_SUPPLY__.getToken 取用（五-5 props 下行），禁自行持久化。
 */
import { createAppStorage } from "@yarch/contract";

const storage = createAppStorage("{{appName}}");

export const authStore = {
  get token() { return storage.get<string>("token") ?? ""; },
  set token(v: string) { storage.set("token", v); },
  clear() { storage.remove("token"); },
};
