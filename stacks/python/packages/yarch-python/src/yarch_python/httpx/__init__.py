# stacks/python/packages/yarch-python/src/yarch_python/httpx/__init__.py
"""httpx：下游客户端——超时≤30s + traceparent 注入 + 信封解包 + 1008/1009 转译。"""
import secrets
from typing import Any

import httpx

from yarch_python import logx
from yarch_python.xerror import BizError

MAX_TIMEOUT_S = 30.0


class YarchHttpClient:
    def __init__(self, base_url: str, *, timeout: float = 10.0,
                 client: httpx.Client | None = None):
        if timeout > MAX_TIMEOUT_S:
            raise ValueError(f"超时强制 ≤ {MAX_TIMEOUT_S}s（logging-trace A7）")
        self._client = client if client is not None else httpx.Client(timeout=timeout)
        # 注入的 client 也须绑定 base_url：相对路径请求（如 /api/v1/x）无 base_url 会在
        # cookie 解析处以 "unknown url type" 崩溃（任务书参考实现缺陷，此处修正）。
        self._client.base_url = base_url

    def _headers(self) -> dict[str, str]:
        trace = logx.current_trace() or logx.new_trace_id()
        return {
            "traceparent": f"00-{trace}-{secrets.token_hex(8)}-01",
            "X-Trace-Id": trace,
        }

    def request(self, method: str, path: str, *, json: Any = None, **kw) -> Any:
        try:
            r = self._client.request(method, path, json=json, headers=self._headers(), **kw)
        except httpx.TimeoutException:
            raise BizError(1008) from None
        except httpx.TransportError:
            raise BizError(1009) from None
        if r.status_code >= 500:
            raise BizError(1009, detail=f"下游 HTTP {r.status_code}")
        try:
            envelope = r.json()
        except ValueError:
            raise BizError(1009, detail="下游响应非 JSON 信封") from None
        if not isinstance(envelope, dict) or "code" not in envelope or "message" not in envelope:
            raise BizError(1009, detail="下游响应非 RestResponse 信封")
        if envelope["code"] != 0:
            raise BizError(int(envelope["code"]), message=str(envelope["message"]))
        return envelope.get("data")

    def get(self, path: str, **kw) -> Any:
        return self.request("GET", path, **kw)

    def post(self, path: str, *, json: Any = None, **kw) -> Any:
        return self.request("POST", path, json=json, **kw)

    def put(self, path: str, *, json: Any = None, **kw) -> Any:
        return self.request("PUT", path, json=json, **kw)

    def patch(self, path: str, *, json: Any = None, **kw) -> Any:
        return self.request("PATCH", path, json=json, **kw)

    def delete(self, path: str, **kw) -> Any:
        return self.request("DELETE", path, **kw)
