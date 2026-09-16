/**
 * 前缀化 storage（micro-frontend.md 二-2/七-3；miniprogram.md 三-6 小程序同款纪律）：
 * key 一律 `{应用名}:` 前缀，裸 key（token、userInfo 直写）= 跨应用覆写事故。
 * 冒号分层与 redis.md key 哲学同源；值统一 JSON 序列化。
 * backend 环境无关（StorageLike 最小形状）：web 传默认 localStorage，小程序/小游戏传 createWxStorageBackend()。
 */
export interface StorageLike {
  getItem(key: string): string | null;
  setItem(key: string, value: string): void;
  removeItem(key: string): void;
}

const APP_NAME_RE = /^[a-z][a-z0-9-]{1,31}$/;
const KEY_RE = /^[a-z][a-z0-9-]*(?::[a-z][a-z0-9-]*)*$/;

export function createAppStorage(appName: string, backend?: StorageLike): AppStorage {
  if (!APP_NAME_RE.test(appName)) {
    throw new Error(`应用名 ${appName} 违反二-1 格式 ^[a-z][a-z0-9-]{1,31}$`);
  }
  const store =
    backend ?? (globalThis as unknown as { localStorage?: StorageLike }).localStorage;
  if (!store) {
    throw new Error("无 storage backend：小程序/小游戏环境须传 createWxStorageBackend()");
  }
  const fullKey = (key: string) => {
    if (!KEY_RE.test(key)) {
      throw new Error(`storage key ${key} 须为 kebab-case 冒号分层（正例 filter-state）`);
    }
    return `${appName}:${key}`;
  };
  return {
    get<T>(key: string): T | null {
      const raw = store.getItem(fullKey(key));
      return raw === null ? null : (JSON.parse(raw) as T);
    },
    set<T>(key: string, value: T): void {
      store.setItem(fullKey(key), JSON.stringify(value));
    },
    remove(key: string): void {
      store.removeItem(fullKey(key));
    },
  };
}

export interface AppStorage {
  get<T>(key: string): T | null;
  set<T>(key: string, value: T): void;
  remove(key: string): void;
}
