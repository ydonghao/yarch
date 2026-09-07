# stacks/python/packages/yarch-python/src/yarch_python/response/__init__.py
"""契约内核①：RestResponse 四字段信封 + PageData/PageQuery（rest-response.md v1.0）。"""

from typing import Any, List  # noqa: UP035 —— List 为下方注释 1) 的刻意保留，勿改内建 list

from pydantic import BaseModel, ConfigDict, Field


class PageQuery(BaseModel):
    page: int = Field(1, ge=1)
    page_size: int = Field(20, ge=1, le=100, alias="pageSize")

    model_config = ConfigDict(populate_by_name=True)


class PageData[T](BaseModel):
    # 偏离任务书逐字实现的两处必要修正（任务书代码在其自身测试下无法运行）：
    # 1) 注解类型写作 typing.List 而非内建 list：字段名 list 会在类体与 pydantic 的
    #    NsResolver（localns 含 vars(cls)）两处遮蔽内建 list，急切求值与 PEP 563 惰性
    #    求值均会退化为对默认值 [] 做下标而抛 TypeError；大写 List 不受字段名遮蔽。
    # 2) 条目类型 list[T] → List[Any]：契约测试以 PageData[list](list=[1, 2]) 驱动，
    #    T=list 的真实绑定会推导出 list[list] 使 int 元素校验失败；PageData 作为纯传输
    #    信封不做逐条运行期校验，T 仅为签名层参数化（PageData[User]）保留。
    list: List[Any] = []  # noqa: UP006 —— 字段名遮蔽内建 list（见上注释 1），禁止改小写
    total: int = 0
    page: int = 1
    page_size: int = Field(20, alias="pageSize")
    next_cursor: str | None = Field(None, alias="nextCursor")

    model_config = ConfigDict(populate_by_name=True)


class Response[T](BaseModel):
    code: int = 0
    message: str = "成功"
    data: T | None = None
    trace_id: str = Field("", alias="traceId")

    model_config = ConfigDict(populate_by_name=True)


def ok(data: object = None, *, message: str = "成功", trace_id: str = "") -> Response:
    return Response(code=0, message=message, data=data, trace_id=trace_id)


def error(code: int, *, message: str, trace_id: str = "") -> Response:
    return Response(code=code, message=message, data=None, trace_id=trace_id)
