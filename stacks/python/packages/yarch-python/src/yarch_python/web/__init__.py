# stacks/python/packages/yarch-python/src/yarch_python/web/__init__.py
"""web：FastAPI 一行装配 + 信封回包 + 1001/1002 分型 + 分页绑定（rest-conventions.md v1.0）。"""

from typing import Any

from fastapi import FastAPI, Request
from fastapi.exceptions import RequestValidationError
from starlette.exceptions import HTTPException as StarletteHTTPException
from starlette.responses import Response as RawResponse

from yarch_python import errcode, logx
from yarch_python.middleware import AccessLogMiddleware, RecoveryMiddleware, TraceMiddleware
from yarch_python.response import PageData, PageQuery, Response
from yarch_python.xerror import BizError

_HTTP_EXCEPTION_MAP = {401: 2001, 403: 2003, 404: 1004, 409: 1005, 429: 1006}


def _envelope_raw(resp: Response) -> RawResponse:
    return RawResponse(
        content=resp.model_dump_json(by_alias=True),
        media_type="application/json",
        status_code=errcode.http_of(resp.code),
    )


def ok(data: Any = None, *, status_code: int = 200, message: str = "成功") -> RawResponse:
    resp: Response[Any] = Response(
        code=0, message=message, data=data, trace_id=logx.current_trace()
    )
    return RawResponse(
        resp.model_dump_json(by_alias=True), status_code=status_code, media_type="application/json"
    )


def page(
    items: list,
    total: int,
    page: int,
    page_size: int,
    next_cursor: str | None = None,
    *,
    status_code: int = 200,
) -> RawResponse:
    pd = PageData[list](
        list=items, total=total, page=page, page_size=page_size, next_cursor=next_cursor or None
    )
    resp: Response[Any] = Response(code=0, data=pd, trace_id=logx.current_trace())
    return RawResponse(
        resp.model_dump_json(by_alias=True, exclude_none=True),
        status_code=status_code,
        media_type="application/json",
    )


def page_query(request: Request) -> PageQuery:
    try:
        p = int(request.query_params.get("page", "1"))
        s = int(request.query_params.get("pageSize", "20"))
    except ValueError:
        raise BizError(1001, detail="page/pageSize 须为整数") from None
    if p < 1 or s < 1 or s > 100:
        raise BizError(1001, detail="page 须为正整数，pageSize 须在 1~100")
    return PageQuery(page=p, page_size=s)


def setup(
    app: FastAPI,
    *,
    service: str,
    env: str,
    idempotency_store: Any = None,
    rate_limit: tuple[Any, int, int] | None = None,
) -> None:
    logx.setup(service, env)
    app.state.service = service
    # starlette：后 add 的在外层。目标外→内：AccessLog > Trace > Recovery > (Rate) > (Idem)。
    # Rate 必须在 Idem 外层：限流拒绝的请求不得进入幂等流程——否则 429 会被幂等层捕获
    # 落库为 done，同键合法重试在 TTL 内永远回放 429、操作永不执行
    if idempotency_store is not None:
        from yarch_python.middleware.idempotency import IdempotencyMiddleware

        app.add_middleware(IdempotencyMiddleware, store=idempotency_store, service=service)
    if rate_limit is not None:
        from yarch_python.middleware.ratelimit import RateLimitMiddleware

        limiter, limit, window_s = rate_limit
        app.add_middleware(
            RateLimitMiddleware, limiter=limiter, limit=limit, window_s=window_s, service=service
        )
    app.add_middleware(RecoveryMiddleware)
    app.add_middleware(TraceMiddleware)
    app.add_middleware(AccessLogMiddleware)

    @app.exception_handler(BizError)
    async def _biz(request: Request, exc: BizError) -> RawResponse:
        return _envelope_raw(
            Response(code=exc.code, message=exc.message, data=None, trace_id=logx.current_trace())
        )

    @app.exception_handler(RequestValidationError)
    async def _validation(request: Request, exc: RequestValidationError) -> RawResponse:
        types = {e.get("type") for e in exc.errors()}
        if types & {"json_invalid", "json_decode_error"}:
            code = 1002
        else:
            code = 1001
        first = exc.errors()[0]
        loc = ".".join(str(x) for x in first["loc"][1:]) or "body"
        detail = f"{loc} {first['msg']}"
        return _envelope_raw(
            Response(
                code=code,
                message=f"{errcode.message_of(code)}：{detail}",
                data=None,
                trace_id=logx.current_trace(),
            )
        )

    @app.exception_handler(StarletteHTTPException)
    async def _http_exc(request: Request, exc: StarletteHTTPException) -> RawResponse:
        code = _HTTP_EXCEPTION_MAP.get(exc.status_code, 1000)
        message = errcode.message_of(code)
        if exc.detail and exc.detail != "Not Found" and exc.status_code == 404:
            message = f"{message}：{exc.detail}"
        return _envelope_raw(
            Response(code=code, message=message, data=None, trace_id=logx.current_trace())
        )
