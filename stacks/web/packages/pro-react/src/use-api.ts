/**
 * @yarch/pro-react 数据 hooks：信封解包/错误码统一走 @yarch/contract（五-1 红线——禁组件内自造 fetch）。
 * 形态对齐 admin 模板 features hooks 既有范式（贫血 + 函数式组织）。
 */
import { useCallback, useEffect, useState } from "react";

import { ApiError } from "@yarch/contract";

/** 错误归一：client 抛出的已是 ApiError（业务/传输/取消三分类），其余兜底为传输错误 -1 */
function toApiError(e: unknown): ApiError {
  if (e instanceof ApiError) return e;
  return new ApiError(-1, e instanceof Error ? e.message : String(e));
}

export interface QueryState<T> {
  data: T | null;
  loading: boolean;
  error: ApiError | null;
  reload: () => void;
}

/** 查询 hook：fn 变化（引用稳定由调用方保证）即执行；错误三分类收敛为 ApiError */
export function useApiQuery<T>(fn: () => Promise<T>, deps: unknown[] = []): QueryState<T> {
  const [data, setData] = useState<T | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<ApiError | null>(null);
  const [tick, setTick] = useState(0);

  const reload = useCallback(() => setTick((t) => t + 1), []);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError(null);
    fn()
      .then((d) => {
        if (!cancelled) setData(d);
      })
      .catch((e: unknown) => {
        if (!cancelled) setError(toApiError(e));
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [...deps, tick]);

  return { data, loading, error, reload };
}

export interface MutationState<TArgs extends unknown[], T> {
  run: (...args: TArgs) => Promise<T | null>;
  loading: boolean;
  error: ApiError | null;
}

/** 变更 hook：错误不抛出而是收敛到 error 态（调用方按业务分支处理） */
export function useApiMutation<TArgs extends unknown[], T>(
  fn: (...args: TArgs) => Promise<T>,
): MutationState<TArgs, T> {
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<ApiError | null>(null);

  const run = useCallback(
    async (...args: TArgs): Promise<T | null> => {
      setLoading(true);
      setError(null);
      try {
        return await fn(...args);
      } catch (e: unknown) {
        setError(toApiError(e));
        return null;
      } finally {
        setLoading(false);
      }
    },
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [],
  );

  return { run, loading, error };
}
