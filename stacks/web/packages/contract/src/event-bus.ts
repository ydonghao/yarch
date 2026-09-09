/**
 * 微前端事件通道（micro-frontend.md 九）：三条通信通道之一——事件上行/广播。
 * 事件名格式 `{应用名}:{动词-名词}`（九-3，正例 ysaas-billing:export-done），裸事件名禁；
 * payload 为可序列化 JSON；处理方异常不得中断发布方（九-4）——emit 内逐 handler try/catch 兜底。
 * 基座与子应用经共享 contract 实例（八-1 单例）取到同一条总线，无需额外传输层。
 */
export type AppEventPayload = unknown;
export type AppEventHandler = (payload: AppEventPayload) => void;

const APP_NAME_RE = /^[a-z][a-z0-9-]{1,31}$/;
const EVENT_NAME_RE = /^[a-z][a-z0-9-]{1,31}:[a-z]+(-[a-z]+)+$/;
const EVENT_ACTION_RE = /^[a-z]+(-[a-z]+)+$/;

export function assertAppEventName(name: string): void {
  if (!EVENT_NAME_RE.test(name)) {
    throw new Error(`事件名 ${name} 违反九-3 格式 {应用名}:{动词-名词}（正例 ysaas-billing:export-done）`);
  }
}

export interface AppEventBus {
  emit(name: string, payload: AppEventPayload): void;
  /** 返回取消订阅函数（七-4：与注册成对使用） */
  on(name: string, handler: AppEventHandler): () => void;
}

export function createAppEventBus(
  onHandlerError: (name: string, cause: unknown) => void = console.error,
): AppEventBus {
  const handlers = new Map<string, Set<AppEventHandler>>();
  return {
    emit(name, payload) {
      assertAppEventName(name);
      for (const handler of handlers.get(name) ?? []) {
        try {
          handler(payload);
        } catch (cause) {
          onHandlerError(name, cause); // 九-4：处理方异常不得中断发布方
        }
      }
    },
    on(name, handler) {
      assertAppEventName(name);
      const set = handlers.get(name) ?? new Set();
      set.add(handler);
      handlers.set(name, set);
      return () => set.delete(handler);
    },
  };
}

let bus: AppEventBus | null = null;

/** 体系级单例总线：微前端下各方共享同一 contract 实例 → 同一条总线 */
export function getAppEventBus(): AppEventBus {
  bus ??= createAppEventBus();
  return bus;
}

/** 子应用侧前缀封装（九-3 类型+运行时双强制）：action 形如 "export-done"，自动拼 `{应用名}:` 前缀 */
export function defineAppEvents(appName: string) {
  if (!APP_NAME_RE.test(appName)) {
    throw new Error(`应用名 ${appName} 违反二-1 格式 ^[a-z][a-z0-9-]{1,31}$`);
  }
  const assertAction = (action: string) => {
    if (!EVENT_ACTION_RE.test(action)) {
      throw new Error(`事件动作 ${action} 须为 动词-名词（正例 export-done）`);
    }
  };
  return {
    emit(action: string, payload: AppEventPayload) {
      assertAction(action);
      getAppEventBus().emit(`${appName}:${action}`, payload);
    },
    on(action: string, handler: AppEventHandler) {
      assertAction(action);
      return getAppEventBus().on(`${appName}:${action}`, handler);
    },
  };
}
