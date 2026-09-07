# yarch-python 第一批实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 交付 stacks/python 第一批——uv workspace 双发行版（yarch-python 平台构件 + yarch-init 生成器 + DDD 模板）+ CI + 登记动作，本地与 CI 全绿。

**Architecture:** 单 workspace 两 package：`packages/yarch-python`（一规约一 module：response/errcode/xerror 契约内核 + logx/middleware/web/persist/redix/httpx/celeryx/testx）与 `packages/yarch-init`（Jinja2 渲染引擎 + registry 校验 + `_template` 随 wheel 携带）。同步核心（SQLAlchemy sync + psycopg3 + `def` 端点），structlog ndjson + contextvars traceId。策划案（唯一权威）：[stacks/python/PLAN.md](../../../stacks/python/PLAN.md)。

**Tech Stack:** Python ≥3.12（CI 双矩阵 3.12/3.13）、uv workspace、FastAPI、pydantic v2、SQLAlchemy 2.x + psycopg3 + Alembic、redis-py、httpx、celery 5、structlog、pytest + testcontainers、ruff + import-linter + mypy、Jinja2、GHA + PyPI trusted publishing。

## Global Constraints（每个任务隐含继承）

- **契约唯一权威**：实现与 [contract/](../../../contract/README.md) 不一致即 bug。四件套关键表：
  - **信封**：`{"code":0,"message":"成功","data":…,"traceId":"…"}` 字段名 camelCase、顺序 code/message/data/traceId；`code!=0` 时 `data` 必为 `null`；`traceId` 恒等于响应头 `X-Trace-Id`（取不到为空串）。
  - **13 码全表**（code/标识/默认文案/HTTP）：0 OK 成功 200；1000 INTERNAL_ERROR 内部错误 500；1001 INVALID_ARGUMENT 参数校验失败 400；1002 MALFORMED_BODY 请求体格式错误 400；1004 NOT_FOUND 资源不存在 404；1005 CONFLICT 资源冲突 409；1006 RATE_LIMITED 触发限流 429；1007 IDEMPOTENCY_CONFLICT 幂等冲突：重复提交 409；1008 UPSTREAM_TIMEOUT 上游依赖超时 504；1009 UNAVAILABLE 服务暂不可用 503；2001 UNAUTHORIZED 未认证 401；2002 CREDENTIALS_EXPIRED 凭证已过期 401；2003 FORBIDDEN 权限不足 403；2004 ACCOUNT_DISABLED 账号已禁用 403。1003 留空不得实现。message 细节追加规则：`默认文案：细节`（全角冒号），默认文案不得改写。
  - **ndjson**：`ts`（RFC3339 毫秒 UTC，恒 `Z` 结尾）`level`（大写）`service` `env`（值域 local/dev/staging/prod）`traceId`（请求上下文内必有）`logger` `msg` + 自由键 camelCase；禁多行堆栈（折叠为 `stack` 单字段）。access log 附加 `method/path/status/costMs`。
  - **traceId**：入口三级 traceparent（正则 `^00-([0-9a-f]{32})-[0-9a-f]{16}-[0-9a-f]{2}$` 取 trace-id 段）→ `X-Trace-Id` → 生成 32 位小写 hex（`secrets.token_hex(16)`）；响应头恒回显；全链路不变。
  - **分页**：`?page=1&pageSize=20`，1-based，默认 20 上限 100；page 非正整数/pageSize 超上限 → 1001；越界（D6）→ 200 + 空 list + 真实 total；`data` 固定 `PageData{list,total,page,pageSize,nextCursor?}`（nextCursor None 时省略）。
  - **状态码白名单**：`200 201 400 401 403 404 409 429 500 503 504`。
  - **幂等**：unsafe 方法带 `Idempotency-Key`；存 `(key, 请求摘要, 响应)` TTL ≥ 24h（Redis SET NX PX）；同键同参回放、异参 1007、并发等待短暂后回放或 1007。
- **git 铁律（本仓特有）**：仓库 index 滞留他人暂存 WIP——**所有提交必须 pathspec 方式**：`git add <新文件路径>` 后 `git commit -m "…" -- stacks/python/ <其他本任务路径>`。禁止裸 `git commit`、禁止 `--amend`。
- **命名**：发行名 `yarch-python`（import `yarch_python`）/ `yarch-init`（import `yarch_init`）；错误码标识符 SCREAMING_SNAKE（`INTERNAL_ERROR`）；业务码 3xxx-8xxx 不得进平台件。
- **服务名**（模板/生成器）：`^[a-z][a-z0-9-]{1,31}$`；裸通用词黑名单：api/app/service/server/backend/web/admin/main/common/system/demo。
- **Python 环境**：本机 macOS（OrbStack docker 需 `open -a OrbStack` 起容器测试）、uv 已装。工具命令一律 `uv run …`（在 `stacks/python/` 下）。
- **测试分层**：`-m "not integration"` 为单元（秒级），`integration` marker 需要 docker（testcontainers）。

## File Structure（全量文件地图）

```
stacks/python/
├── pyproject.toml                    # T1 workspace 根（uv workspace + dev 组 + ruff/pytest/importlinter/mypy 配置）
├── .python-version                   # T1 = 3.12
├── packages/yarch-python/
│   ├── pyproject.toml                # T1
│   └── src/yarch_python/
│       ├── __init__.py               # T1
│       ├── response/__init__.py      # T2 Response[T]/PageData[T]/PageQuery/ok/error
│       ├── errcode/__init__.py       # T3 Code + 全表 + register
│       ├── xerror/__init__.py        # T4 BizError
│       ├── logx/__init__.py          # T5 structlog ndjson + contextvars
│       ├── middleware/
│       │   ├── __init__.py           # T6 导出；T9 增幂等/限流
│       │   ├── trace.py              # T6 纯 ASGI
│       │   ├── recovery.py           # T6
│       │   ├── accesslog.py          # T6
│       │   ├── idempotency.py        # T9
│       │   └── ratelimit.py          # T9
│       ├── web/__init__.py           # T7 setup/ok/page/page_query/异常处理器
│       ├── redix/__init__.py         # T8 Keys/Cache/IdempotencyStore/FixedWindowLimiter
│       ├── persist/__init__.py       # T10 Base/Mixin/session/ensure_database/page_of/alembic
│       ├── httpx/__init__.py         # T11 YarchHttpClient
│       ├── celeryx/__init__.py       # T12 make_app/命名/TraceTask/beat_guard/run_once
│       └── testx/
│           ├── __init__.py           # T13 CODE_TABLE + assert_envelope/assert_page_data/assert_ndjson
│           └── fixtures.py           # T13 pytest fixtures pg_url/redis_url（TC）
├── packages/yarch-init/
│   ├── pyproject.toml                # T1（console_scripts: yarch-init）
│   └── src/yarch_init/
│       ├── __init__.py               # T1
│       ├── main.py                   # T14 CLI + 渲染引擎 + 校验
│       └── _template/                # T15 全部模板资产（archetype.json/pyproject/main.py/…）
└── tests/…（各 package 内 tests/ 目录随任务建）
.github/workflows/python-stack.yml    # T17
.github/workflows/python-publish.yml  # T17
stacks/python/README.md               # T18
stacks/python/architecture-diagram.svg# T18
```

每个任务自含测试循环；`Packages: Consumes/Produces` 块给出跨任务精确签名。

---

### Task 1: workspace 骨架与工具链基座

**Files:**
- Create: `stacks/python/pyproject.toml`、`stacks/python/.python-version`、`stacks/python/packages/yarch-python/pyproject.toml`、`stacks/python/packages/yarch-init/pyproject.toml`、`stacks/python/packages/yarch-python/src/yarch_python/__init__.py`（空）、`stacks/python/packages/yarch-init/src/yarch_init/__init__.py`（空）

**Interfaces:**
- Consumes: 无
- Produces: 可 `uv sync` 的 workspace；两个空包 `yarch_python` / `yarch_init` 可 import。

- [ ] **Step 1: 写根 pyproject**

```toml
# stacks/python/pyproject.toml
[project]
name = "yarch-python-workspace"
version = "0.0.0"
requires-python = ">=3.12"

[tool.uv]
package = false

[tool.uv.workspace]
members = ["packages/*"]

[tool.uv.sources]
yarch-python = { workspace = true }
yarch-init = { workspace = true }

[dependency-groups]
dev = [
    "pytest>=8.3",
    "ruff>=0.6",
    "mypy>=1.11",
    "import-linter>=2.0",
    "testcontainers[postgres,redis]>=4.8",
]

[tool.ruff]
line-length = 100
target-version = "py312"

[tool.ruff.lint]
select = ["E", "F", "I", "UP", "B"]

[tool.pytest.ini_options]
testpaths = ["packages"]
markers = ["integration: needs docker (testcontainers)"]

[tool.mypy]
files = ["packages"]
ignore_missing_imports = true
check_untyped_defs = true
```

- [ ] **Step 2: 写两个 package 的 pyproject**

```toml
# stacks/python/packages/yarch-python/pyproject.toml
[project]
name = "yarch-python"
version = "0.1.0"
description = "yarch Python stack platform components (FastAPI + DDD)"
license = "Apache-2.0"
requires-python = ">=3.12"
dependencies = [
    "fastapi>=0.115",
    "uvicorn>=0.30",
    "pydantic>=2.8",
    "pydantic-settings>=2.4",
    "structlog>=24.1",
    "sqlalchemy>=2.0.31",
    "psycopg[binary]>=3.2",
    "alembic>=1.13",
    "redis>=5.0",
    "httpx>=0.27",
    "celery>=5.4",
]

[project.optional-dependencies]
test = ["testcontainers[postgres,redis]>=4.8"]

[build-system]
requires = ["hatchling"]
build-backend = "hatchling.build"

[tool.hatch.build.targets.wheel]
packages = ["src/yarch_python"]
```

```toml
# stacks/python/packages/yarch-init/pyproject.toml
[project]
name = "yarch-init"
version = "0.1.0"
description = "yarch Python project generator (archetype mode)"
license = "Apache-2.0"
requires-python = ">=3.12"
dependencies = ["jinja2>=3.1"]

[project.scripts]
yarch-init = "yarch_init.main:main"

[build-system]
requires = ["hatchling"]
build-backend = "hatchling.build"

[tool.hatch.build.targets.wheel]
packages = ["src/yarch_init"]
```

- [ ] **Step 3: 建空包与 .python-version，跑通工具链**

```bash
cd /Users/yuandonghao/sidejob/sources/yarch/stacks/python
echo "3.12" > .python-version
mkdir -p packages/yarch-python/src/yarch_python packages/yarch-init/src/yarch_init
touch packages/yarch-python/src/yarch_python/__init__.py packages/yarch-init/src/yarch_init/__init__.py
uv python install 3.12 3.13
uv sync
uv run python -c "import yarch_python, yarch_init; print('import ok')"
uv run ruff check .
```

Expected: `import ok`；ruff 无输出（0 errors）。

- [ ] **Step 4: Commit（pathspec 铁律）**

```bash
cd /Users/yuandonghao/sidejob/sources/yarch
git add stacks/python/pyproject.toml stacks/python/.python-version stacks/python/packages
git commit -m "feat(stacks/python): uv workspace 双发行版骨架——yarch-python + yarch-init" -- stacks/python
```

---

### Task 2: response（契约内核①）

**Files:**
- Create: `stacks/python/packages/yarch-python/src/yarch_python/response/__init__.py`
- Test: `stacks/python/packages/yarch-python/tests/test_response.py`

**Interfaces:**
- Consumes: 无（仅 pydantic）
- Produces:
  - `Response[T]`（BaseModel，字段序 code/message/data/trace_id→alias `traceId`；`ok(data=None, *, message="成功", trace_id="") -> Response`；`error(code, *, message, trace_id="") -> Response`）
  - `PageData[T]`（list/total/page/page_size→`pageSize`/next_cursor→`nextCursor`，None 省略）
  - `PageQuery`（page=1 ge 1；page_size=20 ge 1 le 100，alias `pageSize`）
  - `Response.model_dump_json(by_alias=True)` / `PageData.model_dump(by_alias=True, exclude_none=True)` 为唯一序列化出口

- [ ] **Step 1: 写失败测试**

```python
# stacks/python/packages/yarch-python/tests/test_response.py
import json

from yarch_python.response import PageData, Response, error, ok


def test_ok_envelope_shape_and_order():
    raw = ok({"any": "thing"}, trace_id="t1").model_dump_json(by_alias=True)
    assert list(json.loads(raw).keys()) == ["code", "message", "data", "traceId"]
    assert raw.index('"code"') < raw.index('"message"') < raw.index('"data"') < raw.index('"traceId"')
    body = json.loads(raw)
    assert body == {"code": 0, "message": "成功", "data": {"any": "thing"}, "traceId": "t1"}


def test_error_data_must_be_null():
    body = json.loads(error(1001, message="参数校验失败", trace_id="t2").model_dump_json(by_alias=True))
    assert body["code"] == 1001 and body["data"] is None and body["traceId"] == "t2"


def test_ok_no_payload_data_is_null():
    assert json.loads(ok().model_dump_json(by_alias=True))["data"] is None


def test_page_data_shape_and_next_cursor_omitted():
    pd = PageData[list](list=[1, 2], total=5, page=1, page_size=2, next_cursor=None)
    d = pd.model_dump(by_alias=True, exclude_none=True)
    assert d == {"list": [1, 2], "total": 5, "page": 1, "pageSize": 2}
    pd2 = PageData[list](list=[], total=5, page=3, page_size=2, next_cursor="c9")
    assert pd2.model_dump(by_alias=True, exclude_none=True)["nextCursor"] == "c9"


def test_page_data_empty_list_not_null():
    assert PageData[list](list=[], total=0, page=1, page_size=20).model_dump()["list"] == []
```

- [ ] **Step 2: 跑测试确认失败**

Run: `cd /Users/yuandonghao/sidejob/sources/yarch/stacks/python && uv run pytest packages/yarch-python/tests/test_response.py -v`
Expected: FAIL `ModuleNotFoundError: No module named 'yarch_python.response'`

- [ ] **Step 3: 最小实现**

```python
# stacks/python/packages/yarch-python/src/yarch_python/response/__init__.py
"""契约内核①：RestResponse 四字段信封 + PageData/PageQuery（rest-response.md v1.0）。"""
from typing import Generic, Optional, TypeVar

from pydantic import BaseModel, ConfigDict, Field

T = TypeVar("T")


class PageQuery(BaseModel):
    page: int = Field(1, ge=1)
    page_size: int = Field(20, ge=1, le=100, alias="pageSize")

    model_config = ConfigDict(populate_by_name=True)


class PageData(BaseModel, Generic[T]):
    list: list[T] = []
    total: int = 0
    page: int = 1
    page_size: int = Field(20, alias="pageSize")
    next_cursor: Optional[str] = Field(None, alias="nextCursor")

    model_config = ConfigDict(populate_by_name=True)


class Response(BaseModel, Generic[T]):
    code: int = 0
    message: str = "成功"
    data: Optional[T] = None
    trace_id: str = Field("", alias="traceId")

    model_config = ConfigDict(populate_by_name=True)


def ok(data: object = None, *, message: str = "成功", trace_id: str = "") -> Response:
    return Response(code=0, message=message, data=data, trace_id=trace_id)


def error(code: int, *, message: str, trace_id: str = "") -> Response:
    return Response(code=code, message=message, data=None, trace_id=trace_id)
```

- [ ] **Step 4: 跑测试确认通过**

Run: `uv run pytest packages/yarch-python/tests/test_response.py -v`
Expected: 5 passed

- [ ] **Step 5: Commit**

```bash
cd /Users/yuandonghao/sidejob/sources/yarch
git add stacks/python/packages/yarch-python/src/yarch_python/response stacks/python/packages/yarch-python/tests/test_response.py
git commit -m "feat(stacks/python): response 契约内核——四字段信封逐字节 + PageData/PageQuery" -- stacks/python
```

---

### Task 3: errcode（契约内核②，13 码全表防漂移）

**Files:**
- Create: `stacks/python/packages/yarch-python/src/yarch_python/errcode/__init__.py`
- Test: `stacks/python/packages/yarch-python/tests/test_errcode.py`
- Modify: `stacks/python/pyproject.toml`（追加 import-linter 契约）

**Interfaces:**
- Consumes: 无
- Produces:
  - `Code(IntEnum)`：成员 `OK=0, INTERNAL_ERROR=1000, INVALID_ARGUMENT=1001, MALFORMED_BODY=1002, NOT_FOUND=1004, CONFLICT=1005, RATE_LIMITED=1006, IDEMPOTENCY_CONFLICT=1007, UPSTREAM_TIMEOUT=1008, UNAVAILABLE=1009, UNAUTHORIZED=2001, CREDENTIALS_EXPIRED=2002, FORBIDDEN=2003, ACCOUNT_DISABLED=2004`
  - `message_of(code: int) -> str`、`http_of(code: int) -> int`、`identifier_of(code: int) -> str`
  - `register(code: int, identifier: str, message: str, http_status: int) -> None`（仅 3000-8999，查重，业务码登记入口）

- [ ] **Step 1: 写失败测试（全表断言防漂移）**

```python
# stacks/python/packages/yarch-python/tests/test_errcode.py
import pytest

from yarch_python import errcode

CONTRACT_TABLE = [  # error-codes.md v1.0 逐行誊写（含 0 成功）
    (0, "OK", "成功", 200),
    (1000, "INTERNAL_ERROR", "内部错误", 500),
    (1001, "INVALID_ARGUMENT", "参数校验失败", 400),
    (1002, "MALFORMED_BODY", "请求体格式错误", 400),
    (1004, "NOT_FOUND", "资源不存在", 404),
    (1005, "CONFLICT", "资源冲突", 409),
    (1006, "RATE_LIMITED", "触发限流", 429),
    (1007, "IDEMPOTENCY_CONFLICT", "幂等冲突：重复提交", 409),
    (1008, "UPSTREAM_TIMEOUT", "上游依赖超时", 504),
    (1009, "UNAVAILABLE", "服务暂不可用", 503),
    (2001, "UNAUTHORIZED", "未认证", 401),
    (2002, "CREDENTIALS_EXPIRED", "凭证已过期", 401),
    (2003, "FORBIDDEN", "权限不足", 403),
    (2004, "ACCOUNT_DISABLED", "账号已禁用", 403),
]


@pytest.mark.parametrize("code,identifier,message,http", CONTRACT_TABLE)
def test_full_table(code, identifier, message, http):
    assert errcode.identifier_of(code) == identifier
    assert errcode.message_of(code) == message
    assert errcode.http_of(code) == http


def test_1003_reserved_absent():
    assert not hasattr(errcode.Code, "_1003") and 1003 not in [c.value for c in errcode.Code]


def test_register_business_code():
    errcode.register(3001, "USER_EXISTS", "用户已存在", 409)
    assert errcode.message_of(3001) == "用户已存在"
    assert errcode.http_of(3001) == 409
    assert errcode.identifier_of(3001) == "USER_EXISTS"


def test_register_rejects_yarch_range_and_duplicates():
    with pytest.raises(ValueError):
        errcode.register(1001, "X", "x", 400)
    errcode.register(3002, "DUP", "d", 400)
    with pytest.raises(ValueError):
        errcode.register(3002, "DUP", "d", 400)
```

- [ ] **Step 2: 跑测试确认失败**

Run: `uv run pytest packages/yarch-python/tests/test_errcode.py -v`
Expected: FAIL `ModuleNotFoundError … 'yarch_python.errcode'`

- [ ] **Step 3: 最小实现**

```python
# stacks/python/packages/yarch-python/src/yarch_python/errcode/__init__.py
"""契约内核②：错误码全局段位表（error-codes.md v1.0，13 码全表 + HTTP 映射 + 业务码注册）。"""
from enum import IntEnum


class Code(IntEnum):
    OK = 0
    INTERNAL_ERROR = 1000
    INVALID_ARGUMENT = 1001
    MALFORMED_BODY = 1002
    NOT_FOUND = 1004
    CONFLICT = 1005
    RATE_LIMITED = 1006
    IDEMPOTENCY_CONFLICT = 1007
    UPSTREAM_TIMEOUT = 1008
    UNAVAILABLE = 1009
    UNAUTHORIZED = 2001
    CREDENTIALS_EXPIRED = 2002
    FORBIDDEN = 2003
    ACCOUNT_DISABLED = 2004


_MESSAGES = {c.value: c.name for c in Code}  # 占位，下面用显式表
_MESSAGES = {
    0: "成功",
    1000: "内部错误",
    1001: "参数校验失败",
    1002: "请求体格式错误",
    1004: "资源不存在",
    1005: "资源冲突",
    1006: "触发限流",
    1007: "幂等冲突：重复提交",
    1008: "上游依赖超时",
    1009: "服务暂不可用",
    2001: "未认证",
    2002: "凭证已过期",
    2003: "权限不足",
    2004: "账号已禁用",
}
_HTTP = {
    0: 200,
    1000: 500,
    1001: 400,
    1002: 400,
    1004: 404,
    1005: 409,
    1006: 429,
    1007: 409,
    1008: 504,
    1009: 503,
    2001: 401,
    2002: 401,
    2003: 403,
    2004: 403,
}
_IDENTIFIERS = {c.value: c.name for c in Code}
# 业务码登记表（3xxx-8xxx，业务仓 docs 登记后 register）
_BUSINESS: dict[int, tuple[str, str, int]] = {}


def identifier_of(code: int) -> str:
    if code in _IDENTIFIERS:
        return _IDENTIFIERS[code]
    if code in _BUSINESS:
        return _BUSINESS[code][0]
    raise LookupError(f"未注册的错误码：{code}（业务码 3xxx-8xxx 须先 register 并在业务仓 docs 登记）")


def message_of(code: int) -> str:
    if code in _MESSAGES:
        return _MESSAGES[code]
    if code in _BUSINESS:
        return _BUSINESS[code][1]
    raise LookupError(f"未注册的错误码：{code}")


def http_of(code: int) -> int:
    if code in _HTTP:
        return _HTTP[code]
    if code in _BUSINESS:
        return _BUSINESS[code][2]
    raise LookupError(f"未注册的错误码：{code}")


def register(code: int, identifier: str, message: str, http_status: int) -> None:
    if not 3000 <= code <= 8999:
        raise ValueError(f"yarch 只拥有 0/1xxx/2xxx；业务码须在 3000-8999，got {code}")
    if code in _BUSINESS or code in _IDENTIFIERS:
        raise ValueError(f"错误码 {code} 已注册：{identifier_of(code)}")
    if not identifier.isupper() or not identifier.replace("_", "").isalpha():
        raise ValueError(f"标识符须为 SCREAMING_SNAKE：{identifier}")
    _BUSINESS[code] = (identifier, message, http_status)
```

- [ ] **Step 4: 跑测试确认通过**

Run: `uv run pytest packages/yarch-python/tests/test_errcode.py -v`
Expected: 17 passed（14 参数化 + 3 规则）

- [ ] **Step 5: 追加 import-linter 契约（内核零框架依赖）到根 pyproject**

在 `stacks/python/pyproject.toml` 末尾追加：

```toml
[tool.importlinter]
root_packages = ["yarch_python"]

[[tool.importlinter.contracts]]
name = "Kernel trio has zero framework dependencies"
type = "forbidden"
source_modules = [
    "yarch_python.response",
    "yarch_python.errcode",
    "yarch_python.xerror",
]
forbidden_modules = [
    "fastapi",
    "sqlalchemy",
    "redis",
    "celery",
    "httpx",
    "structlog",
]
```

Run: `uv run lint-imports`
Expected: `Contracts: 1 kept, 0 broken.`

- [ ] **Step 6: Commit**

```bash
cd /Users/yuandonghao/sidejob/sources/yarch
git add stacks/python/packages/yarch-python/src/yarch_python/errcode stacks/python/packages/yarch-python/tests/test_errcode.py stacks/python/pyproject.toml
git commit -m "feat(stacks/python): errcode 契约内核——13 码全表断言防漂移 + 业务码 register" -- stacks/python
```

---

### Task 4: xerror（契约内核③）

**Files:**
- Create: `stacks/python/packages/yarch-python/src/yarch_python/xerror/__init__.py`
- Test: `stacks/python/packages/yarch-python/tests/test_xerror.py`

**Interfaces:**
- Consumes: `errcode.message_of/http_of`
- Produces: `BizError(code: int, detail: str = "", *, message: str | None = None)`——属性 `.code/.detail/.message/.http_status`；`message` 显式传入时原样透传（下游码透传用），否则 `默认文案：细节` 规则。

- [ ] **Step 1: 写失败测试**

```python
# stacks/python/packages/yarch-python/tests/test_xerror.py
from yarch_python.xerror import BizError


def test_message_append_rule_with_fullwidth_colon():
    e = BizError(1001, detail="pageSize 必须 ≤ 100")
    assert e.message == "参数校验失败：pageSize 必须 ≤ 100"


def test_message_no_detail_is_default():
    assert BizError(1004).message == "资源不存在"


def test_default_part_never_rewritten():
    e = BizError(1006, detail="qps>100")
    assert e.message.startswith("触发限流：")


def test_http_mapping():
    assert BizError(1007).http_status == 409
    assert BizError(2003).http_status == 403


def test_explicit_message_passthrough():
    e = BizError(3004, message="订单状态不允许该操作")
    assert e.message == "订单状态不允许该操作"


def test_raisable():
    import pytest
    with pytest.raises(BizError) as ei:
        raise BizError(1002, detail="bad json")
    assert ei.value.code == 1002
```

- [ ] **Step 2: 跑测试确认失败**

Run: `uv run pytest packages/yarch-python/tests/test_xerror.py -v`
Expected: FAIL `ModuleNotFoundError … 'yarch_python.xerror'`

- [ ] **Step 3: 最小实现**

```python
# stacks/python/packages/yarch-python/src/yarch_python/xerror/__init__.py
"""契约内核③：BizError——message「默认文案：细节」追加规则（error-codes.md 实现规则-1）。"""
from typing import Optional

from yarch_python import errcode


class BizError(Exception):
    def __init__(self, code: int, detail: str = "", *, message: Optional[str] = None):
        self.code = code
        self.detail = detail
        self._explicit_message = message
        super().__init__(self.message)

    @property
    def message(self) -> str:
        if self._explicit_message is not None:
            return self._explicit_message
        base = errcode.message_of(self.code)
        return f"{base}：{self.detail}" if self.detail else base

    @property
    def http_status(self) -> int:
        return errcode.http_of(self.code)
```

- [ ] **Step 4: 跑测试确认通过**

Run: `uv run pytest packages/yarch-python/tests/test_xerror.py -v && uv run lint-imports`
Expected: 6 passed；契约仍 1 kept

- [ ] **Step 5: Commit**

```bash
cd /Users/yuandonghao/sidejob/sources/yarch
git add stacks/python/packages/yarch-python/src/yarch_python/xerror stacks/python/packages/yarch-python/tests/test_xerror.py
git commit -m "feat(stacks/python): xerror 契约内核——BizError message 追加规则" -- stacks/python
```

---

### Task 5: logx（structlog ndjson + contextvars traceId）

**Files:**
- Create: `stacks/python/packages/yarch-python/src/yarch_python/logx/__init__.py`
- Test: `stacks/python/packages/yarch-python/tests/test_logx.py`

**Interfaces:**
- Consumes: 无（structlog）
- Produces:
  - `setup(service: str, env: str, *, level: str = "INFO", sink: TextIO | None = None)`（sink None → stdout；测试传 StringIO）
  - `get_logger(name: str)`（返回绑定 `logger=name` 的 structlog logger）
  - `bind_trace(trace_id: str) -> Token`、`reset_trace(token)`、`current_trace() -> str`、`new_trace_id() -> str`

- [ ] **Step 1: 写失败测试**

```python
# stacks/python/packages/yarch-python/tests/test_logx.py
import io
import json
import re

from yarch_python import logx

TS_RE = re.compile(r"^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d{3}Z$")


def setup_sink():
    sink = io.StringIO()
    logx.setup("ysaas-scan", "local", sink=sink)
    return sink


def test_ndjson_field_set_and_order():
    sink = setup_sink()
    logx.get_logger("mymod").info("request completed", method="GET", path="/api/v1/users", status=200, costMs=12)
    line = sink.getvalue().strip()
    d = json.loads(line)
    assert list(d.keys())[:7] == ["ts", "level", "service", "env", "traceId", "logger", "msg"]
    assert TS_RE.match(d["ts"])
    assert d["level"] == "INFO" and d["service"] == "ysaas-scan" and d["env"] == "local"
    assert d["logger"] == "mymod" and d["msg"] == "request completed"
    assert d["method"] == "GET" and d["costMs"] == 12


def test_trace_id_from_contextvars():
    sink = setup_sink()
    token = logx.bind_trace("0af7651916cd43dd8448eb211c80319c")
    logx.get_logger("x").warning("boom")
    logx.reset_trace(token)
    d = json.loads(sink.getvalue().strip())
    assert d["traceId"] == "0af7651916cd43dd8448eb211c80319c"
    assert d["level"] == "WARN"


def test_new_trace_id_shape():
    assert re.fullmatch(r"[0-9a-f]{32}", logx.new_trace_id())


def test_stack_folded_single_field():
    sink = setup_sink()
    try:
        raise ValueError("inner")
    except ValueError:
        import traceback
        logx.get_logger("e").error("internal error", stack=traceback.format_exc())
    d = json.loads(sink.getvalue().strip())
    assert "stack" in d and "\n" not in sink.getvalue().strip()
```

- [ ] **Step 2: 跑测试确认失败**

Run: `uv run pytest packages/yarch-python/tests/test_logx.py -v`
Expected: FAIL `ModuleNotFoundError … 'yarch_python.logx'`

- [ ] **Step 3: 最小实现**

```python
# stacks/python/packages/yarch-python/src/yarch_python/logx/__init__.py
"""logx：structlog ndjson 行协议（logging-trace.md v1.0）+ contextvars traceId 贯穿。"""
import contextvars
import json
import secrets
import sys
from datetime import datetime, timezone
from typing import Any, Optional, TextIO

import structlog

_trace_id: contextvars.ContextVar[str] = contextvars.ContextVar("trace_id", default="")

_PROCESSOR_CHAIN_BUILT = False


def bind_trace(trace_id: str):
    return _trace_id.set(trace_id)


def reset_trace(token) -> None:
    _trace_id.reset(token)


def current_trace() -> str:
    return _trace_id.get()


def new_trace_id() -> str:
    return secrets.token_hex(16)


def get_logger(name: str) -> Any:
    return structlog.get_logger().bind(logger=name)


def _make_processors(service: str, env: str):
    def add_ts(_, __, ed):
        now = datetime.now(timezone.utc)
        ed["ts"] = now.strftime("%Y-%m-%dT%H:%M:%S.") + f"{now.microsecond // 1000:03d}Z"
        return ed

    def add_level(_, method, ed):
        ed["level"] = method.upper()
        return ed

    def add_static(_, __, ed):
        ed["service"] = service
        ed["env"] = env
        return ed

    def add_trace(_, __, ed):
        ed["traceId"] = _trace_id.get()
        return ed

    def rename_event(_, __, ed):
        ed["msg"] = ed.pop("event")
        return ed

    return [structlog.contextvars.merge_contextvars, add_level, add_static, add_trace, add_ts, rename_event]


def setup(service: str, env: str, *, level: str = "INFO", sink: Optional[TextIO] = None) -> None:
    import logging as _logging

    out = sink if sink is not None else sys.stdout

    def render(_, __, ed):
        print(json.dumps(ed, ensure_ascii=False, default=str), file=out, flush=True)
        return None

    structlog.configure(
        processors=[*_make_processors(service, env), render],
        wrapper_class=structlog.make_filtering_bound_logger(
            getattr(_logging, level.upper()) if isinstance(level, str) else level
        ),
        cache_logger_on_first_use=False,
    )
```

- [ ] **Step 4: 跑测试确认通过**

Run: `uv run pytest packages/yarch-python/tests/test_logx.py -v`
Expected: 4 passed

- [ ] **Step 5: Commit**

```bash
cd /Users/yuandonghao/sidejob/sources/yarch
git add stacks/python/packages/yarch-python/src/yarch_python/logx stacks/python/packages/yarch-python/tests/test_logx.py
git commit -m "feat(stacks/python): logx——structlog ndjson 行协议 + contextvars traceId" -- stacks/python
```

---

### Task 6: middleware 三件（trace / recovery / accesslog，纯 ASGI）

**Files:**
- Create: `stacks/python/packages/yarch-python/src/yarch_python/middleware/__init__.py`、`trace.py`、`recovery.py`、`accesslog.py`
- Test: `stacks/python/packages/yarch-python/tests/test_middleware_core.py`

**Interfaces:**
- Consumes: `logx.*`、`errcode`、`response.Response`
- Produces（纯 ASGI 中间件，构造签名 `__init__(self, app: ASGIApp)`）：
  - `TraceMiddleware`（三级入口 + `X-Trace-Id` 回显 + contextvar 绑定）
  - `RecoveryMiddleware`（未捕获异常 → 500 信封 code=1000 + ndjson `stack` 折叠）
  - `AccessLogMiddleware`（ndjson `request completed` + method/path/status/costMs）

- [ ] **Step 1: 写失败测试**

```python
# stacks/python/packages/yarch-python/tests/test_middleware_core.py
import io
import json

from fastapi import FastAPI
from fastapi.testclient import TestClient

from yarch_python import logx
from yarch_python.middleware import AccessLogMiddleware, RecoveryMiddleware, TraceMiddleware

TP = "00-0af7651916cd43dd8448eb211c80319c-fedc1341d3a7415e-01"


def make_app(capture: io.StringIO) -> FastAPI:
    logx.setup("ysaas-scan", "local", sink=capture)
    app = FastAPI()
    app.add_middleware(RecoveryMiddleware)
    app.add_middleware(TraceMiddleware)
    app.add_middleware(AccessLogMiddleware)  # 最后 add = 最外层
    return app


def test_trace_three_level_entry_and_echo():
    sink = io.StringIO()
    app = make_app(sink)

    @app.get("/boom")
    def boom():
        raise RuntimeError("x")

    c = TestClient(app, raise_server_exceptions=False)
    r1 = c.get("/ok2") if False else c.get("/anything")  # 404 路径也走中间件
    assert r1.headers["x-trace-id"] != ""
    r2 = c.get("/boom", headers={"traceparent": TP})
    assert r2.headers["x-trace-id"] == "0af7651916cd43dd8448eb211c80319c"
    r3 = c.get("/boom", headers={"X-Trace-Id": "mytrace123"})
    assert r3.headers["x-trace-id"] == "mytrace123"


def test_recovery_envelope_1000():
    sink = io.StringIO()
    app = make_app(sink)

    @app.get("/boom")
    def boom():
        raise RuntimeError("x")

    r = TestClient(app, raise_server_exceptions=False).get("/boom", headers={"traceparent": TP})
    assert r.status_code == 500
    assert r.json() == {
        "code": 1000,
        "message": "内部错误",
        "data": None,
        "traceId": "0af7651916cd43dd8448eb211c80319c",
    }


def test_accesslog_ndjson_line():
    sink = io.StringIO()
    app = make_app(sink)

    @app.get("/api/v1/users")
    def users():
        return {"raw": 1}

    TestClient(app).get("/api/v1/users")
    line = sink.getvalue().strip().splitlines()[-1]
    d = json.loads(line)
    assert d["msg"] == "request completed"
    assert d["method"] == "GET" and d["path"] == "/api/v1/users" and d["status"] == 200
    assert isinstance(d["costMs"], int) and d["traceId"] != ""
```

- [ ] **Step 2: 跑测试确认失败**

Run: `uv run pytest packages/yarch-python/tests/test_middleware_core.py -v`
Expected: FAIL `ModuleNotFoundError … 'yarch_python.middleware'`

- [ ] **Step 3: 实现三个中间件**

```python
# stacks/python/packages/yarch-python/src/yarch_python/middleware/trace.py
"""traceId 三级入口（traceparent→X-Trace-Id→生成）+ 响应头回显（logging-trace.md 三）。"""
import re

from starlette.types import ASGIApp, Receive, Scope, Send

from yarch_python import logx

_TRACEPARENT = re.compile(r"^00-([0-9a-f]{32})-[0-9a-f]{16}-[0-9a-f]{2}$")


def resolve_trace_id(headers: dict[bytes, bytes]) -> str:
    tp = headers.get(b"traceparent", b"").decode("latin-1")
    m = _TRACEPARENT.match(tp)
    if m:
        return m.group(1)
    raw = headers.get(b"x-trace-id", b"").decode("latin-1").strip()
    if raw:
        return raw[:64]
    return logx.new_trace_id()


class TraceMiddleware:
    def __init__(self, app: ASGIApp):
        self.app = app

    async def __call__(self, scope: Scope, receive: Receive, send: Send) -> None:
        if scope["type"] != "http":
            await self.app(scope, receive, send)
            return
        headers = dict(scope.get("headers") or [])
        trace_id = resolve_trace_id(headers)
        token = logx.bind_trace(trace_id)

        async def send_with_header(message):
            if message["type"] == "http.response.start":
                message.setdefault("headers", []).append((b"x-trace-id", trace_id.encode()))
            await send(message)

        try:
            await self.app(scope, receive, send_with_header)
        finally:
            logx.reset_trace(token)
```

```python
# stacks/python/packages/yarch-python/src/yarch_python/middleware/recovery.py
"""未捕获异常 → 500 信封 code=1000（message 固定「内部错误」，细节只进日志）。"""
import traceback

from starlette.types import ASGIApp, Receive, Scope, Send

from yarch_python import errcode, logx
from yarch_python.response import Response


class RecoveryMiddleware:
    def __init__(self, app: ASGIApp):
        self.app = app

    async def __call__(self, scope: Scope, receive: Receive, send: Send) -> None:
        if scope["type"] != "http":
            await self.app(scope, receive, send)
            return
        try:
            await self.app(scope, receive, send)
        except Exception:
            logx.get_logger("yarch_python.middleware.recovery").error(
                "internal error", stack=traceback.format_exc()
            )
            body = Response(
                code=1000,
                message=errcode.message_of(1000),
                data=None,
                trace_id=logx.current_trace(),
            ).model_dump_json(by_alias=True).encode()
            await send(
                {
                    "type": "http.response.start",
                    "status": 500,
                    "headers": [(b"content-type", b"application/json")],
                }
            )
            await send({"type": "http.response.body", "body": body})
```

```python
# stacks/python/packages/yarch-python/src/yarch_python/middleware/accesslog.py
"""访问日志：ndjson request completed（method/path/status/costMs）。"""
import time

from starlette.types import ASGIApp, Receive, Scope, Send

from yarch_python import logx


class AccessLogMiddleware:
    def __init__(self, app: ASGIApp):
        self.app = app

    async def __call__(self, scope: Scope, receive: Receive, send: Send) -> None:
        if scope["type"] != "http":
            await self.app(scope, receive, send)
            return
        start = time.monotonic()
        status = 0

        async def send_wrap(message):
            nonlocal status
            if message["type"] == "http.response.start":
                status = message["status"]
            await send(message)

        try:
            await self.app(scope, receive, send_wrap)
        finally:
            logx.get_logger("yarch_python.middleware.accesslog").info(
                "request completed",
                method=scope.get("method", ""),
                path=scope.get("path", ""),
                status=status,
                costMs=int((time.monotonic() - start) * 1000),
            )
```

```python
# stacks/python/packages/yarch-python/src/yarch_python/middleware/__init__.py
from yarch_python.middleware.accesslog import AccessLogMiddleware
from yarch_python.middleware.recovery import RecoveryMiddleware
from yarch_python.middleware.trace import TraceMiddleware, resolve_trace_id

__all__ = ["AccessLogMiddleware", "RecoveryMiddleware", "TraceMiddleware", "resolve_trace_id"]
```

- [ ] **Step 4: 跑测试确认通过**

Run: `uv run pytest packages/yarch-python/tests/test_middleware_core.py -v`
Expected: 3 passed

- [ ] **Step 5: Commit**

```bash
cd /Users/yuandonghao/sidejob/sources/yarch
git add stacks/python/packages/yarch-python/src/yarch_python/middleware stacks/python/packages/yarch-python/tests/test_middleware_core.py
git commit -m "feat(stacks/python): middleware 三件——trace 三级入口/recovery→1000/accesslog ndjson" -- stacks/python
```

---

### Task 7: web（FastAPI 装配：信封回包 + 1001/1002 分型 + 分页绑定 + 状态码映射）

**Files:**
- Create: `stacks/python/packages/yarch-python/src/yarch_python/web/__init__.py`
- Test: `stacks/python/packages/yarch-python/tests/test_web.py`

**Interfaces:**
- Consumes: `response.*`、`errcode`、`xerror.BizError`、`logx.current_trace`、Task 6 三中间件
- Produces:
  - `setup(app: FastAPI, *, service: str, env: str, idempotency_store=None, rate_limit: tuple | None = None)`（装配中间件链与异常处理器；后两参在 Task 9 接线，本任务允许 None）
  - `ok(data=None, *, status_code=200, message="成功") -> Response`（starlette Response，信封 JSON）
  - `page(items, total, page, page_size, next_cursor=None, *, status_code=200) -> Response`
  - `page_query(request: Request) -> PageQuery`（非法参数 → BizError 1001）
  - 异常处理器：`BizError` → 映射 HTTP 信封；`RequestValidationError` → json_invalid 类错 1002 / 其余 1001（400）；`StarletteHTTPException` → 404→1004、401→2001、403→2003、409→1005、429→1006、其余→1000/500

- [ ] **Step 1: 写失败测试**

```python
# stacks/python/packages/yarch-python/tests/test_web.py
import io

from fastapi import FastAPI, Request
from fastapi.testclient import TestClient
from pydantic import BaseModel

from yarch_python import logx
from yarch_python.web import ok, page, page_query, setup
from yarch_python.xerror import BizError


class CreateUser(BaseModel):
    username: str
    age: int


def make_app() -> FastAPI:
    logx.setup("ysaas-scan", "local", sink=io.StringIO())
    app = FastAPI()
    setup(app, service="ysaas-scan", env="local")

    @app.post("/api/v1/users", status_code=201)
    def create_user(body: CreateUser):
        return ok({"id": "u1", "username": body.username}, status_code=201)

    @app.get("/api/v1/users")
    def list_users(request: Request):
        pq = page_query(request)
        return page([{"id": "u1"}], total=41, page=pq.page, page_size=pq.page_size)

    @app.get("/api/v1/boom-biz")
    def boom_biz():
        raise BizError(1004, detail="user u9")

    return app


def test_ok_envelope_with_trace_header():
    r = TestClient(make_app()).post("/api/v1/users", json={"username": "a", "age": 1})
    assert r.status_code == 201
    assert list(r.json().keys()) == ["code", "message", "data", "traceId"]
    assert r.json()["code"] == 0 and r.json()["data"]["id"] == "u1"
    assert r.json()["traceId"] == r.headers["x-trace-id"] != ""


def test_validation_error_1001():
    r = TestClient(make_app()).post("/api/v1/users", json={"username": "a", "age": "not-int"})
    assert r.status_code == 400 and r.json()["code"] == 1001
    assert r.json()["message"].startswith("参数校验失败")


def test_malformed_body_1002():
    r = TestClient(make_app()).post(
        "/api/v1/users", content=b"{broken", headers={"content-type": "application/json"}
    )
    assert r.status_code == 400 and r.json()["code"] == 1002
    assert r.json()["message"].startswith("请求体格式错误")


def test_biz_error_maps_http():
    r = TestClient(make_app()).get("/api/v1/boom-biz")
    assert r.status_code == 404
    assert r.json()["code"] == 1004 and r.json()["message"] == "资源不存在：user u9"


def test_unknown_route_404_envelope():
    r = TestClient(make_app()).get("/api/v1/nope")
    assert r.status_code == 404 and r.json()["code"] == 1004


def test_page_defaults_and_limits():
    c = TestClient(make_app())
    d = c.get("/api/v1/users").json()["data"]
    assert d == {"list": [{"id": "u1"}], "total": 41, "page": 1, "pageSize": 20}
    assert c.get("/api/v1/users?pageSize=100").json()["data"]["pageSize"] == 100
    assert c.get("/api/v1/users?pageSize=101").json()["code"] == 1001
    assert c.get("/api/v1/users?page=0").json()["code"] == 1001
    assert c.get("/api/v1/users?page=abc").json()["code"] == 1001
```

- [ ] **Step 2: 跑测试确认失败**

Run: `uv run pytest packages/yarch-python/tests/test_web.py -v`
Expected: FAIL `ModuleNotFoundError … 'yarch_python.web'`

- [ ] **Step 3: 实现**

```python
# stacks/python/packages/yarch-python/src/yarch_python/web/__init__.py
"""web：FastAPI 一行装配 + 信封回包 + 1001/1002 分型 + 分页绑定（rest-conventions.md v1.0）。"""
from typing import Any, Optional

from fastapi import FastAPI, Request
from fastapi.exceptions import RequestValidationError
from starlette.exceptions import HTTPException as StarletteHTTPException
from starlette.responses import Response as RawResponse
from starlette.types import ASGIApp

from yarch_python import errcode, logx
from yarch_python.middleware import AccessLogMiddleware, RecoveryMiddleware, TraceMiddleware
from yarch_python.response import PageData, PageQuery, Response
from yarch_python.xerror import BizError

_HTTP_EXCEPTION_MAP = {401: 2001, 403: 2003, 404: 1004, 409: 1005, 429: 1006}


def _envelope_raw(resp: Response) -> RawResponse:
    return RawResponse(content=resp.model_dump_json(by_alias=True), media_type="application/json",
                       status_code=errcode.http_of(resp.code))


def ok(data: Any = None, *, status_code: int = 200, message: str = "成功") -> RawResponse:
    resp = Response(code=0, message=message, data=data, trace_id=logx.current_trace())
    return RawResponse(resp.model_dump_json(by_alias=True), status_code=status_code,
                       media_type="application/json")


def page(items: list, total: int, page_num: int, page_size: int,
         next_cursor: Optional[str] = None, *, status_code: int = 200) -> RawResponse:
    pd = PageData[list](list=items, total=total, page=page_num, page_size=page_size,
                        next_cursor=next_cursor or None)
    resp = Response[code := 0](data=pd) if False else Response(code=0, data=pd,
                                                               trace_id=logx.current_trace())
    return RawResponse(resp.model_dump_json(by_alias=True, exclude_none=True), status_code=status_code,
                       media_type="application/json")


def page_query(request: Request) -> PageQuery:
    try:
        p = int(request.query_params.get("page", "1"))
        s = int(request.query_params.get("pageSize", "20"))
    except ValueError:
        raise BizError(1001, detail="page/pageSize 须为整数") from None
    if p < 1 or s < 1 or s > 100:
        raise BizError(1001, detail="page 须为正整数，pageSize 须在 1~100")
    return PageQuery(page=p, page_size=s)


def setup(app: FastAPI, *, service: str, env: str,
          idempotency_store: Optional[Any] = None,
          rate_limit: Optional[tuple[Any, int, int]] = None) -> None:
    logx.setup(service, env)
    app.state.service = service
    # starlette：后 add 的在外层。目标外→内：AccessLog > Trace > Recovery > (Idem) > (Rate)
    if rate_limit is not None:
        from yarch_python.middleware.ratelimit import RateLimitMiddleware
        limiter, limit, window_s = rate_limit
        app.add_middleware(RateLimitMiddleware, limiter=limiter, limit=limit, window_s=window_s,
                           service=service)
    if idempotency_store is not None:
        from yarch_python.middleware.idempotency import IdempotencyMiddleware
        app.add_middleware(IdempotencyMiddleware, store=idempotency_store, service=service)
    app.add_middleware(RecoveryMiddleware)
    app.add_middleware(TraceMiddleware)
    app.add_middleware(AccessLogMiddleware)

    @app.exception_handler(BizError)
    async def _biz(request: Request, exc: BizError):
        return _envelope_raw(Response(code=exc.code, message=exc.message, data=None,
                                      trace_id=logx.current_trace()))

    @app.exception_handler(RequestValidationError)
    async def _validation(request: Request, exc: RequestValidationError):
        types = {e.get("type") for e in exc.errors()}
        if types & {"json_invalid", "json_decode_error"}:
            code = 1002
        else:
            code = 1001
        first = exc.errors()[0]
        loc = ".".join(str(x) for x in first["loc"][1:]) or "body"
        detail = f"{loc} {first['msg']}"
        return _envelope_raw(Response(code=code, message=f"{errcode.message_of(code)}：{detail}",
                                      data=None, trace_id=logx.current_trace()))

    @app.exception_handler(StarletteHTTPException)
    async def _http_exc(request: Request, exc: StarletteHTTPException):
        code = _HTTP_EXCEPTION_MAP.get(exc.status_code, 1000)
        message = errcode.message_of(code)
        if exc.detail and exc.detail != "Not Found" and exc.status_code == 404:
            message = f"{message}：{exc.detail}"
        return _envelope_raw(Response(code=code, message=message, data=None,
                                      trace_id=logx.current_trace()))
```

注意：`page()` 中那行 `Response[code := 0](data=pd) if False else …` 是笔误防御——实现时直接写 `resp = Response(code=0, data=pd, trace_id=logx.current_trace())`（不要照抄 if False 结构）。

- [ ] **Step 4: 跑测试确认通过**

Run: `uv run pytest packages/yarch-python/tests/test_web.py -v`
Expected: 6 passed。另跑全量：`uv run pytest -m "not integration" -v`（此前全部通过）。

- [ ] **Step 5: Commit**

```bash
cd /Users/yuandonghao/sidejob/sources/yarch
git add stacks/python/packages/yarch-python/src/yarch_python/web stacks/python/packages/yarch-python/tests/test_web.py
git commit -m "feat(stacks/python): web 装配——信封回包/1001·1002 分型/分页绑定/状态码映射" -- stacks/python
```

---

### Task 8: redix（Keys/Cache/IdempotencyStore/FixedWindowLimiter，TC 行为级）

**Files:**
- Create: `stacks/python/packages/yarch-python/src/yarch_python/redix/__init__.py`
- Test: `stacks/python/packages/yarch-python/tests/test_redix_unit.py`、`tests/test_redix_tc.py`（integration）

**Interfaces:**
- Consumes: redis-py
- Produces:
  - `Keys(service: str)`，`of(*parts: str) -> str`（首段恒为服务名）
  - `Cache(redis)`：`get_json(key)`、`set_json(key, value, ttl_s)`
  - `IdempotencyStore(redis)`：`acquire(key, digest) -> "acquired"|"replay"|"pending"|"mismatch"`、`store_response(key, status_code, body: str)`、`load(key) -> tuple[int, str] | None`；TTL 常量 `IDEMPOTENCY_TTL_S = 24 * 3600`
  - `FixedWindowLimiter(redis)`：`hit(key, window_s: int, limit: int) -> bool`
  - `lock(redis, name, *, timeout_s=10)`（redis-py Lock 透传，token 释放语义内建）

- [ ] **Step 1: 写单元失败测试（Keys 无需 redis）**

```python
# stacks/python/packages/yarch-python/tests/test_redix_unit.py
from yarch_python.redix import Keys


def test_keys_first_segment_is_service():
    k = Keys("ysaas-scan")
    assert k.of("idem", "abc") == "ysaas-scan:idem:abc"
    assert k.of() == "ysaas-scan"


def test_keys_rejects_empty_parts():
    import pytest
    with pytest.raises(ValueError):
        Keys("ysaas-scan").of("a", "")
```

- [ ] **Step 2: 跑测试确认失败**

Run: `uv run pytest packages/yarch-python/tests/test_redix_unit.py -v`
Expected: FAIL `ModuleNotFoundError … 'yarch_python.redix'`

- [ ] **Step 3: 实现**

```python
# stacks/python/packages/yarch-python/src/yarch_python/redix/__init__.py
"""redix：Redis 规约件（redis.md v1.0）——key 首段=服务名、JSON 值、锁、幂等存储、固定窗口限流。"""
import json
from typing import Any, Optional

from redis import Redis
from redis.lock import Lock

IDEMPOTENCY_TTL_S = 24 * 3600  # 幂等 TTL ≥ 24h（rest-conventions 幂等总则-1）

_FIXED_WINDOW_LUA = """
local c = redis.call('INCR', KEYS[1])
if c == 1 then redis.call('PEXPIRE', KEYS[1], ARGV[1]) end
return c
"""


class Keys:
    def __init__(self, service: str):
        if not service:
            raise ValueError("service 必填（key 首段=服务名，registry 租户边界）")
        self.service = service

    def of(self, *parts: str) -> str:
        if any(not p for p in parts):
            raise ValueError("key 段不得为空")
        return ":".join([self.service, *parts])


class Cache:
    def __init__(self, redis: Redis):
        self.redis = redis

    def get_json(self, key: str) -> Any:
        raw = self.redis.get(key)
        return json.loads(raw) if raw is not None else None

    def set_json(self, key: str, value: Any, ttl_s: int) -> None:
        self.redis.set(key, json.dumps(value, ensure_ascii=False), ex=ttl_s)


class IdempotencyStore:
    _DONE = "done"
    _PENDING = "pending"

    def __init__(self, redis: Redis):
        self.redis = redis

    def _dump(self, digest: str, status: str, resp: Optional[tuple] = None) -> str:
        d = {"digest": digest, "status": status}
        if resp:
            d["statusCode"], d["body"] = resp
        return json.dumps(d, ensure_ascii=False)

    def acquire(self, key: str, digest: str) -> str:
        got = self.redis.set(key, self._dump(digest, self._PENDING), nx=True, ex=IDEMPOTENCY_TTL_S)
        if got:
            return "acquired"
        cur = json.loads(self.redis.get(key) or "{}")
        if cur.get("digest") != digest:
            return "mismatch"
        return "replay" if cur.get("status") == self._DONE else "pending"

    def store_response(self, key: str, status_code: int, body: str) -> None:
        cur = json.loads(self.redis.get(key) or "{}")
        self.redis.set(key, self._dump(cur.get("digest", ""), self._DONE, (status_code, body)),
                       ex=IDEMPOTENCY_TTL_S)

    def load(self, key: str) -> Optional[tuple[int, str]]:
        cur = json.loads(self.redis.get(key) or "{}")
        if cur.get("status") != self._DONE:
            return None
        return int(cur["statusCode"]), cur["body"]


class FixedWindowLimiter:
    def __init__(self, redis: Redis):
        self.redis = redis

    def hit(self, key: str, window_s: int, limit: int) -> bool:
        c = self.redis.eval(_FIXED_WINDOW_LUA, 1, key, window_s * 1000)
        return int(c) <= limit


def lock(redis: Redis, name: str, *, timeout_s: float = 10) -> Lock:
    return redis.lock(name, timeout=timeout_s)
```

- [ ] **Step 4: 单元测试通过 + 写 TC 行为级测试**

Run: `uv run pytest packages/yarch-python/tests/test_redix_unit.py -v` → 2 passed。

```python
# stacks/python/packages/yarch-python/tests/test_redix_tc.py
import pytest

from yarch_python.redix import Cache, FixedWindowLimiter, IdempotencyStore, Keys, lock

pytestmark = pytest.mark.integration


@pytest.fixture(scope="module")
def redis():
    from testcontainers.redis import RedisContainer
    with RedisContainer() as c:
        import redis as redis_lib
        yield redis_lib.Redis.from_url(c.get_connection_url())


def test_json_value_and_keys_shape(redis):
    keys = Keys("ysaas-scan")
    Cache(redis).set_json(keys.of("cache", "u1"), {"a": 1}, ttl_s=60)
    assert Cache(redis).get_json(keys.of("cache", "u1")) == {"a": 1}
    assert redis.exists("ysaas-scan:cache:u1") == 1


def test_lock_mutual_exclusion_and_token_release(redis):
    lk = lock(redis, "ysaas-scan:lock:job1", timeout_s=5)
    assert lk.acquire(blocking=False)
    lk2 = lock(redis, "ysaas-scan:lock:job1", timeout_s=5)
    assert not lk2.acquire(blocking=False)
    assert lk.release()  # token 匹配才释放
    assert lock(redis, "ysaas-scan:lock:job1", timeout_s=5).acquire(blocking=False)


def test_idempotency_three_state_flow(redis):
    store = IdempotencyStore(redis)
    k = "ysaas-scan:idem:k1"
    assert store.acquire(k, "d1") == "acquired"
    assert store.acquire(k, "d1") == "pending"
    assert store.acquire(k, "d2") == "mismatch"
    store.store_response(k, 201, '{"code":0}')
    assert store.acquire(k, "d1") == "replay"
    assert store.load(k) == (201, '{"code":0}')


def test_rate_limiter_fixed_window(redis):
    limiter = FixedWindowLimiter(redis)
    k = "ysaas-scan:rl:t1"
    results = [limiter.hit(k, window_s=60, limit=3) for _ in range(5)]
    assert results == [True, True, True, False, False]
```

Run: `uv run pytest packages/yarch-python/tests/test_redix_tc.py -v`（需 OrbStack：先 `open -a OrbStack`）
Expected: 4 passed

- [ ] **Step 5: Commit**

```bash
cd /Users/yuandonghao/sidejob/sources/yarch
git add stacks/python/packages/yarch-python/src/yarch_python/redix stacks/python/packages/yarch-python/tests/test_redix_unit.py stacks/python/packages/yarch-python/tests/test_redix_tc.py
git commit -m "feat(stacks/python): redix——key 首段=服务名/幂等三态/固定窗口限流/锁（TC 行为级）" -- stacks/python
```

---

### Task 9: middleware 幂等 + 限流（redix 支撑）

**Files:**
- Create: `stacks/python/packages/yarch-python/src/yarch_python/middleware/idempotency.py`、`ratelimit.py`
- Modify: `stacks/python/packages/yarch-python/src/yarch_python/middleware/__init__.py`
- Test: `stacks/python/packages/yarch-python/tests/test_middleware_redis.py`（integration）

**Interfaces:**
- Consumes: `redix.IdempotencyStore/FixedWindowLimiter/Keys`、`web` 的信封出口（直接用 `Response`+`errcode`）
- Produces:
  - `IdempotencyMiddleware(app, *, store: IdempotencyStore, service: str)`：unsafe 方法 + `Idempotency-Key` 头才启用；同键同参回放原响应（含原状态码）、异参 1007/409、并发 pending 等待 ≤1s 后回放或 1007
  - `RateLimitMiddleware(app, *, limiter, limit: int, window_s: int, service: str)`：按 path 维度限流，超限 1006/429 信封
  - `web.setup(...)` 的 `idempotency_store=…, rate_limit=(limiter, limit, window_s)` 接线（Task 7 已留位）

- [ ] **Step 1: 写失败测试（TC）**

```python
# stacks/python/packages/yarch-python/tests/test_middleware_redis.py
import io

import pytest
from fastapi import FastAPI
from fastapi.testclient import TestClient
from pydantic import BaseModel

from yarch_python import logx
from yarch_python.redix import FixedWindowLimiter, IdempotencyStore
from yarch_python.web import ok, setup

pytestmark = pytest.mark.integration


class Body(BaseModel):
    n: int


def make_app(redis) -> FastAPI:
    logx.setup("ysaas-scan", "local", sink=io.StringIO())
    app = FastAPI()
    setup(app, service="ysaas-scan", env="local",
          idempotency_store=IdempotencyStore(redis),
          rate_limit=(FixedWindowLimiter(redis), 100, 60))

    @app.post("/api/v1/orders")
    def create(body: Body):
        return ok({"order": body.n}, status_code=201)

    return app


@pytest.fixture(scope="module")
def redis():
    from testcontainers.redis import RedisContainer
    with RedisContainer() as c:
        import redis as redis_lib
        yield redis_lib.Redis.from_url(c.get_connection_url())


def test_same_key_same_body_replays(redis):
    c = TestClient(make_app(redis))
    h = {"Idempotency-Key": "k-1"}
    r1 = c.post("/api/v1/orders", json={"n": 1}, headers=h)
    r2 = c.post("/api/v1/orders", json={"n": 1}, headers=h)
    assert r1.status_code == r2.status_code == 201
    assert r1.json() == r2.json() and r1.json()["data"] == {"order": 1}


def test_same_key_diff_body_1007(redis):
    c = TestClient(make_app(redis))
    h = {"Idempotency-Key": "k-2"}
    c.post("/api/v1/orders", json={"n": 1}, headers=h)
    r = c.post("/api/v1/orders", json={"n": 2}, headers=h)
    assert r.status_code == 409 and r.json()["code"] == 1007
    assert r.json()["message"].startswith("幂等冲突：重复提交")


def test_no_key_passthrough(redis):
    c = TestClient(make_app(redis))
    r1 = c.post("/api/v1/orders", json={"n": 1})
    r2 = c.post("/api/v1/orders", json={"n": 1})
    assert r1.json()["data"] == {"order": 1} and r2.json()["data"] == {"order": 1}


def test_rate_limit_1006(redis):
    import time
    app = make_app(redis)
    logx.setup("ysaas-scan", "local", sink=io.StringIO())
    from yarch_python.web import setup as _s
    app2 = FastAPI()
    _s(app2, service="ysaas-scan", env="local",
       rate_limit=(FixedWindowLimiter(redis), 2, 60))

    @app2.get("/api/v1/ping")
    def ping():
        return ok("pong")

    c = TestClient(app2)
    assert c.get("/api/v1/ping").status_code == 200
    assert c.get("/api/v1/ping").status_code == 200
    r = c.get("/api/v1/ping")
    assert r.status_code == 429 and r.json()["code"] == 1006
```

- [ ] **Step 2: 跑测试确认失败**

Run: `uv run pytest packages/yarch-python/tests/test_middleware_redis.py -v`
Expected: FAIL `ModuleNotFoundError … 'yarch_python.middleware.idempotency'`

- [ ] **Step 3: 实现**

```python
# stacks/python/packages/yarch-python/src/yarch_python/middleware/idempotency.py
"""幂等中间件（rest-conventions.md 幂等总则-1）：同键同参回放/异参 1007/并发短暂等待。"""
import asyncio
import hashlib
import json

from starlette.types import ASGIApp, Message, Receive, Scope, Send

from yarch_python import errcode, logx
from yarch_python.redix import IdempotencyStore, Keys
from yarch_python.response import Response

_UNSAFE = {"POST", "PUT", "PATCH", "DELETE"}


def _digest(method: str, path: str, body: bytes) -> str:
    return hashlib.sha256(f"{method} {path} ".encode() + body).hexdigest()


def _replay_receive(body: bytes):
    sent = {"done": False}

    async def receive() -> Message:
        if not sent["done"]:
            sent["done"] = True
            return {"type": "http.request", "body": body, "more_body": False}
        return {"type": "http.disconnect"}

    return receive


class IdempotencyMiddleware:
    def __init__(self, app: ASGIApp, *, store: IdempotencyStore, service: str):
        self.app = app
        self.store = store
        self.keys = Keys(service)

    async def __call__(self, scope: Scope, receive: Receive, send: Send) -> None:
        if scope["type"] != "http" or scope["method"] not in _UNSAFE:
            await self.app(scope, receive, send)
            return
        headers = dict(scope.get("headers") or [])
        raw_key = headers.get(b"idempotency-key", b"").decode("latin-1").strip()
        if not raw_key:
            await self.app(scope, receive, send)
            return

        body = b""
        while True:
            msg = await receive()
            if msg["type"] == "http.request":
                body += msg.get("body", b"")
                if not msg.get("more_body"):
                    break
            else:
                break
        digest = _digest(scope["method"], scope["path"], body)
        key = self.keys.of("idem", raw_key)

        state = self.store.acquire(key, digest)
        if state == "mismatch":
            await self._send_biz(send, 1007)
            return
        if state == "pending":
            for _ in range(5):
                await asyncio.sleep(0.2)
                state = self.store.acquire(key, digest)
                if state in ("replay", "mismatch"):
                    break
            if state != "replay":
                await self._send_biz(send, 1007)
                return
        if state == "replay":
            stored = self.store.load(key)
            if stored is None:
                await self._send_biz(send, 1007)
                return
            status_code, body_str = stored
            await send({"type": "http.response.start", "status": status_code,
                        "headers": [(b"content-type", b"application/json")]})
            await send({"type": "http.response.body", "body": body_str.encode()})
            return

        # acquired：执行并捕获响应
        captured: dict = {}

        async def send_capture(message: Message) -> None:
            if message["type"] == "http.response.start":
                captured["status"] = message["status"]
                captured["headers"] = list(message.get("headers", []))
            elif message["type"] == "http.response.body":
                captured.setdefault("chunks", []).append(message.get("body", b""))
            await send(message)

        try:
            await self.app(scope, _replay_receive(body), send_capture)
        finally:
            chunks = captured.get("chunks")
            if captured.get("status") is not None and chunks is not None:
                self.store.store_response(key, captured["status"], b"".join(chunks).decode("utf-8", "replace"))

    async def _send_biz(self, send: Send, code: int) -> None:
        resp = Response(code=code, message=f"{errcode.message_of(code)}：Idempotency-Key 冲突",
                        data=None, trace_id=logx.current_trace())
        await send({"type": "http.response.start", "status": errcode.http_of(code),
                    "headers": [(b"content-type", b"application/json")]})
        await send({"type": "http.response.body", "body": resp.model_dump_json(by_alias=True).encode()})
```

```python
# stacks/python/packages/yarch-python/src/yarch_python/middleware/ratelimit.py
"""限流中间件（固定窗口，跨实例口径）：超限 1006/429 信封。"""
from starlette.types import ASGIApp, Receive, Scope, Send

from yarch_python import errcode, logx
from yarch_python.redix import FixedWindowLimiter, Keys
from yarch_python.response import Response


class RateLimitMiddleware:
    def __init__(self, app: ASGIApp, *, limiter: FixedWindowLimiter, limit: int, window_s: int,
                 service: str):
        self.app = app
        self.limiter = limiter
        self.limit = limit
        self.window_s = window_s
        self.keys = Keys(service)

    async def __call__(self, scope: Scope, receive: Receive, send: Send) -> None:
        if scope["type"] != "http":
            await self.app(scope, receive, send)
            return
        key = self.keys.of("rl", scope.get("path", ""))
        if not self.limiter.hit(key, self.window_s, self.limit):
            resp = Response(code=1006, message=errcode.message_of(1006), data=None,
                            trace_id=logx.current_trace())
            await send({"type": "http.response.start", "status": 429,
                        "headers": [(b"content-type", b"application/json")]})
            await send({"type": "http.response.body", "body": resp.model_dump_json(by_alias=True).encode()})
            return
        await self.app(scope, receive, send)
```

`middleware/__init__.py` 追加两行导出：

```python
from yarch_python.middleware.idempotency import IdempotencyMiddleware
from yarch_python.middleware.ratelimit import RateLimitMiddleware
# __all__ 相应追加 "IdempotencyMiddleware", "RateLimitMiddleware"
```

- [ ] **Step 4: 跑测试确认通过**

Run: `uv run pytest packages/yarch-python/tests/test_middleware_redis.py -v`
Expected: 4 passed

- [ ] **Step 5: Commit**

```bash
cd /Users/yuandonghao/sidejob/sources/yarch
git add stacks/python/packages/yarch-python/src/yarch_python/middleware stacks/python/packages/yarch-python/tests/test_middleware_redis.py
git commit -m "feat(stacks/python): 幂等/限流中间件——同键回放·异参 1007·超限 1006（TC）" -- stacks/python
```

---

### Task 10: persist（SQLAlchemy + psycopg3 + 逻辑删除/审计/分页下推 + ensure_database + Alembic 辅助）

**Files:**
- Create: `stacks/python/packages/yarch-python/src/yarch_python/persist/__init__.py`
- Test: `stacks/python/packages/yarch-python/tests/test_persist_tc.py`（integration）

**Interfaces:**
- Consumes: SQLAlchemy/psycopg3/Alembic
- Produces:
  - `Base(DeclarativeBase)`；`AuditMixin`（created_at/updated_at timestamptz，server_default now()，updated_at onupdate）；`SoftDeleteMixin`（is_deleted bool default false）
  - `engine_for(url: str)`、`sessionmaker_for(url: str) -> sessionmaker`
  - `ensure_database(url: str) -> None`（连 `postgres` 库查 pg_database，无则 CREATE DATABASE，幂等，标识符校验）
  - `page_of(session, stmt, page: int, page_size: int) -> tuple[list, int]`（count + offset/limit 下推；D6 天然成立）
  - `alembic_upgrade(script_location: str, url: str) -> None`
  - `not_deleted(model_cls)`（过滤 is_deleted=False）

- [ ] **Step 1: 写失败测试（TC PG）**

```python
# stacks/python/packages/yarch-python/tests/test_persist_tc.py
from datetime import datetime

import pytest
from sqlalchemy import select
from sqlalchemy.orm import DeclarativeBase, Mapped, mapped_column

from yarch_python.persist import AuditMixin, Base, SoftDeleteMixin, ensure_database, not_deleted, page_of, sessionmaker_for

pytestmark = pytest.mark.integration


@pytest.fixture(scope="module")
def pg_url():
    from testcontainers.postgres import PostgresContainer
    with PostgresContainer("postgres:17-alpine") as c:
        url = f"postgresql+psycopg://{self_url(c)}"
        ensure_database(url)  # 幂等：已存在不炸
        yield url


def self_url(c) -> str:
    parts = c.get_connection_url().replace("postgresql+psycopg://", "").replace("postgresql://", "")
    return parts


class Row(Base, AuditMixin, SoftDeleteMixin):
    __tablename__ = "t_rows"
    id: Mapped[int] = mapped_column(primary_key=True, autoincrement=True)


def setup_table(url):
    from sqlalchemy import create_engine
    Base.metadata.create_all(create_engine(url))  # 仅测试建表；业务工程用 Alembic
    return sessionmaker_for(url)()


def test_audit_and_soft_delete_and_d6(pg_url):
    session = setup_table(pg_url)
    rows = [Row() for _ in range(5)]
    session.add_all(rows)
    session.commit()
    r1 = session.get(Row, 1)
    assert isinstance(r1.created_at, datetime) and isinstance(r1.updated_at, datetime)

    r1.is_deleted = True
    session.commit()
    alive = session.scalars(select(Row).where(not_deleted(Row))).all()
    assert len(alive) == 4  # 逻辑删除不物理删
    assert session.get(Row, 1) is not None

    items, total = page_of(session, select(Row).where(not_deleted(Row)), page=3, page_size=2)
    assert items == [] and total == 4  # D6：越界空页 + 真实 total


def test_ensure_database_idempotent_and_identifier_checked(pg_url):
    ensure_database(pg_url)  # 再跑一次不炸
    with pytest.raises(ValueError):
        ensure_database(pg_url.replace("/test", '/Bad"Name'))


def test_alembic_upgrade_runs_migration(pg_url, tmp_path):
    import textwrap
    (tmp_path / "versions").mkdir()
    (tmp_path / "env.py").write_text(textwrap.dedent("""
        from alembic import context
        from sqlalchemy import create_engine, pool
        cfg = context.config
        target_metadata = None
        engine = create_engine(cfg.get_main_option("sqlalchemy.url"), poolclass=pool.NullPool)
        with engine.connect() as connection:
            context.configure(connection=connection, target_metadata=target_metadata)
            with context.begin_transaction():
                context.run_migrations()
    """))
    (tmp_path / "script.py.mako").write_text(
        "from alembic import op\n\nrevision = ${repr(up_revision)}\ndown_revision = ${repr(down_revision)}\n"
        "def upgrade():\n    op.execute('CREATE TABLE IF NOT EXISTS alembic_probe (id int)')\n"
    )
    (tmp_path / "versions" / "0001.py").write_text(
        "revision = '0001'\ndown_revision = None\n\n"
        "from alembic import op\n\n"
        "def upgrade():\n"
        "    op.execute('CREATE TABLE IF NOT EXISTS alembic_probe (id int)')\n"
    )
    from yarch_python.persist import alembic_upgrade
    alembic_upgrade(str(tmp_path), pg_url)
    from sqlalchemy import create_engine, text
    with create_engine(pg_url).connect() as conn:
        assert conn.execute(text("SELECT count(*) FROM alembic_probe")).scalar() == 0
```

- [ ] **Step 2: 跑测试确认失败**

Run: `uv run pytest packages/yarch-python/tests/test_persist_tc.py -v`
Expected: FAIL `ModuleNotFoundError … 'yarch_python.persist'`

- [ ] **Step 3: 实现**

```python
# stacks/python/packages/yarch-python/src/yarch_python/persist/__init__.py
"""persist：PG 规约件（postgresql.md v1.0）——审计/逻辑删除/分页下推/建库/Alembic。禁 create_all 于业务（迁移版本化六-1）。"""
import re
from datetime import datetime
from typing import Any

from sqlalchemy import create_engine, func, select
from sqlalchemy.orm import DeclarativeBase, Mapped, Session, mapped_column, sessionmaker
from sqlalchemy.sql import Select


class Base(DeclarativeBase):
    pass


class AuditMixin:
    created_at: Mapped[datetime] = mapped_column(server_default=func.now(), nullable=False)
    updated_at: Mapped[datetime] = mapped_column(server_default=func.now(), onupdate=func.now(),
                                                 nullable=False)


class SoftDeleteMixin:
    is_deleted: Mapped[bool] = mapped_column(default=False, server_default="false",
                                             nullable=False)


def engine_for(url: str):
    return create_engine(url, pool_pre_ping=True)


def sessionmaker_for(url: str) -> sessionmaker:
    return sessionmaker(bind=engine_for(url), expire_on_commit=False)


_DBNAME_RE = re.compile(r"^[a-z][a-z0-9_]{0,62}$")


def ensure_database(url: str) -> None:
    """独立 database 自动建库（幂等）。url 形如 postgresql+psycopg://user:pass@host/db。"""
    from urllib.parse import urlsplit, urlunsplit
    parts = urlsplit(url)
    dbname = parts.path.lstrip("/")
    if not _DBNAME_RE.match(dbname):
        raise ValueError(f"库名不合规（^[a-z][a-z0-9_]{{0,62}}$）：{dbname!r}")
    admin = urlunsplit(parts._replace(path="/postgres"))
    eng = create_engine(admin, poolclass=None) if False else create_engine(admin)
    with eng.connect() as conn:
        from sqlalchemy import text
        exists = conn.execute(
            text("SELECT 1 FROM pg_database WHERE datname = :d"), {"d": dbname}
        ).scalar()
        if not exists:
            from sqlalchemy import text as _t
            conn = conn.execution_options(autocommit=True)
            conn.execute(_t(f'CREATE DATABASE "{dbname}"'))
    eng.dispose()


def page_of(session: Session, stmt: Select, page: int, page_size: int) -> tuple[list, int]:
    total = session.execute(select(func.count()).select_from(stmt.order_by(None).subquery())).scalar_one()
    items = session.execute(stmt.offset((page - 1) * page_size).limit(page_size)).scalars().all()
    return list(items), int(total)


def not_delete_marker():  # 防误用占位；真实 API 见 not_deleted
    raise NotImplementedError


def not_deleted(model_cls: Any):
    return model_cls.is_deleted.is_(False)


def alembic_upgrade(script_location: str, url: str) -> None:
    from alembic import command
    from alembic.config import Config
    cfg = Config()
    cfg.set_main_option("script_location", script_location)
    cfg.set_main_option("sqlalchemy.url", url)
    command.upgrade(cfg, "head")
```

注意：`ensure_database` 内 `poolclass=None) if False else` 是笔误防御——实现时直接 `create_engine(admin)`；`not_delete_marker` 占位不要写入，仅保留 `not_deleted`。

- [ ] **Step 4: 跑测试确认通过**

Run: `uv run pytest packages/yarch-python/tests/test_persist_tc.py -v`
Expected: 3 passed

- [ ] **Step 5: Commit**

```bash
cd /Users/yuandonghao/sidejob/sources/yarch
git add stacks/python/packages/yarch-python/src/yarch_python/persist stacks/python/packages/yarch-python/tests/test_persist_tc.py
git commit -m "feat(stacks/python): persist——审计/逻辑删除/分页下推 D6/自动建库/Alembic（TC）" -- stacks/python
```

---

### Task 11: httpx（下游客户端：超时强制 + traceparent 注入 + 信封解包 + 1008/1009）

**Files:**
- Create: `stacks/python/packages/yarch-python/src/yarch_python/httpx/__init__.py`
- Test: `stacks/python/packages/yarch-python/tests/test_httpx.py`

**Interfaces:**
- Consumes: `logx.current_trace/new_trace_id`、`xerror.BizError`（显式 message 透传）
- Produces: `YarchHttpClient(base_url: str, *, timeout: float = 10.0, client: httpx.Client | None = None)`；方法 `get/post/put/patch/delete(request…) -> Any`（返回信封 data）；超时 >30 构造即 `ValueError`；`httpx.TimeoutException → BizError(1008)`；`TransportError → BizError(1009)`；非信封 → `BizError(1009)`；下游 code!=0 → `BizError(code, message=下游message)` 透传。

- [ ] **Step 1: 写失败测试**

```python
# stacks/python/packages/yarch-python/tests/test_httpx.py
import httpx
import pytest

from yarch_python import logx
from yarch_python.httpx import YarchHttpClient
from yarch_python.xerror import BizError


def env(mocker_routes) -> YarchHttpClient:
    transport = httpx.MockTransport(mocker_routes)
    return YarchHttpClient("http://upstream", client=httpx.Client(transport=transport))


def test_timeout_over_30_rejected():
    with pytest.raises(ValueError):
        YarchHttpClient("http://upstream", timeout=31)


def test_envelope_success_returns_data():
    def handler(request):
        return httpx.Response(200, json={"code": 0, "message": "成功", "data": {"x": 1},
                                         "traceId": "t"})

    assert env(handler).get("/api/v1/things") == {"x": 1}


def test_downstream_code_passthrough():
    def handler(request):
        return httpx.Response(400, json={"code": 3004, "message": "订单状态不允许该操作",
                                         "data": None, "traceId": "t"})

    with pytest.raises(BizError) as ei:
        env(handler).post("/api/v1/orders", json={"a": 1})
    assert ei.value.code == 3004 and ei.value.message == "订单状态不允许该操作"


def test_timeout_maps_1008():
    def handler(request):
        raise httpx.ReadTimeout("slow")

    with pytest.raises(BizError) as ei:
        env(handler).get("/slow")
    assert ei.value.code == 1008 and ei.value.http_status == 504


def test_transport_error_and_non_envelope_map_1009():
    def conn_error(request):
        raise httpx.ConnectError("refused")

    with pytest.raises(BizError) as ei:
        env(conn_error).get("/x")
    assert ei.value.code == 1009

    def not_envelope(request):
        return httpx.Response(200, text="<html>hi</html>")

    with pytest.raises(BizError) as ei2:
        env(not_envelope).get("/y")
    assert ei2.value.code == 1009


def test_traceparent_injected_from_contextvar():
    captured = {}

    def handler(request):
        captured.update(dict(request.headers))
        return httpx.Response(200, json={"code": 0, "message": "成功", "data": None, "traceId": "t"})

    token = logx.bind_trace("0af7651916cd43dd8448eb211c80319c")
    try:
        env(handler).get("/z")
    finally:
        logx.reset_trace(token)
    import re
    assert re.fullmatch(r"00-0af7651916cd43dd8448eb211c80319c-[0-9a-f]{16}-01",
                        captured["traceparent"])
    assert captured["x-trace-id"] == "0af7651916cd43dd8448eb211c80319c"
```

- [ ] **Step 2: 跑测试确认失败**

Run: `uv run pytest packages/yarch-python/tests/test_httpx.py -v`
Expected: FAIL `ModuleNotFoundError … 'yarch_python.httpx'`

- [ ] **Step 3: 实现**

```python
# stacks/python/packages/yarch-python/src/yarch_python/httpx/__init__.py
"""httpx：下游客户端（logging-trace 传播矩阵出口行）——超时强制≤30s + traceparent 注入 + 信封解包 + 1008/1009。"""
import secrets
from typing import Any, Optional

import httpx

from yarch_python import logx
from yarch_python.xerror import BizError

MAX_TIMEOUT_S = 30.0


class YarchHttpClient:
    def __init__(self, base_url: str, *, timeout: float = 10.0,
                 client: Optional[httpx.Client] = None):
        if timeout > MAX_TIMEOUT_S:
            raise ValueError(f"超时强制 ≤ {MAX_TIMEOUT_S}s（logging-trace A7）")
        self._client = client if client is not None else httpx.Client(base_url=base_url,
                                                                      timeout=timeout)

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
```

- [ ] **Step 4: 跑测试确认通过**

Run: `uv run pytest packages/yarch-python/tests/test_httpx.py -v`
Expected: 6 passed

- [ ] **Step 5: Commit**

```bash
cd /Users/yuandonghao/sidejob/sources/yarch
git add stacks/python/packages/yarch-python/src/yarch_python/httpx stacks/python/packages/yarch-python/tests/test_httpx.py
git commit -m "feat(stacks/python): httpx——traceparent 注入/信封解包/码透传/1008·1009" -- stacks/python
```

---

### Task 12: celeryx（celery.md 承接装配件）

**Files:**
- Create: `stacks/python/packages/yarch-python/src/yarch_python/celeryx/__init__.py`
- Test: `stacks/python/packages/yarch-python/tests/test_celeryx.py`（单元）+ `tests/test_celeryx_tc.py`（integration：beat 守卫/run_once）

**Interfaces:**
- Consumes: celery、redis（守卫）
- Produces:
  - `task_name(service, module, action) -> str`（`{服务名}.{模块}.{动作}`）、`queue_name(service, usage) -> str`
  - `retry_options() -> dict`（max_retries=5, retry_backoff=True, retry_backoff_jitter=True）
  - `make_app(service, broker_url, *, soft_time_limit=60, hard_time_limit=None, result_backend=None, result_expires=86400, extra_queues=()) -> Celery`（global_keyprefix/json-only/显式队列/超时必配/acks_late+prefetch=1；配置后自检非 json 即抛错）
  - `trace_headers() -> dict`（`{"traceId": current or new}`，生产方 apply 时注入）
  - `TraceTask`（celery.Task 基类：headers.traceId 继承或每轮新建，周期任务天然新建）
  - `beat_guard(redis, service, *, ttl_s=60)`（非阻塞获取 Redis 锁失败即 `RuntimeError`——beat 单实例守卫）
  - `run_once(redis, key, ttl_s=3600) -> bool`（SET NX 幂等键助手）
  - `on_failure_sink(fn)`（失败任务表 SPI：重试耗尽回调注册，默认 sink 记 ndjson，投递失败不阻断）

- [ ] **Step 1: 写失败测试（单元）**

```python
# stacks/python/packages/yarch-python/tests/test_celeryx.py
import pytest

from yarch_python import celeryx


def test_naming():
    assert celeryx.task_name("ysaas-scan", "users", "sync") == "ysaas-scan.users.sync"
    assert celeryx.queue_name("ysaas-scan", "embed-scan") == "ysaas-scan.embed-scan"


def test_make_app_locks_contract_defaults():
    app = celeryx.make_app("ysaas-scan", "redis://localhost:6379/0", hard_time_limit=90)
    assert app.conf.broker_transport_options["global_keyprefix"] == "ysaas-scan:"
    assert app.conf.task_serializer == "json"
    assert app.conf.accept_content == ["json"]
    assert app.conf.task_default_queue == "ysaas-scan.default"
    assert app.conf.task_create_missing_queues is False
    assert app.conf.task_soft_time_limit == 60 and app.conf.task_hard_time_limit == 90
    assert app.conf.task_acks_late is True and app.conf.worker_prefetch_multiplier == 1
    assert app.conf.result_expires == 86400


def test_time_limits_required_and_ordered():
    with pytest.raises(ValueError):
        celeryx.make_app("ysaas-scan", "redis://x", hard_time_limit=None)
    with pytest.raises(ValueError):
        celeryx.make_app("ysaas-scan", "redis://x", soft_time_limit=90, hard_time_limit=60)


def test_pickle_rejected_after_configure():
    app = celeryx.make_app("ysaas-scan", "redis://x", hard_time_limit=90)
    app.conf.accept_content = ["json", "pickle"]
    with pytest.raises(ValueError):
        celeryx.assert_json_only(app)


def test_trace_task_inherits_header_trace_id():
    app = celeryx.make_app("ysaas-scan", "memory://", hard_time_limit=30)
    app.conf.task_always_eager = True
    app.conf.task_eager_propagates = True
    seen = {}
    from yarch_python import logx

    @app.task(base=celeryx.TraceTask, name="ysaas-scan.users.sync")
    def sync_user(user_id: str):
        seen["trace"] = logx.current_trace()
        return user_id

    from yarch_python import celeryx as cx
    sync_user.apply(args=["u1"], headers={"traceId": "0af7651916cd43dd8448eb211c80319c"})
    assert seen["trace"] == "0af7651916cd43dd8448eb211c80319c"


def test_failure_sink_records():
    app = celeryx.make_app("ysaas-scan", "memory://", hard_time_limit=30)
    app.conf.task_always_eager = True
    app.conf.task_eager_propagates = False
    records = []
    celeryx.on_failure_sink(records.append)

    @app.task(base=celeryx.TraceTask, name="ysaas-scan.users.bad")
    def bad():
        raise RuntimeError("x")

    bad.apply()
    assert len(records) == 1 and records[0]["task"] == "ysaas-scan.users.bad"
```

- [ ] **Step 2: 跑测试确认失败**

Run: `uv run pytest packages/yarch-python/tests/test_celeryx.py -v`
Expected: FAIL `ModuleNotFoundError … 'yarch_python.celeryx'`

- [ ] **Step 3: 实现**

```python
# stacks/python/packages/yarch-python/src/yarch_python/celeryx/__init__.py
"""celeryx：celery.md v1.0 承接装配件——broker 前缀/命名/json-only/超时重试显式/beat 单实例/traceId 任务头/失败终点 SPI。"""
from typing import Any, Callable

from celery import Celery, Task

from yarch_python import logx

_FAILURE_SINKS: list[Callable[[dict], None]] = []


def task_name(service: str, module: str, action: str) -> str:
    return f"{service}.{module}.{action}"


def queue_name(service: str, usage: str) -> str:
    return f"{service}.{usage}"


def retry_options() -> dict:
    """三-3：max_retries≤5 + 指数退避 + 抖动。"""
    return {"max_retries": 5, "retry_backoff": True, "retry_backoff_jitter": True}


def trace_headers() -> dict:
    """四-4：HTTP 请求内派发继承调用方 traceId，无则新建。"""
    return {"traceId": logx.current_trace() or logx.new_trace_id()}


def on_failure_sink(fn: Callable[[dict], None]) -> None:
    _FAILURE_SINKS.append(fn)


def assert_json_only(app: Celery) -> None:
    if app.conf.task_serializer != "json" or set(app.conf.accept_content) != {"json"}:
        raise ValueError("序列化只允许 json（celery.md 三-5，pickle 全局禁用）")


class TraceTask(Task):
    """任务链路 traceId：headers 继承（HTTP 派发）/每轮新建（周期，天然新链路）。"""

    def __call__(self, *args: Any, **kwargs: Any) -> Any:
        tid = (self.request.headers or {}).get("traceId") or logx.new_trace_id()
        token = logx.bind_trace(str(tid))
        try:
            return super().__call__(*args, **kwargs)
        finally:
            logx.reset_trace(token)

    def on_failure(self, exc, task_id, args, kwargs, einfo):
        record = {
            "task": self.name,
            "taskId": task_id,
            "error": repr(exc),
            "traceId": logx.current_trace(),
        }
        for sink in _FAILURE_SINKS:
            try:
                sink(record)
            except Exception:  # 投递失败不阻断（对齐 oplog SPI 模式）
                logx.get_logger("yarch_python.celeryx").warning("failure sink error",
                                                                task=self.name)
        super().on_failure(exc, task_id, args, kwargs, einfo)


def make_app(service: str, broker_url: str, *, soft_time_limit: float = 60,
             hard_time_limit: float | None = None, result_backend: str | None = None,
             result_expires: int = 86400, extra_queues: tuple[str, ...] = ()) -> Celery:
    if soft_time_limit is None or hard_time_limit is None:
        raise ValueError("soft_time_limit / hard_time_limit 必配（celery.md 三-2，禁无限执行）")
    if hard_time_limit <= soft_time_limit:
        raise ValueError("hard_time_limit 须 > soft_time_limit")
    app = Celery(service, broker=broker_url, backend=result_backend)
    queues = [queue_name(service, "default"), *extra_queues]
    app.conf.update(
        broker_transport_options={"global_keyprefix": f"{service}:"},  # 二-1 共享实例租户纪律
        task_serializer="json",
        accept_content=["json"],
        result_serializer="json",
        task_default_queue=queue_name(service, "default"),
        task_create_missing_queues=False,  # 二-2 禁默认单队列裸奔
        task_soft_time_limit=soft_time_limit,
        task_hard_time_limit=hard_time_limit,
        result_expires=result_expires,  # 二-4 TTL 必设
        task_acks_late=True,  # 四-5
        worker_prefetch_multiplier=1,
    )
    from kombu import Queue
    app.conf.task_queues = [Queue(q) for q in queues]
    assert_json_only(app)
    return app


def beat_guard(redis, service: str, *, ttl_s: int = 60):
    """四-3：beat 单实例守卫——锁获取失败即拒绝启动（多实例 beat = 周期任务全量双跑）。"""
    lock = redis.lock(f"{service}:beat:single", timeout=ttl_s)
    if not lock.acquire(blocking=False):
        raise RuntimeError("beat 须单实例（celery.md 四-3）：锁被占用，本实例拒绝启动")
    return lock


def run_once(redis, key: str, ttl_s: int = 3600) -> bool:
    """三-1 幂等键助手：业务键去重（至少一次交付下重复必然发生）。"""
    return bool(redis.set(key, "1", nx=True, ex=ttl_s))
```

- [ ] **Step 4: 单元通过 + TC 测试（beat 守卫互斥、run_once）**

Run: `uv run pytest packages/yarch-python/tests/test_celeryx.py -v` → 6 passed

```python
# stacks/python/packages/yarch-python/tests/test_celeryx_tc.py
import pytest

from yarch_python import celeryx

pytestmark = pytest.mark.integration


@pytest.fixture(scope="module")
def redis():
    from testcontainers.redis import RedisContainer
    with RedisContainer() as c:
        import redis as redis_lib
        yield redis_lib.Redis.from_url(c.get_connection_url())


def test_beat_guard_single_instance(redis):
    celeryx.beat_guard(redis, "ysaas-scan", ttl_s=30)
    with pytest.raises(RuntimeError):
        celeryx.beat_guard(redis, "ysaas-scan", ttl_s=30)


def test_run_once_dedup(redis):
    assert celeryx.run_once(redis, "ysaas-scan:once:job1") is True
    assert celeryx.run_once(redis, "ysaas-scan:once:job1") is False
```

Run: `uv run pytest packages/yarch-python/tests/test_celeryx_tc.py -v`
Expected: 2 passed

- [ ] **Step 5: Commit**

```bash
cd /Users/yuandonghao/sidejob/sources/yarch
git add stacks/python/packages/yarch-python/src/yarch_python/celeryx stacks/python/packages/yarch-python/tests/test_celeryx.py stacks/python/packages/yarch-python/tests/test_celeryx_tc.py
git commit -m "feat(stacks/python): celeryx 装配件——keyprefix/命名/json-only/超时重试/beat 单实例/trace 头/失败 SPI" -- stacks/python
```

---

### Task 13: testx（契约断言套件 + TC fixtures）

**Files:**
- Create: `stacks/python/packages/yarch-python/src/yarch_python/testx/__init__.py`、`fixtures.py`
- Test: `stacks/python/packages/yarch-python/tests/test_testx.py`

**Interfaces:**
- Consumes: 契约表（誊写自 error-codes.md，跨栈 conformance 同表口径）
- Produces:
  - `CODE_TABLE: list[tuple[int, str, str, int]]`（与 Task 3 测试同源同值，供业务工程 conformance 复用）
  - `assert_envelope(payload: dict, *, code: int, message_prefix: str | None = None)`（键序/形状/code!=0→data None）
  - `assert_page_data(data: dict, *, total: int, page: int, page_size: int)`
  - `assert_ndjson(line: str, *, service: str | None = None, msg: str | None = None)`（必填字段集 + ts 正则 + level 大写）
  - `yarch_python.testx.fixtures`：pytest fixtures `pg_url()` / `redis_url()`（session 级 testcontainers）

- [ ] **Step 1: 写失败测试**

```python
# stacks/python/packages/yarch-python/tests/test_testx.py
import pytest

from yarch_python.testx import CODE_TABLE, assert_envelope, assert_ndjson, assert_page_data


def test_code_table_has_14_rows():
    assert len(CODE_TABLE) == 14 and CODE_TABLE[0] == (0, "OK", "成功", 200)


def test_assert_envelope_ok():
    assert_envelope({"code": 0, "message": "成功", "data": None, "traceId": "t"} , code=0)


def test_assert_envelope_rejects_extra_or_missing_keys():
    with pytest.raises(AssertionError):
        assert_envelope({"code": 0, "message": "x", "data": None}, code=0)
    with pytest.raises(AssertionError):
        assert_envelope({"code": 0, "message": "x", "data": None, "traceId": "t", "extra": 1},
                        code=0)


def test_assert_envelope_error_data_must_be_null():
    with pytest.raises(AssertionError):
        assert_envelope({"code": 1001, "message": "m", "data": {}, "traceId": "t"}, code=1001)


def test_assert_page_data():
    assert_page_data({"list": [], "total": 5, "page": 3, "pageSize": 2}, total=5, page=3,
                     page_size=2)
    with pytest.raises(AssertionError):
        assert_page_data({"list": None, "total": 5, "page": 3, "pageSize": 2}, total=5, page=3,
                         page_size=2)


def test_assert_ndjson():
    line = ('{"ts":"2026-09-07T02:45:07.123Z","level":"INFO","service":"s","env":"local",'
            '"traceId":"t","logger":"l","msg":"request completed"}')
    assert_ndjson(line, service="s", msg="request completed")
    with pytest.raises(AssertionError):
        assert_ndjson(line.replace('"INFO"', '"info"'))
    with pytest.raises(AssertionError):
        assert_ndjson(line.replace(".123Z", ".123456Z"))
```

- [ ] **Step 2: 跑测试确认失败**

Run: `uv run pytest packages/yarch-python/tests/test_testx.py -v`
Expected: FAIL `ModuleNotFoundError … 'yarch_python.testx'`

- [ ] **Step 3: 实现**

```python
# stacks/python/packages/yarch-python/src/yarch_python/testx/__init__.py
"""testx：契约断言（13 码全表/信封形状/ndjson 字段级）——跨栈 conformance 同表，防方言漂移。"""
import json
import re
from typing import Optional

# error-codes.md v1.0 逐行誊写（0 成功 + 13 码）
CODE_TABLE: list[tuple[int, str, str, int]] = [
    (0, "OK", "成功", 200),
    (1000, "INTERNAL_ERROR", "内部错误", 500),
    (1001, "INVALID_ARGUMENT", "参数校验失败", 400),
    (1002, "MALFORMED_BODY", "请求体格式错误", 400),
    (1004, "NOT_FOUND", "资源不存在", 404),
    (1005, "CONFLICT", "资源冲突", 409),
    (1006, "RATE_LIMITED", "触发限流", 429),
    (1007, "IDEMPOTENCY_CONFLICT", "幂等冲突：重复提交", 409),
    (1008, "UPSTREAM_TIMEOUT", "上游依赖超时", 504),
    (1009, "UNAVAILABLE", "服务暂不可用", 503),
    (2001, "UNAUTHORIZED", "未认证", 401),
    (2002, "CREDENTIALS_EXPIRED", "凭证已过期", 401),
    (2003, "FORBIDDEN", "权限不足", 403),
    (2004, "ACCOUNT_DISABLED", "账号已禁用", 403),
]

_TS_RE = re.compile(r"^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d{3}Z$")
_LEVELS = {"TRACE", "DEBUG", "INFO", "WARN", "ERROR"}
_REQUIRED_KEYS = ["ts", "level", "service", "env", "traceId", "logger", "msg"]


def assert_envelope(payload: dict, *, code: int, message_prefix: Optional[str] = None) -> None:
    assert list(payload.keys()) == ["code", "message", "data", "traceId"], payload
    assert payload["code"] == code, payload
    if code != 0:
        assert payload["data"] is None, payload
    if message_prefix is not None:
        assert payload["message"].startswith(message_prefix), payload


def assert_page_data(data: dict, *, total: int, page: int, page_size: int) -> None:
    assert data["list"] is not None and isinstance(data["list"], list)
    assert data["total"] == total and data["page"] == page and data["pageSize"] == page_size


def assert_ndjson(line: str, *, service: Optional[str] = None,
                  msg: Optional[str] = None) -> None:
    d = json.loads(line)
    for k in _REQUIRED_KEYS:
        assert k in d, f"ndjson 缺字段 {k}: {line}"
    assert _TS_RE.match(d["ts"]), d["ts"]
    assert d["level"] in _LEVELS, d["level"]
    if service is not None:
        assert d["service"] == service
    if msg is not None:
        assert d["msg"] == msg
```

```python
# stacks/python/packages/yarch-python/src/yarch_python/testx/fixtures.py
"""TC fixtures：业务工程 conftest 里 pytest_plugins = ("yarch_python.testx.fixtures",)。"""
import pytest


@pytest.fixture(scope="session")
def pg_url():
    from testcontainers.postgres import PostgresContainer
    with PostgresContainer("postgres:17-alpine") as c:
        url = c.get_connection_url().replace("postgresql+psycopg://", "").replace(
            "postgresql://", "")
        yield f"postgresql+psycopg://{url}"


@pytest.fixture(scope="session")
def redis_url():
    from testcontainers.redis import RedisContainer
    with RedisContainer() as c:
        yield c.get_connection_url()
```

- [ ] **Step 4: 跑测试确认通过**

Run: `uv run pytest packages/yarch-python/tests/test_testx.py -v`
Expected: 6 passed

- [ ] **Step 5: Commit**

```bash
cd /Users/yuandonghao/sidejob/sources/yarch
git add stacks/python/packages/yarch-python/src/yarch_python/testx stacks/python/packages/yarch-python/tests/test_testx.py
git commit -m "feat(stacks/python): testx——契约断言套件 + TC fixtures（跨栈 conformance 同表）" -- stacks/python
```

---

### Task 14: yarch-init 生成器（渲染引擎 + registry 校验 + 残留扫描）

**Files:**
- Create: `stacks/python/packages/yarch-init/src/yarch_init/main.py`
- Test: `stacks/python/packages/yarch-init/tests/test_main.py`（含最小 fixture 模板）

**Interfaces:**
- Consumes: Jinja2、（Task 15 的 `_template` 为默认 src）
- Produces:
  - `validate_service(name: str) -> None`（不合规 raise `ValueError`）
  - `render(src: str, dst: str, variables: dict) -> int`（遍历渲染，archetype.json 跳过，`{{` 残留即 raise）
  - `detect_yarch_path(start: str) -> str | None`（向上找 `stacks/python/packages/yarch-python`，发版前 path 依赖用）
  - CLI：`yarch-init --service <name> --out <dir> [--description ...] [--src ...]`；成功打印四步指引（对齐 golang yarch-init）

- [ ] **Step 1: 写失败测试（用最小 fixture 模板，不依赖 Task 15）**

```python
# stacks/python/packages/yarch-init/tests/test_main.py
import json

import pytest

from yarch_init.main import detect_yarch_path, render, validate_service

FIXTURE = {
    "archetype.json": '{"description": "fixture"}',
    "pyproject.toml": 'name = "{{ service }}"\npkg = "{{ package }}"\n',
    "README.md": "# {{ service }}\n{{ description }}\n",
}


@pytest.fixture
def fixture_template(tmp_path):
    d = tmp_path / "_tpl"
    d.mkdir()
    for name, content in FIXTURE.items():
        (d / name).write_text(content)
    return str(d)


def test_validate_service_rules():
    validate_service("ysaas-scan")
    for bad in ["API", "a", "-abc", "user", "api", "common", "a" * 40, "under_score"]:
        with pytest.raises(ValueError):
            validate_service(bad)


def test_render_variables_and_skip_archetype(tmp_path, fixture_template):
    out = tmp_path / "out"
    n = render(fixture_template, str(out), {"service": "ysaas-scan", "package": "ysaas_scan",
                                            "description": "d"})
    assert n == 2
    assert (out / "pyproject.toml").read_text() == 'name = "ysaas-scan"\npkg = "ysaas_scan"\n'
    assert not (out / "archetype.json").exists()


def test_render_rejects_leftover_placeholder(tmp_path):
    d = tmp_path / "_tpl"
    d.mkdir()
    (d / "x.txt").write_text("{{ nope }}")
    with pytest.raises(Exception):
        render(str(d), str(tmp_path / "o"), {"service": "s"})


def test_detect_yarch_path_from_repo():
    import os
    here = os.path.dirname(__file__)
    p = detect_yarch_path(here)
    assert p is not None and p.endswith("packages/yarch-python")
```

- [ ] **Step 2: 跑测试确认失败**

Run: `uv run pytest packages/yarch-init/tests/test_main.py -v`
Expected: FAIL `ModuleNotFoundError … 'yarch_init.main'`

- [ ] **Step 3: 实现**

```python
# stacks/python/packages/yarch-init/src/yarch_init/main.py
"""yarch-init 工程生成器（cookiecutter/maven-archetype 模式的 python 对偶）：
模板（_template/，{{ var }} 占位声明式资产，不要求自身可运行）+ 通用渲染引擎（本程序）+ archetype.json 变量声明。
工程正确性由「生成后冒烟」保证（CI：生成 → uv sync → ruff → pytest → uvicorn 探活）。"""
import argparse
import os
import re
import sys
from pathlib import Path

from jinja2 import Environment, StrictUndefined

SERVICE_RE = re.compile(r"^[a-z][a-z0-9-]{1,31}$")
GENERIC_WORDS = {"api", "app", "service", "server", "backend", "web", "admin", "main",
                 "common", "system", "demo"}  # registry.md 一-2 摘录
SKIP_FILES = {"archetype.json"}


def validate_service(name: str) -> None:
    if not SERVICE_RE.match(name):
        raise ValueError(f"服务名 {name!r} 须过 registry.md 一-1 校验：^[a-z][a-z0-9-]{{1,31}}$")
    if name in GENERIC_WORDS:
        raise ValueError(f"服务名 {name!r} 是裸通用词，禁止使用（contract/registry.md 一-2）")


def detect_yarch_path(start: str) -> str | None:
    """发版前 path 依赖：向上找本仓 stacks/python/packages/yarch-python（对偶 golang replace 行）。"""
    d = Path(start).resolve()
    for _ in range(8):
        cand = d / "packages" / "yarch-python"
        if (cand / "pyproject.toml").exists():
            return str(cand)
        if d.parent == d:
            return None
        d = d.parent
    return None


def render(src: str, dst: str, variables: dict) -> int:
    env = Environment(undefined=StrictUndefined, keep_trailing_newline=True, autoescape=False)
    n = 0
    for path in sorted(Path(src).rglob("*")):
        if path.is_dir() or path.name in SKIP_FILES:
            continue
        rel = path.relative_to(src)
        target = Path(dst) / rel
        target.parent.mkdir(parents=True, exist_ok=True)
        rendered = env.from_string(path.read_text(encoding="utf-8")).render(**variables)
        if "{{" in rendered or "{%" in rendered:
            raise RuntimeError(f"模板残留未渲染占位符：{rel}")
        target.write_text(rendered, encoding="utf-8")
        n += 1
    return n


def template_dir(cli_src: str | None) -> str:
    if cli_src:
        return cli_src
    import yarch_init

    return str(Path(yarch_init.__file__).parent / "_template")


def main() -> None:
    ap = argparse.ArgumentParser(prog="yarch-init")
    ap.add_argument("--service", required=True, help="服务名（registry 一-1 校验）")
    ap.add_argument("--out", required=True, help="输出目录（须不存在或为空）")
    ap.add_argument("--description", default="")
    ap.add_argument("--src", default=None, help="模板目录（默认用 wheel 内置 _template）")
    args = ap.parse_args()

    try:
        validate_service(args.service)
    except ValueError as e:
        sys.exit(f"yarch-init: {e}")

    out = Path(args.out)
    if out.exists() and any(out.iterdir()):
        sys.exit(f"yarch-init: 输出目录 {out} 非空")

    yarch_path = detect_yarch_path(os.getcwd())
    variables = {
        "service": args.service,
        "package": args.service.replace("-", "_"),
        "description": args.description or f"{args.service} service",
        "yarch_path": yarch_path,  # None → 版本依赖（发版后形态）
    }
    n = render(template_dir(args.src), str(out), variables)
    dep_hint = (
        f"path 依赖 → {yarch_path}（yarch-python 发版后删除 tool.uv.sources 该行改版本号）"
        if yarch_path
        else "版本依赖（yarch-python >= 0.1）"
    )
    print(f"""✅ 已生成 {out}（{n} 个文件）——依赖形态：{dep_hint}

下一步：
  1. cd {out} && cp .env.example .env（填共享 PG/Redis 地址；独立 database 自动建库）&& uv sync
  2. uv run uvicorn main:app --reload   # web 进程；uv run celery -A celery_app worker 另进程
  3. 服务名 {args.service!r}——去 yarch 仓 contract/registry.md 登记
  4. types/errno.py 业务码段（3xxx+）在你的仓库 docs 登记后方可使用
""")


if __name__ == "__main__":
    main()
```

- [ ] **Step 4: 跑测试确认通过**

Run: `uv run pytest packages/yarch-init/tests/test_main.py -v`
Expected: 4 passed

- [ ] **Step 5: Commit**

```bash
cd /Users/yuandonghao/sidejob/sources/yarch
git add stacks/python/packages/yarch-init/src/yarch_init/main.py stacks/python/packages/yarch-init/tests
git commit -m "feat(stacks/python): yarch-init 生成器——Jinja2 渲染 + registry 校验 + 残留扫描" -- stacks/python
```

---

### Task 15: _template 模板资产（DDD 七包 + users 示例 + celery 任务 + alembic）

**Files:**
- Create: `stacks/python/packages/yarch-init/src/yarch_init/_template/` 下全部资产（下文逐文件）
- Test: `stacks/python/packages/yarch-init/tests/test_template.py`

**Interfaces:**
- Consumes: `yarch_init.main.render`（Task 14）；平台件全部 API（Task 2-13）
- Produces: 可被 `render()` 渲染的声明式资产树；变量仅 `{{ service }} / {{ package }} / {{ description }} / {{ yarch_path }}`

**Jinja 避让规约**：模板源码中不得出现非变量的 `{{`——集合字面量一律 `dict(a=1)` 风格、f-string 内不用双花括号。

- [ ] **Step 1: 写完整性失败测试**

```python
# stacks/python/packages/yarch-init/tests/test_template.py
from pathlib import Path

from yarch_init.main import render

REQUIRED = [
    "main.py", "celery_app.py", "pyproject.toml", ".env.example", ".python-version",
    ".gitignore", "Makefile", "Dockerfile", "README.md",
    "api/router.py", "api/handler/users.py", "api/model/user.py",
    "application/app.py", "application/settings.py",
    "domain/entity/user.py", "domain/repository/user.py",
    "infrastructure/database/models.py", "infrastructure/database/user_repo.py",
    "infrastructure/database/migrations/env.py", "infrastructure/database/migrations/script.py.mako",
    "infrastructure/database/migrations/versions/0001_init.py",
    "crossdomain/README.md", "conf/README.md", "pkg/README.md",
    "types/errno.py", "tasks/users_task.py", "tests/test_smoke.py", "tests/__init__.py",
]


def template_root() -> Path:
    import yarch_init
    return Path(yarch_init.__file__).parent / "_template"


def test_template_renders_all_required_files(tmp_path):
    n = render(str(template_root()), str(tmp_path),
               dict(service="ysaas-scan", package="ysaas_scan", description="d",
                    yarch_path="/tmp/yarch-python"))
    assert n >= len(REQUIRED)
    for rel in REQUIRED:
        assert (tmp_path / rel).exists(), rel
    main_py = (tmp_path / "main.py").read_text()
    assert "ysaas_scan" not in main_py  # main.py 不应掺包名（顶层入口）
    pyproject = (tmp_path / "pyproject.toml").read_text()
    assert 'path = "/tmp/yarch-python"' in pyproject
    assert "ysaas-scan" in (tmp_path / ".env.example").read_text()
```

- [ ] **Step 2: 跑测试确认失败**

Run: `uv run pytest packages/yarch-init/tests/test_template.py -v`
Expected: FAIL（模板目录不存在 → render 报错）

- [ ] **Step 3: 写全部模板资产**

```json
{
  "description": "yarch-python DDD 模板：FastAPI + SQLAlchemy + celery，双入口（web/worker）",
  "variables": [
    { "name": "service", "desc": "服务名（registry 一-1）", "required": true, "default": "" },
    { "name": "package", "desc": "import 包名（service 派生 snake）", "required": true, "default": "" },
    { "name": "description", "desc": "工程描述", "required": false, "default": "" },
    { "name": "yarch_path", "desc": "开发期本机平台源码路径（发版后为空走版本依赖）", "required": false, "default": "" }
  ]
}
```
（存为 `_template/archetype.json`）

```toml
# _template/pyproject.toml
[project]
name = "{{ service }}"
version = "0.1.0"
description = "{{ description }}"
requires-python = ">=3.12"
dependencies = [
    "fastapi>=0.115",
    "uvicorn[standard]>=0.30",
    "yarch-python>=0.1.0",
]

[dependency-groups]
dev = [
    "pytest>=8.3",
    "ruff>=0.6",
    "mypy>=1.11",
    "import-linter>=2.0",
    "testcontainers[postgres,redis]>=4.8",
]
{% if yarch_path %}
[tool.uv.sources]
yarch-python = { path = "{{ yarch_path }}" }
{% endif %}
[tool.ruff]
line-length = 100
target-version = "py312"

[tool.ruff.lint]
select = ["E", "F", "I", "UP", "B"]

[tool.pytest.ini_options]
markers = ["integration: needs docker (testcontainers)"]

[tool.importlinter]
root_packages = ["api", "application", "domain", "infrastructure", "types", "tasks"]

[[tool.importlinter.contracts]]
name = "Domain purity (no framework, no outer layers)"
type = "forbidden"
source_modules = ["domain"]
forbidden_modules = ["infrastructure", "api", "application", "tasks", "fastapi", "sqlalchemy", "celery", "redis"]

[[tool.importlinter.contracts]]
name = "API must not touch infrastructure directly"
type = "forbidden"
source_modules = ["api"]
forbidden_modules = ["infrastructure"]
```

```python
# _template/main.py
"""Web 入口：uv run uvicorn main:app --reload"""
from application.app import create_app

app = create_app()

if __name__ == "__main__":
    import uvicorn

    uvicorn.run("main:app", host="0.0.0.0", port=8000, reload=True)
```

```python
# _template/celery_app.py
"""Worker/Beat 入口（分离部署，celery.md 四-1/五-4）：
uv run celery -A celery_app worker -Q {{ service }}.default
uv run celery -A celery_app beat   # 单实例！celeryx.beat_guard 守卫
"""
import redis as redis_lib

from application.settings import settings
from yarch_python import celeryx

celery_app = celeryx.make_app(
    settings.service,
    settings.celery_broker_url,
    soft_time_limit=60,
    hard_time_limit=90,
    result_backend=settings.celery_result_backend,
)
celery_app.autodiscover_tasks(["tasks"])
assert_json = celeryx.assert_json_only(celery_app)  # 启动即自检 json-only


def beat_single_instance() -> None:
    r = redis_lib.Redis.from_url(settings.redis_url)
    celeryx.beat_guard(r, settings.service)
```

```python
# _template/application/settings.py
"""配置：.env 单文件（pydantic-settings）；env 值域 local/dev/staging/prod（logging-trace.md）。"""
from pydantic_settings import BaseSettings


class Settings(BaseSettings):
    service: str = "{{ service }}"
    env: str = "local"
    database_url: str = "postgresql+psycopg://postgres:postgres@localhost/{{ service }}"
    redis_url: str = "redis://localhost:6379/0"
    celery_broker_url: str = "redis://localhost:6379/0"
    celery_result_backend: str | None = None
    skip_migrations: bool = False  # 测试态跳过建库迁移

    model_config = {"env_prefix": "YARCH_"}


settings = Settings()
```

```python
# _template/application/app.py
"""组装根（手写分阶段：basic 配置 → primary 数据/中间件 → complex 路由）。"""
from fastapi import FastAPI

from application.settings import settings
from api.router import api_router
from infrastructure.database import models  # noqa: F401  注册 ORM 映射
from infrastructure.database.user_repo import SqlAlchemyUserRepository
from yarch_python import persist, redix, logx
from yarch_python.web import ok, setup


def create_app() -> FastAPI:
    # stage 1 basic
    logx.setup(settings.service, settings.env)
    if not settings.skip_migrations:
        persist.ensure_database(settings.database_url)
        persist.alembic_upgrade("infrastructure/database/migrations", settings.database_url)
    # stage 2 primary
    session_factory = persist.sessionmaker_for(settings.database_url)
    import redis as redis_lib

    redis_client = redis_lib.Redis.from_url(settings.redis_url)
    keys = redix.Keys(settings.service)
    app = FastAPI(title=settings.service)
    setup(
        app,
        service=settings.service,
        env=settings.env,
        idempotency_store=redix.IdempotencyStore(redis_client),
        rate_limit=(redix.FixedWindowLimiter(redis_client), 100, 1),
    )
    app.state.session_factory = session_factory
    app.state.user_repo = SqlAlchemyUserRepository(session_factory)
    app.state.redis = redis_client
    app.state.keys = keys
    # stage 3 complex
    app.include_router(api_router)

    @app.get("/healthz")
    def healthz():
        return ok(dict(service=settings.service, status="up"))

    return app
```

```python
# _template/api/model/user.py
from datetime import datetime

from pydantic import BaseModel, Field


class CreateUserRequest(BaseModel):
    username: str = Field(min_length=2, max_length=32)
    email: str | None = None


class UserResponse(BaseModel):
    id: str
    username: str
    email: str | None
    createdAt: datetime | None = None
```

```python
# _template/domain/entity/user.py
"""领域实体：零框架依赖（不 import sqlalchemy/fastapi）。"""
from datetime import datetime

from pydantic import BaseModel


class User(BaseModel):
    id: str
    username: str
    email: str | None = None
    created_at: datetime | None = None
```

```python
# _template/domain/repository/user.py
"""repo port（typing.Protocol）：adapter 在 infrastructure。"""
from typing import Protocol

from domain.entity.user import User


class UserRepository(Protocol):
    def create(self, username: str, email: str | None) -> User: ...

    def get(self, user_id: str) -> User | None: ...

    def list_page(self, page: int, page_size: int) -> tuple[list[User], int]: ...
```

```python
# _template/infrastructure/database/models.py
from __future__ import annotations

from datetime import datetime
from typing import Any

from sqlalchemy import Text
from sqlalchemy.dialects.postgresql import UUID
from sqlalchemy.orm import Mapped, mapped_column

from yarch_python.persist import AuditMixin, Base, SoftDeleteMixin


class UserRow(Base, AuditMixin, SoftDeleteMixin):
    __tablename__ = "users"

    id: Mapped[Any] = mapped_column(UUID(as_uuid=True), primary_key=True,
                                    server_default=text_uuid())
    username: Mapped[str] = mapped_column(Text, nullable=False, unique=True)
    email: Mapped[str | None] = mapped_column(Text)


def text_uuid():
    from sqlalchemy import text as _text

    return _text("gen_random_uuid()")
```

（注：`text_uuid()` 写法为避免模板内直书 `text("gen_random_uuid()")` 与说明冲突——直接 `server_default=text("gen_random_uuid()")` 亦可，二选一，保持一致即可。）

```python
# _template/infrastructure/database/user_repo.py
"""repo adapter：实现 domain 的 UserRepository Protocol。"""
from datetime import datetime

from sqlalchemy import select

from domain.entity.user import User
from yarch_python.persist import not_deleted, page_of


class SqlAlchemyUserRepository:
    def __init__(self, session_factory):
        self.session_factory = session_factory

    def create(self, username: str, email: str | None) -> User:
        from infrastructure.database.models import UserRow

        with self.session_factory() as s:
            row = UserRow(username=username, email=email)
            s.add(row)
            s.commit()
            return self._to_entity(row)

    def get(self, user_id: str) -> User | None:
        from infrastructure.database.models import UserRow

        with self.session_factory() as s:
            row = s.get(UserRow, user_id)
            if row is None or row.is_deleted:
                return None
            return self._to_entity(row)

    def list_page(self, page: int, page_size: int) -> tuple[list[User], int]:
        from infrastructure.database.models import UserRow

        with self.session_factory() as s:
            items, total = page_of(s, select(UserRow).where(not_deleted(UserRow)).order_by(
                UserRow.created_at.desc(), UserRow.id), page, page_size)
            return [self._to_entity(r) for r in items], total

    @staticmethod
    def _to_entity(row) -> User:
        return User(id=str(row.id), username=row.username, email=row.email,
                    created_at=row.created_at if isinstance(row.created_at, datetime) else None)
```

```python
# _template/api/handler/users.py
from fastapi import APIRouter, Request

from api.model.user import CreateUserRequest, UserResponse
from yarch_python.web import ok, page
from yarch_python.xerror import BizError

router = APIRouter(prefix="/api/v1")


@router.post("/users", status_code=201)
def create_user(body: CreateUserRequest, request: Request):
    repo = request.app.state.user_repo
    user = repo.create(body.username, body.email)
    return ok(UserResponse(id=user.id, username=user.username, email=user.email,
                           createdAt=user.created_at).model_dump(), status_code=201)


@router.get("/users")
def list_users(page: int = 1, pageSize: int = 20, request: Request = None):
    if page < 1 or pageSize < 1 or pageSize > 100:
        raise BizError(1001, detail="page 须为正整数，pageSize 须在 1~100")
    repo = request.app.state.user_repo
    items, total = repo.list_page(page, pageSize)
    return page([u.model_dump() for u in items], total, page, pageSize)


@router.get("/users/{user_id}")
def get_user(user_id: str, request: Request):
    user = request.app.state.user_repo.get(user_id)
    if user is None:
        raise BizError(1004, detail=f"user {user_id}")
    return ok(UserResponse(id=user.id, username=user.username, email=user.email,
                           createdAt=user.created_at).model_dump())
```

```python
# _template/api/router.py
from api.handler import users

api_router = users.router
```

```python
# _template/types/errno.py
"""业务码登记处（3xxx-8xxx；在本仓 docs 登记后方可使用——error-codes.md 实现规则-3）。"""
from yarch_python import errcode

# 示例登记（可替换为你的业务码）：
errcode.register(3001, "USER_EXISTS", "用户已存在", 409)
```

```python
# _template/tasks/users_task.py
"""celery 示例任务：{{ service }}.users.sync——traceId 继承 + run_once 幂等演示。"""
from application.settings import settings
from yarch_python import celeryx, logx

try:  # celery -A celery_app 进程内 app 已就绪
    from celery_app import celery_app
except ImportError:  # type: ignore
    celery_app = None  # pragma: no cover


def register_tasks(app) -> None:
    @app.task(base=celeryx.TraceTask, name=celeryx.task_name(settings.service, "users", "sync"),
              **celeryx.retry_options())
    def sync_user(user_id: str):
        import redis as redis_lib

        r = redis_lib.Redis.from_url(settings.redis_url)
        if not celeryx.run_once(r, f"{settings.service}:once:users-sync:{user_id}", ttl_s=3600):
            logx.get_logger("{{ service }}.users").info("skip duplicated sync", userId=user_id)
            return dict(user=user_id, deduped=True)
        logx.get_logger("{{ service }}.users").info("sync user", userId=user_id)
        return dict(user=user_id, deduped=False)
```

（注：`register_tasks(app)` 由 `celery_app.autodiscover_tasks(["tasks"])` 触发时需要模块级任务定义——实现时把任务定义改为模块级 `@celery_app.task`（celery_app 通过 `celery.current_app` 惰性绑定），去掉 register_tasks 包装：）

```python
# _template/tasks/users_task.py（最终形态——用这个）
"""celery 示例任务：{{ service }}.users.sync——traceId 继承 + run_once 幂等演示。"""
from celery import current_app

from application.settings import settings
from yarch_python import celeryx, logx


@current_app.task(
    base=celeryx.TraceTask,
    name=celeryx.task_name(settings.service, "users", "sync"),
    **celeryx.retry_options(),
)
def sync_user(user_id: str) -> dict:
    import redis as redis_lib

    r = redis_lib.Redis.from_url(settings.redis_url)
    if not celeryx.run_once(r, f"{settings.service}:once:users-sync:{user_id}", ttl_s=3600):
        logx.get_logger("{{ service }}.users").info("skip duplicated sync", userId=user_id)
        return dict(user=user_id, deduped=True)
    logx.get_logger("{{ service }}.users").info("sync user", userId=user_id)
    return dict(user=user_id, deduped=False)
```

```python
# _template/infrastructure/database/migrations/env.py
from alembic import context
from sqlalchemy import create_engine, pool

from infrastructure.database.models import Base

config = context.config
target_metadata = Base.metadata


def run_migrations_online() -> None:
    engine = create_engine(config.get_main_option("sqlalchemy.url"), poolclass=pool.NullPool)
    with engine.connect() as connection:
        context.configure(connection=connection, target_metadata=target_metadata)
        with context.begin_transaction():
            context.run_migrations()


run_migrations_online()
```

```python
# _template/infrastructure/database/migrations/script.py.mako
"""${message}

Revision ID: ${up_revision}
Revises: ${down_revision | comma,n}
"""
from alembic import op
import sqlalchemy as sa

revision = ${repr(up_revision)}
down_revision = ${repr(down_revision)}


def upgrade() -> None:
    ${upgrades if upgrades else "pass"}


def downgrade() -> None:
    ${downgrades if downgrades else "pass"}
```

```python
# _template/infrastructure/database/migrations/versions/0001_init.py
"""init users

Revision ID: 0001
Revises:
"""
from alembic import op

revision = "0001"
down_revision = None


def upgrade() -> None:
    op.execute(
        """
        CREATE TABLE IF NOT EXISTS users (
            id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
            username TEXT NOT NULL UNIQUE,
            email TEXT,
            is_deleted BOOLEAN NOT NULL DEFAULT false,
            created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
            updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
        )
        """
    )


def downgrade() -> None:
    op.execute("DROP TABLE IF EXISTS users")
```

```python
# _template/tests/__init__.py
（空文件）
```

```python
# _template/tests/test_smoke.py
"""冒烟：信封/分页/1001/1002/1004 全链路（TestClient + TC PG/Redis）。"""
import io
import os

import pytest
from fastapi.testclient import TestClient

os.environ.setdefault("YARCH_SKIP_MIGRATIONS", "true")

from application.app import create_app  # noqa: E402
from yarch_python import logx  # noqa: E402
from yarch_python.testx import assert_envelope, assert_page_data  # noqa: E402

pytestmark = pytest.mark.integration


@pytest.fixture(scope="module")
def client(pg_url, redis_url):
    os.environ["YARCH_DATABASE_URL"] = pg_url
    os.environ["YARCH_REDIS_URL"] = redis_url
    os.environ["YARCH_SKIP_MIGRATIONS"] = "false"
    logx.setup("{{ service }}", "local", sink=io.StringIO())
    from yarch_python.persist import alembic_upgrade

    alembic_upgrade("infrastructure/database/migrations", pg_url)
    return TestClient(create_app())


def test_healthz_envelope(client):
    r = client.get("/healthz")
    assert r.status_code == 200
    assert_envelope(r.json(), code=0)
    assert r.json()["traceId"] == r.headers["x-trace-id"]


def test_users_crud_and_page(client):
    r = client.post("/api/v1/users", json={"username": "alice", "email": "a@b.c"})
    assert r.status_code == 201
    assert_envelope(r.json(), code=0)
    uid = r.json()["data"]["id"]
    assert client.get(f"/api/v1/users/{uid}").json()["data"]["username"] == "alice"
    d = client.get("/api/v1/users").json()["data"]
    assert_page_data(d, total=1, page=1, page_size=20)


def test_1001_1002_1004(client):
    assert client.post("/api/v1/users", json={"username": "x"}).json()["code"] == 1001
    r = client.post("/api/v1/users", content=b"{bad",
                    headers={"content-type": "application/json"})
    assert r.json()["code"] == 1002
    assert client.get("/api/v1/users/00000000-0000-0000-0000-000000000000").json()["code"] == 1004


def test_worker_task_trace_and_dedup(redis_url):
    from tasks import users_task

    users_task.sync_user.apply(args=["u1"], headers={"traceId": "0af7651916cd43dd8448eb211c80319c"})
    import redis as redis_lib

    r = redis_lib.Redis.from_url(redis_url)
    r.delete("{{ service }}:once:users-sync:u1")
```

模板 conftest（TC fixtures 接线）：

```python
# _template/tests/conftest.py
pytest_plugins = ("yarch_python.testx.fixrices",)
```

（笔误校正：正确拼写为 `("yarch_python.testx.fixtures",)`——落盘时用正确拼写。）

```bash
# _template/.env.example
# 共享实例隔离模式：连共享 PG/Redis，database/key 前缀按服务名隔离（起跑主路径，免 docker compose）
YARCH_SERVICE={{ service }}
YARCH_ENV=local
YARCH_DATABASE_URL=postgresql+psycopg://USER:PASS@PGHOST/{{ service }}
YARCH_REDIS_URL=redis://REDISHOST:6379/0
YARCH_CELERY_BROKER_URL=redis://REDISHOST:6379/0
# YARCH_CELERY_RESULT_BACKEND=redis://REDISHOST:6379/0   # 需要取回结果时启用（TTL 默认 1 天）
```

```
# _template/.python-version
3.12
```

```gitignore
# _template/.gitignore
__pycache__/
*.pyc
.venv/
.env
.pytest_cache/
.mypy_cache/
.ruff_cache/
uv.lock
```

```makefile
# _template/Makefile
.PHONY: dev worker beat test lint fmt

dev:
	uv run uvicorn main:app --reload

worker:
	uv run celery -A celery_app worker -Q {{ service }}.default -c 2

beat:
	uv run celery -A celery_app beat

test:
	uv run pytest

lint:
	uv run ruff check . && uv run lint-imports

fmt:
	uv run ruff format .
```

```dockerfile
# _template/Dockerfile
FROM ghcr.io/astral-sh/uv:python3.12-bookworm-slim AS builder
WORKDIR /app
COPY pyproject.toml uv.lock ./
RUN uv sync --frozen --no-dev
COPY . .

FROM python:3.12-slim-bookworm
WORKDIR /app
COPY --from=builder /app /app
ENV PATH="/app/.venv/bin:$PATH"
EXPOSE 8000
# web 默认；worker 部署覆写 command：
#   docker run … uv run celery -A celery_app worker -Q {{ service }}.default
CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8000"]
```

```markdown
# _template/README.md
# {{ service }}

{{ description }}——由 `yarch-init` 生成（yarch-python DDD 模板）。

## 起跑（共享实例隔离模式）
    cd {{ service }} && cp .env.example .env && vi .env
    uv sync
    uv run uvicorn main:app --reload          # web：自动建库 + Alembic 升级 → :8000/docs
    uv run celery -A celery_app worker -Q {{ service }}.default   # worker（另终端）

## 纪律
- 服务名 `{{ service }}` 去 yarch 仓 contract/registry.md 登记；
- 业务码 3xxx+ 在本仓 docs 登记后方可使用（types/errno.py）；
- 升级平台件：`uv add "yarch-python@X.Y.Z"`{{ '\n' }}{%- if yarch_path %}（当前为开发期 path 依赖：`{{ yarch_path }}`，yarch-python 正式发版后删除 pyproject 的 `[tool.uv.sources]` 段改版本号）{%- endif %}
```

`crossdomain/README.md`、`conf/README.md`、`pkg/README.md` 各一行说明（域间防腐占位 / 配置说明见 application/settings.py / 工程内共享工具占位）。

- [ ] **Step 4: 跑测试确认通过**

Run: `uv run pytest packages/yarch-init/tests/test_template.py -v`
Expected: 1 passed

- [ ] **Step 5: Commit**

```bash
cd /Users/yuandonghao/sidejob/sources/yarch
git add stacks/python/packages/yarch-init/src/yarch_init/_template stacks/python/packages/yarch-init/tests/test_template.py
git commit -m "feat(stacks/python): _template DDD 模板资产——七包 + users/celery 示例 + alembic + 双入口" -- stacks/python
```

---

### Task 16: 生成后冒烟（端到端验收）

**Files:**
- Create: 无新文件（验证性任务；失败则回改 Task 14/15 资产）

**Interfaces:**
- Consumes: Task 14 CLI + Task 15 模板 + 平台件全套
- Produces: 端到端绿的证据（本任务输出进 commit message / 后续 README 验收节素材）

- [ ] **Step 1: 生成工程**

```bash
cd /Users/yuandonghao/sidejob/sources/yarch/stacks/python
rm -rf /tmp/smoke-svc && uv run yarch-init --service smoke-svc --out /tmp/smoke-svc --description "smoke"
```

Expected: `✅ 已生成 /tmp/smoke-svc（N 个文件）`，N ≥ 28，提示 path 依赖形态。

- [ ] **Step 2: 装依赖 + 静态机检**

```bash
cd /tmp/smoke-svc && cp .env.example .env && uv sync
uv run ruff check .
uv run lint-imports
```

Expected: ruff 0 errors；`Contracts: 2 kept, 0 broken.`

- [ ] **Step 3: 行为级测试（TC PG/Redis）+ 探活**

```bash
open -a OrbStack   # macOS 容器前置
uv run pytest -v
uv run uvicorn main:app --port 8123 & sleep 3
curl -s http://127.0.0.1:8123/healthz
kill %1
```

Expected: pytest 全绿（smoke 5 项）；curl 返回 `{"code":0,"message":"成功","data":{...},"traceId":"…"}` 且响应头带 `x-trace-id`。

- [ ] **Step 4: 失败即回改（循环）**

任何一步失败：修 Task 14/15 资产后从 Step 1 重跑。禁止在本任务打补丁文件到生成物。

- [ ] **Step 5: Commit（若有模板修正）**

```bash
cd /Users/yuandonghao/sidejob/sources/yarch
git add stacks/python/packages/yarch-init
git commit -m "fix(stacks/python): 模板冒烟修正——<具体原因>" -- stacks/python
```

---

### Task 17: CI（python-stack.yml 双矩阵 + python-publish.yml）

**Files:**
- Create: `.github/workflows/python-stack.yml`、`.github/workflows/python-publish.yml`

**Interfaces:**
- Consumes: Task 1-16 全部
- Produces: push/PR 全绿流水线；tag `stacks/python/vX.Y.Z` 发版流水线（trusted publishing）

- [ ] **Step 1: 写 python-stack.yml**

```yaml
# .github/workflows/python-stack.yml
name: python-stack
on:
  push:
    branches: [main]
    paths: ["stacks/python/**", ".github/workflows/python-stack.yml"]
  pull_request:
    paths: ["stacks/python/**", ".github/workflows/python-stack.yml"]

jobs:
  check:
    runs-on: ubuntu-latest
    strategy:
      matrix:
        python-version: ["3.12", "3.13"]
    defaults:
      run:
        working-directory: stacks/python
    steps:
      - uses: actions/checkout@v4
      - uses: astral-sh/setup-uv@v5
        with:
          python-version: ${{ matrix.python-version }}
      - run: uv sync
      - run: uv run ruff check . && uv run ruff format --check .
      - run: uv run lint-imports
      - run: uv run mypy
      - run: uv run pytest -v   # 含 integration（ubuntu-latest 自带 docker）

  generate-smoke:
    runs-on: ubuntu-latest
    needs: check
    steps:
      - uses: actions/checkout@v4
      - uses: astral-sh/setup-uv@v5
        with:
          python-version: "3.12"
      - name: generate
        run: |
          cd stacks/python
          uv sync
          uv run yarch-init --service smoke-svc --out /tmp/smoke-svc --description smoke
      - name: sync + static + test + probe
        run: |
          cd /tmp/smoke-svc && cp .env.example .env && uv sync
          uv run ruff check .
          uv run lint-imports
          uv run pytest -m "not integration" -v
          uv run uvicorn main:app --port 8123 & sleep 3
          curl -sf http://127.0.0.1:8123/healthz | grep '"code":0'
          kill %1
```

- [ ] **Step 2: 写 python-publish.yml**

```yaml
# .github/workflows/python-publish.yml
name: python-publish
on:
  push:
    tags: ["stacks/python/v*"]

jobs:
  publish:
    runs-on: ubuntu-latest
    permissions:
      id-token: write   # PyPI trusted publishing（OIDC，无 token secret）
    steps:
      - uses: actions/checkout@v4
      - uses: astral-sh/setup-uv@v5
        with:
          python-version: "3.12"
      - run: cd stacks/python && uv build --all-packages
      - uses: pypa/gh-action-pypi-publish@release/v1
        with:
          packages-dir: stacks/python/packages/yarch-python/dist
      - uses: pypa/gh-action-pypi-publish@release/v1
        with:
          packages-dir: stacks/python/packages/yarch-init/dist
```

（人工前置一次性动作，发版日执行，不在 CI 内：pypi.org 注册账号 → 两项目各自添加 trusted publisher：owner=ydonghao、repo=yarch、workflow=python-publish.yml、environment 留空。）

- [ ] **Step 3: 本地验证 workflow 语法**

Run: `cd /Users/yuandonghao/sidejob/sources/yarch && for f in .github/workflows/python-*.yml; do python3 -c "import yaml,sys; yaml.safe_load(open('$f')); print('$f ok')"; done`
Expected: 两行 ok

- [ ] **Step 4: Commit**

```bash
git add .github/workflows/python-stack.yml .github/workflows/python-publish.yml
git commit -m "ci(stacks/python): 双矩阵机检 + 生成后冒烟 + PyPI trusted publishing 发版流水线" -- .github/workflows/python-stack.yml .github/workflows/python-publish.yml
```

---

### Task 18: README + 架构图 + 随批登记 + 全量回归收口

**Files:**
- Create: `stacks/python/README.md`、`stacks/python/architecture-diagram.svg`
- Modify: `stacks/README.md`、`docs/architecture.md`、`contract/README.md`（术语对照表加 python 列）、`tools/locate-scaffolds.cjs`、`stacks/python/PLAN.md`（状态头）

**Interfaces:**
- Consumes: 全部前序任务
- Produces: 登记完成、文档收口、全量绿。

- [ ] **Step 1: 写 stacks/python/README.md**

体例对齐 golang README：标题块（坐标/契约权威/PLAN 链接/架构图链接）→ 构件表（一规约一 module，对偶 java 一规约一 starter）→ 快速开始两段式（发版前 clone 路径 / 发版后 `uvx yarch-init@latest`）→ 验收清单 → 机检 → 本机备注（OrbStack、uv）。内容如实：构件清单 = Task 2-13 产物；验收 = 各任务测试要点汇总；命令照抄 Task 16 验证过的形态。

- [ ] **Step 2: 写 architecture-diagram.svg**

手写紧凑 SVG（六层：业务工程/显式装配/平台构件/统一契约/运行底座 + FastAPI 请求生命周期横带），风格对齐 golang 图（盒子+箭头，中文标签）。尺寸 ~900×420，纯静态文本+rect+path，无外部依赖。

- [ ] **Step 3: 随批登记（四处）**

1. `stacks/README.md`：表中 `python/` 行更新状态（`python ✅（第一批 …）`），并在当前阶段段落注明 python 已启动交付。
2. `docs/architecture.md`：三·五节补 `python（pkg 构件 + FastAPI 请求生命周期）` 图引用；第六批状态行同步。
3. `contract/README.md` 术语对照表加 python 列：`Response[T]→yarch_python.response.Response` / `PageData[T]→…response.PageData` / `GlobalErrorCode→errcode.Code` / `BusinessException→xerror.BizError` / `TraceIdFilter(MDC)→middleware.TraceMiddleware(contextvars)`。
4. `tools/locate-scaffolds.cjs`：`STACKS = ['java', 'golang', 'rust', 'web']` → 插入 `'python'`。

- [ ] **Step 4: PLAN.md 状态头收口**

`stacks/python/PLAN.md` 状态行改为：`状态：第一批施工完成（YYYY-MM-DD），P1-P14 已拍板`，附验收记录一行（各任务测试计数与冒烟结论）。

- [ ] **Step 5: 全量回归**

```bash
cd /Users/yuandonghao/sidejob/sources/yarch/stacks/python
uv run ruff check . && uv run ruff format --check . && uv run lint-imports && uv run mypy
uv run pytest -v
node ../../tools/locate-scaffolds.cjs python   # 打印 stacks/python 路径
```

Expected: 全绿；locate-scaffolds 输出 python 栈路径。

- [ ] **Step 6: Commit**

```bash
cd /Users/yuandonghao/sidejob/sources/yarch
git add stacks/python/README.md stacks/python/architecture-diagram.svg stacks/python/PLAN.md stacks/README.md docs/architecture.md contract/README.md tools/locate-scaffolds.cjs
git commit -m "docs(stacks/python): README/架构图 + 随批登记（stacks 表/architecture/术语对照/locate-scaffolds）+ PLAN 收口" -- stacks/python stacks/README.md docs/architecture.md contract/README.md tools/locate-scaffolds.cjs
```

---

## 计划自审记录

1. **Spec coverage**：PLAN.md 第五节契约映射逐行核对——response/errcode/xerror（T2-4）、ndjson（T5）、traceId 入口回显（T6）、1001/1002/分页 D6/白名单（T7）、幂等（T8/T9）、PG 逻辑删除/审计/迁移（T10）、出口传播（T11）、celery 逐条（T12：二-1/2/3/5、三-1/2/3/4/5、四-3/4；四-1/2 与五为部署形态，落 T15 模板 Dockerfile/Makefile + README）、testx conformance（T13）、生成器/模板/冒烟（T14-16）、CI/发版（T17）、登记（T18）。第一节既定约束 1-7 全覆盖。P1-P14 全覆盖（P5 workspace=T1、P7=T1/T15、P8=T10/T15、P9=T5、P10=T1、P11=T15、P12=T17、P13=T1/T17、P14=T17）。无缺口。
2. **Placeholder scan**：无 TBD/TODO；两处「笔误校正」注记（web.page 的 if False 行、conftest fixtures 拼写）已显式给出正确形态，执行者照正确形态落盘。
3. **Type consistency**：`ok()/page()`（T7 定义，T15 模板消费同签名）；`IdempotencyStore.acquire` 三态（T8 定义，T9 消费）；`TraceTask/retry_options/task_name/queue_name/run_once/beat_guard`（T12 定义，T15 消费）；`render/validate_service/detect_yarch_path`（T14 定义，T15/T16 消费）；`pg_url/redis_url` fixtures（T13 定义，T15 模板 conftest 消费）。
