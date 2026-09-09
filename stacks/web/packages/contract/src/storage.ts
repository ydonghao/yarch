/**
 * 前缀化 Web storage（micro-frontend.md 二-2/七-3）：key 一律 `{应用名}:` 前缀，
 * 裸 key（token、userInfo 直写）= 跨应用覆写事故。冒号分层与 redis.md key 哲学同源；
 * 值统一 JSON 序列化。
 */
export interface AppStorage {
  get<T>(key: string): T | null;
  set<T>(key: string, value: T): void;
  remove(key: string): void;
}

const APP_NAME_RE = /^[a-z][a-z0-9-]{1,31}$/;
const KEY_RE = /^[a-z][a-z0-9-]*(?::[a-z][a-z0-9-]*)*$/;

export function createAppStorage(appName: string, backend: Storage = globalThis.localStorage): AppStorage {
  if (!APP_NAME_RE.test(appName)) {
    throw new Error(`应用名 ${appName} 违反二-1 格式 ^[a-z][a-z0-9-]{1,31}$`);
  }
  const fullKey = (key: string) => {
    if (!KEY_RE.test(key)) {
      throw new Error(`storage key ${key} 须为 kebab-case 冒号分层（正例 filter-state）`);
    }
    return `${appName}:${key}`;
  };
  return {
    get<T>(key: string): T | null {
      const raw = backend.getItem(fullKey(key));
      return raw === null ? null : (JSON.parse(raw) as T);
    },
    set<T>(key: string, value: T): void {
      backend.setItem(fullKey(key), JSON.stringify(value));
    },
    remove(key: string): void {
      backend.removeItem(fullKey(key));
    },
  };
}
