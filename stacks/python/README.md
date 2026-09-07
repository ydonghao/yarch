# stacks/python · yarch-python

> yarch Python 栈平台构件（uv workspace 双发行版 `yarch-python` + `yarch-init`，`requires-python >=3.12` 基线；发 PyPI，业务工程 `uv add yarch-python` 引入，版本步进自动跟进）。
> 契约唯一权威来源：[../contract/](../contract/README.md)——实现与契约不一致即 bug。
> 策划与拍板记录：[PLAN.md](PLAN.md)（P1-P14 已拍板，2026-09-07；celery.md 承接为本栈启动触发）。
> 架构图：[architecture-diagram.svg](architecture-diagram.svg)（六层 + FastAPI 请求生命周期）。
> 技术叙事：FastAPI 同步核心 + DDD 七包同构 coze-studio；celery 规约由 celeryx 装配件一次封死。

## 构件（一规约一 module，对偶 java 一规约一 starter / golang 一规约一 package）

```
stacks/python/                                   # uv workspace（根 pyproject + uv.lock；CI 3.12/3.13 双矩阵）
├── packages/yarch-python/                       # ① 平台构件发行版（PyPI: yarch-python → import yarch_python）
│   └── src/yarch_python/
│       ├── response/                            #   契约内核①：Response[T] / PageData[T] / PageQuery（零框架依赖）
│       ├── errcode/                             #   契约内核②：Code + 13 码全表 + HTTP 映射 + 业务码注册
│       ├── xerror/                              #   契约内核③：BizError（message「默认文案：细节」规则）
│       ├── logx/                                #   structlog ndjson 行协议（ts/level/service/env/traceId/logger/msg）+ contextvars
│       ├── middleware/                          #   Trace(traceparent 优先) / Recovery→1000 / AccessLog / Idempotency→1007 / RateLimit→1006
│       ├── web/                                 #   setup() 一行装配 + 信封回包 + 1001/1002 分型 + 分页绑定(D6)
│       ├── persist/                             #   SQLAlchemy 2.x + psycopg3：逻辑删除 is_deleted + 审计 + page_of 分页下推 + Alembic 辅助
│       ├── redix/                               #   Keys(首段=服务名) + JSON Cache + Lock + Idempotency(三态) + FixedWindowLimiter
│       ├── httpx/                               #   下游客户端：超时强制(≤30s) + traceparent 注入 + 信封解包 + 1008/1009
│       ├── celeryx/                             #   celery 装配件：celery.md 强制默认一次性封死（keyprefix/队列/命名/json-only/超时/重试/beat 单实例/trace 头/失败 SPI）
│       └── testx/                               #   契约断言（13 码/信封/ndjson/PageData，跨栈 conformance 同表）+ TC PG/Redis 基座
└── packages/yarch-init/                         # ② 工程生成器发行版（PyPI: yarch-init，console_scripts: yarch-init，仅依赖 jinja2）
    └── src/yarch_init/
        ├── main.py                              #   Jinja2 渲染 + registry 服务名/裸词校验 + 残留占位扫描
        └── _template/                           #   模板资产随 wheel 携带（{{ var }} 占位 + archetype.json）
            └── …                                #   DDD 七包：api/application/domain/crossdomain/infrastructure/errors + conf/pkg 占位
                                                 #   （errors/ 即他栈 types 层——python types 与 stdlib 冲突，已拍板更名）
```

依赖方向纪律（import-linter 机检固化）：契约内核三件零框架依赖；构件间禁止反向依赖（对偶 golang depguard，生成工程预配置同契约随初始化生效）。

## 快速开始（新人从零到一）

当前阶段（PyPI 未发版）从本仓跑生成器，模板随仓携带：

```bash
git clone https://github.com/ydonghao/yarch && cd yarch/stacks/python
uv sync --all-packages && uv run yarch-init --service ysaas-scan --out ~/code/ysaas-scan
```

发版后（推送 tag `stacks/python/vX.Y.Z` → [python-publish.yml](../../.github/workflows/python-publish.yml) PyPI trusted publishing 幂等发布双件）零 clone 一条命令：

```bash
uvx yarch-init@latest --service ysaas-scan --out ysaas-scan
```

平台升级 = 发版式 `uv add "yarch-python@X.Y.Z"`，业务工程自动跟进。

> 生成工程 pyproject 的 `[tool.uv.sources]` path 依赖行在正式发版后删除、版本改正式 tag（生成 README 已注明）。

### 起跑三步（生成工程，共享实例隔离模式）

```bash
# ① 填 .env（连共享/自有 PG+Redis；独立 database，web 启动自动建库 + Alembic 自动升级）
cd ~/code/ysaas-scan && cp .env.example .env && vi .env
# ② web（→ :8000/docs，users 示例在 /api/v1/users）
uv sync && uv run uvicorn main:app --reload
# ③ worker/beat 另进程（队列 = 服务名.用途，celery.md 二-2）
uv run celery -A celery_app worker -Q ysaas-scan.default
```

## 验收（随 CI 双矩阵 python 3.12/3.13）

- 契约断言：13 码全表（code/标识/文案/HTTP 映射）+ 信封形状逐字节 + ndjson 字段级 + message 追加规则；
- 中间件链：traceId 三级入口（traceparent→X-Trace-Id→生成）+ 回显 + 异常→1000 + 幂等同键回放/异参 1007 + 限流 1006；
- web：校验失败→1001 / 请求体格式错→1002（RequestValidationError 分型）、分页默认 20 上限 100、越界 D6 空页；
- 行为级（testcontainers 真 PG/Redis）：分页下推 + D6 真 total、逻辑删除不物理删、审计填充、key 首段=服务名、锁互斥/token 释放、幂等三态流转；
- httpx：traceparent 注入、下游业务码透传、超时→1008、连接失败/非信封→1009、超时强制截断；
- celeryx 装配件：global_keyprefix/队列路由/显式命名/json-only（改 pickle 即抛错）/soft+hard 超时/重试上限退避/beat 单实例锁/trace 头继承/失败落库 SPI——fake broker 单元断言 + TC Redis 真 broker 行为级；
- 生成后冒烟端到端（python-stack.yml）：生成 → uv sync → ruff/lint-imports → pytest → uvicorn 探活 `code:0`。

## 机检（python-stack.yml，与 java/golang 同强度）

`uv run ruff check . && uv run ruff format --check . && uv run lint-imports && uv run mypy && uv run pytest`（pytest 含 integration：testcontainers 起 PG/Redis 容器）。

## 本机开发备注（macOS + OrbStack）

- 行为级测试需 Docker：OrbStack 起docker 即可（testcontainers 自动探测本机 docker socket）；
- **`uv sync` 须带 `--all-packages`**（uv 0.11 语义：裸 sync 只装 workspace 根，成员包不进环境）；
- TC 用 testcontainers 4.15 community 模块（`testcontainers[postgres,redis]`，lock 已钉 4.15.0；对偶 golang/java 栈容器基座，测试态专用）。
