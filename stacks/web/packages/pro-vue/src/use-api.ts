/**
 * @yarch/pro-vue 数据 hooks：信封解包/错误码统一走 @yarch/contract（五-1 红线——禁组件内自造 fetch）。
 * 组合式 API 版，语义对齐 @yarch/pro-react use-api（贫血 + 函数式组织）。
 */
import { ref, shallowRef, watch } from "vue";
import type { Ref, ShallowRef } from "vue";

import { ApiError } from "@yarch/contract";

/** 错误归一：client 抛出的已是 ApiError（业务/传输/取消三分类），其余兜底为传输错误 -1 */
function toApiError(e: unknown): ApiError {
  if (e instanceof ApiError) return e;
  return new ApiError(-1, e instanceof Error ? e.message : String(e));
}

export interface QueryState<T> {
  data: ShallowRef<T | null>;
  loading: Ref<boolean>;
  error: ShallowRef<ApiError | null>;
  reload: () => void;
}

/**
 * 查询 hook：deps 里的响应式源（ref/computed）变化即重新执行；
 * 组件卸载自动取消未落地的异步落盘（cancelled 收尾）。传普通值则不追踪（对齐 React deps 语义）。
 */
export function useApiQuery<T>(fn: () => Promise<T>, deps: unknown[] = []): QueryState<T> {
  const data = shallowRef<T | null>(null);
  const loading = ref(false);
  const error = shallowRef<ApiError | null>(null);
  const tick = ref(0);

  const reload = () => {
    tick.value += 1;
  };

  watch(
    [() => tick.value, ...deps],
    (_next, _prev, onCleanup) => {
      let cancelled = false;
      onCleanup(() => {
        cancelled = true;
      });
      loading.value = true;
      error.value = null;
      fn()
        .then((d) => {
          if (!cancelled) data.value = d;
        })
        .catch((e: unknown) => {
          if (!cancelled) error.value = toApiError(e);
        })
        .finally(() => {
          if (!cancelled) loading.value = false;
        });
    },
    { immediate: true },
  );

  return { data, loading, error, reload };
}

export interface MutationState<TArgs extends unknown[], T> {
  run: (...args: TArgs) => Promise<T | null>;
  loading: Ref<boolean>;
  error: ShallowRef<ApiError | null>;
}

/** 变更 hook：错误不抛出而是收敛到 error 态（调用方按业务分支处理） */
export function useApiMutation<TArgs extends unknown[], T>(
  fn: (...args: TArgs) => Promise<T>,
): MutationState<TArgs, T> {
  const loading = ref(false);
  const error = shallowRef<ApiError | null>(null);

  const run = async (...args: TArgs): Promise<T | null> => {
    loading.value = true;
    error.value = null;
    try {
      return await fn(...args);
    } catch (e: unknown) {
      error.value = toApiError(e);
      return null;
    } finally {
      loading.value = false;
    }
  };

  return { run, loading, error };
}
