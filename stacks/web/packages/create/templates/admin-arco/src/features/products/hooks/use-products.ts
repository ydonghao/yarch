import { useCallback, useEffect, useState } from "react";
import type { PageData } from "@yarch/contract";
import type { Product } from "../../../types";
import { fetchProducts } from "../api";

export function useProducts() {
  const [data, setData] = useState<PageData<Product> | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<Error | null>(null);
  const [page, setPage] = useState(1);

  const load = useCallback(async (p: number) => {
    setLoading(true); setError(null);
    try { setData(await fetchProducts(p)); } catch (e) { setError(e as Error); } finally { setLoading(false); }
  }, []);

  useEffect(() => { load(page); }, [page, load]);
  return { data, loading, error, page, setPage, reload: () => load(page) };
}
