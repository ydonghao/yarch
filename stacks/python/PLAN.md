# stacks/python · yarch-python 脚手架策划案

> **状态：策划案定稿（2026-09-07），P1-P14 已拍板**（P1-P4 交互拍板，P5-P14 随整案按推荐值通过），待开工。
> 输入：[contract/](../../contract/README.md) 24 份定稿规约（唯一权威，celery.md 为本栈启动触发）· [docs/architecture.md](../../docs/architecture.md)（FastAPI+DDD 既定、全域治理护栏）· [golang PLAN](../golang/PLAN.md) 与 web `@yarch/create-admin` 脚手架范式（三栈同构基准）。
> 注：本文件 P1-P14 指 python 栈决策编号，与 postgresql.md 的 G1-G10、golang 栈 G1-G9 无关。
> PyPI 命名核实（2026-09-07）：`yarch` / `yarch-python` / `yarch-init` 均未占用。

## 一、定位与既定约束（来自 architecture.md 与 contract，本策划不再议）

1. yarch-python = Python 栈平台构件（uv workspace 双发行版），经 PyPI 分发；业务工程 `uv add yarch-python` 引入，版本步进自动跟进。
2. 位置 `stacks/python/`，`requires-python >=3.12`；发布 tag `stacks/python/vX.Y.Z`（trusted publishing，见 P14）。
3. 铁律：不含任何业务语义；实现与 contract/ 不一致即 bug。
4. 术语对照（契约锁定，模块名必须命中）：`yarch_python.response.Response` / `yarch_python.response.PageData` / `yarch_python.errcode.Code` / `yarch_python.xerror.BizError` / `yarch_python.middleware` / `yarch_python.logx`（structlog）。
5. 分层：DDD 七包同构 coze-studio（api / application / domain / crossdomain / infrastructure / types + 双入口）；手写组装根分阶段（basic→primary→complex）；`infrastructure` port/adapter 分离（repo 抽象用 `typing.Protocol`）。
6. 启动触发：**celery 规约承接**（architecture.md 六-5"python 先行——celery 规约在等承接"）——`celeryx` 进第一批（P4）。
7. 治理护栏兑现：CI 全绿 + 一行命令起工程 + 发版链路活（architecture.md 六-6）。

## 二、与 golang / web 脚手架范式的对齐（三栈同构基准）

| 维度 | golang | web | **python（本栈）** |
|---|---|---|---|
| 工程形态 | 单 module 多 package | pnpm workspace 三包 | **uv workspace 双发行版** |
| 模板资产 | `_template/` + archetype.json | `packages/create/templates/` 三 UI 档 | **`yarch_init/_template/`（随 wheel 携带）+ archetype.json** |
| 生成器 | `cmd/yarch-init`（text/template 渲染） | `@yarch/create-admin`（交互问答） | **`yarch-init`（Jinja2 渲染 + registry 校验）** |
| 一行命令 | `go run …/cmd/yarch-init@tag` | `npm create @yarch/admin@latest` | **`uvx yarch-init@latest`** |
| 发版 | tag → Go module proxy | tag → web-publish.yml → npm 三包 | **tag → python-publish.yml → PyPI 双件（OIDC trusted publishing）** |
| 机检 | golangci-lint depguard + testx + testcontainers | depcruise + biome + vitest | **ruff + import-linter + mypy + pytest + testcontainers（P12）** |

> 模板均为**声明式资产**（占位 + archetype.json 变量声明，不要求自身可运行），工程正确性由 CI「生成后冒烟」保证——三栈同一铁律。

## 三、目标形态（workspace 全景）

```
stacks/python/                                    # uv workspace（pyproject 根 + uv.lock；requires-python >=3.12，CI 3.12/3.13）
├── PLAN.md / README.md / architecture-diagram.svg
│
├── packages/yarch-python/                        # ① 平台构件发行版（PyPI: yarch-python → import yarch_python）
│   ├── pyproject.toml                            #    运行时依赖全量必选；testcontainers 走 [test] extra（P10）
│   └── src/yarch_python/
│       ├── response/                             #    契约内核①：Response[T]/PageData[T]/PageQuery（零框架依赖）
│       ├── errcode/                              #    契约内核②：Code + 13 码全表 + HTTP 映射 + 业务码注册
│       ├── xerror/                               #    契约内核③：BizError（message「默认文案：细节」）
│       ├── logx/                                 #    structlog ndjson（ts/level/service/env/traceId/logger/msg）+ contextvars
│       ├── middleware/                           #    Trace 三级入口/Recovery→1000/AccessLog/Idempotency→1007/RateLimit→1006
│       ├── web/                                  #    setup() 一行装配 + 信封回包 + 1001/1002 分型 + 分页绑定(D6)
│       ├── persist/                              #    SQLAlchemy 2.x + psycopg3：逻辑删除/审计/分页下推 + Alembic 辅助
│       ├── redix/                                #    Keys(首段=服务名)/JSON Cache/Lock/Idempotency/RateLimiter(Lua)
│       ├── httpx/                                #    下游客户端：超时强制≤30s + traceparent 注入 + 信封解包 + 1008/1009
│       ├── celeryx/                              #    celery 装配件：celery.md 强制默认一次性封死（见第五节）
│       └── testx/                                #    契约断言（13 码/信封/ndjson，跨栈 conformance 同表）+ TC PG/Redis 基座
│
└── packages/yarch-init/                          # ② 工程生成器发行版（PyPI: yarch-init，console_scripts: yarch-init）
    ├── pyproject.toml                            #    仅依赖 jinja2
    └── src/yarch_init/
        ├── main.py                               #    渲染引擎 + registry 服务名/裸词校验（golang cmd/yarch-init 对偶）
        └── _template/                            #    模板资产随 wheel 携带（{{ var }} 占位 + archetype.json）
```

依赖方向纪律（import-linter 机检固化）：契约内核三件零框架依赖；构件间禁止反向依赖（对偶 golang depguard 模板侧同步）。

## 四、生成工程脚手架（`yarch-init` 渲染产物，DDD 七包 python 化）

```
ysaas-scan/                                       # 目录名 = 服务名（registry 校验过）
├── main.py                                       # Web 入口：组装根装配 → uv run uvicorn main:app
├── celery_app.py                                 # Worker/Beat 入口：celery -A celery_app worker -Q ysaas-scan.default
├── pyproject.toml                                # uv 工程：fastapi + yarch-python + dev 组（pytest/ruff/mypy/import-linter）
│                                                 #   发版前 tool.uv.sources 指向本机平台源码（对偶 golang replace 行，README 注明发版后删）
├── api/{router.py, handler/users.py, model/user.py}          # ① 接口层（/api/v1 前缀，D5）
├── application/app.py                            # ② 手写分阶段组装根（basic→primary→complex）
├── domain/{entity/user.py, repository/user.py}   # ③ 领域层：实体不碰 ORM，repo port 用 typing.Protocol（零框架依赖）
├── infrastructure/database/{models.py, user_repo.py, migrations/}   # ④ adapter + Alembic 纯 SQL 版本化（禁 create_all）
├── crossdomain/ · conf/ · pkg/                   # ⑤⑥⑦ 占位 README（域间防腐/配置说明/工程内共享）
├── types/errno.py                                # 业务码 3xxx+ 段位（登记后方可使用，生成器成功提示点名）
├── tasks/users_task.py                           # celery 示例任务：ysaas-scan.users.sync（幂等演示）
├── tests/                                        # TestClient 冒烟 + testx 信封/分页断言 + TC 行为级示范
├── .env.example                                  # 共享实例隔离模式：独立 database + key 前缀=服务名（起跑主路径免 compose）
├── .python-version · .gitignore · Makefile（dev/worker/beat/test/lint/migrate）
├── Dockerfile                                    # uv 基镜像多阶段；单镜像双命令（web/worker 分离部署，celery.md 五-4）
└── README.md                                     # 渲染服务名的起跑三步 + 登记/升级指引
```

起跑主路径（对齐 golang 模板"共享实例隔离 + 自动建库"）：

```bash
cd ysaas-scan && cp .env.example .env && vi .env   # 填共享 PG/Redis 地址
uv sync && uv run uvicorn main:app --reload        # 启动自动建库 + Alembic 自动升级 → :8000/api/v1/users
uv run celery -A celery_app worker -Q ysaas-scan.default   # worker/beat 另进程
```

关键设计：

1. **双入口分离**：`main.py`（web）与 `celery_app.py`（worker/beat）两进程共享 domain/application；生产按队列专享部署（celery.md 四-1、五-4）。
2. **domain 纯净度**：实体不 import SQLAlchemy、repo 以 Protocol 定 port；import-linter 在生成工程预配置契约（domain 禁依赖 infra/api、api 禁直依赖 infra）——机检随工程初始化即生效。
3. **服务名一次渲染全局封死**：目录名、Redis key 前缀（`redix.Keys`）、celery 队列/任务名/global_keyprefix、日志 service 字段，全部由 `--service` 派生，业务侧无第二个改漏机会。
4. **archetype.json 变量**：`service`（必填，registry 一-1 校验 + 裸通用词拒绝）、`description`、`yarch_version`（开发期渲染本机 path 源，发版后改 tag）——与 golang/web 同一 schema。

## 五、契约 → 构件映射

API 四件套 + PG/Redis 逐行对偶 golang PLAN 第四节：

| 契约条文（出处） | 落点 | 机检 / 验收 |
|---|---|---|
| RestResponse 四字段 camelCase、code!=0 data=null、traceId=X-Trace-Id（rest-response.md） | response | pydantic 序列化断言（字段名/必填/形状） |
| 13 码全表 + 默认文案 + HTTP 映射 + 标识符驼峰（error-codes.md） | errcode | 全量断言（code/标识/message/HTTP），防漂移 |
| message「默认文案：细节」追加规则（error-codes.md 实现规则-1） | xerror | 断言 |
| ndjson 行协议字段（logging-trace.md） | logx | 捕获输出逐行断言 |
| traceId 入口（traceparent→X-Trace-Id→生成）/ 回显 / contextvars 贯穿 | middleware.trace | TestClient 集成断言 |
| 校验失败→1001/400、请求体格式→1002/400（error-codes + rest-conventions） | web | RequestValidationError 分型 + json decode 单独捕获，集成测试 |
| 分页默认 20 上限 100、越界空页 D6、PageData{list,total,page,pageSize,nextCursor?}（rest-conventions） | web + persist | TC PG 集成：越界空 list + 真实 total |
| 幂等 Idempotency-Key：同键回放/异参 1007/TTL≥24h（rest-conventions 幂等总则-1） | middleware.idempotency + redix | TC Redis 集成测试 |
| PG 逻辑删除 is_deleted / 审计填充 / 迁移版本化（postgresql.md 五·六） | persist + 模板 migrations | TC PG 行为级测试 |
| Redis key 首段=服务名 / JSON 值（redis.md） | redix.Keys | 断言 key 形状 |
| 出口传播：traceparent 注入 + 超时强制 + 信封解包 + 1008/1009（logging-trace 传播矩阵 + A7） | httpx | 下游桩集成 |
| 状态码白名单 200/201/400/401/403/404/409/429/500/503/504（rest-conventions） | web | 断言 |

**celery.md 逐条承接（python 独有增量，celeryx 装配件）**：

| celery.md 强制条文 | celeryx 落点 |
|---|---|
| broker global_keyprefix = `{服务名}:`（二-1） | 装配默认值由服务名派生，业务零配置即合规 |
| 队列 `{服务名}.{用途}` 显式路由、禁默认单队列（二-2） | 命名函数 + 路由登记器，装配层拒绝无路由任务 |
| task 命名 `{服务名}.{模块}.{动作}` 显式 name=（二-3） | 任务注册基类强制显式命名，禁自动生成名 |
| json-only、pickle 全局禁（三-5） | 配置封死，试图改 pickle 即抛错 |
| 幂等是默认假设、至少一次交付（三-1） | 幂等键助手基件（业务键/唯一约束去重，上位口径 rest-conventions 幂等总则） |
| soft/hard_time_limit 必配、禁无限执行（三-2） | 装配默认值 + 未配即拒绝启动 |
| max_retries≤5 + 指数退避抖动（三-3） | 重试策略工厂 + 上限校验 |
| 失败有终点：重试耗尽落库告警（三-4） | errback/信号 → 失败任务表 SPI（异步投递失败不阻断，对齐 golang oplog 模式） |
| beat 单实例（四-3） | Redis 锁守卫件 |
| 任务继承调用方 traceId、周期任务每轮新建（四-4） | 任务头注入 + contextvars 桥，与 logx 同链 |

机检：fake broker 单元断言 + TC Redis 真 broker 行为级断言（与 redix 断言共享容器）。

## 六、决策清单（已拍板记录）

| # | 决策 | 拍板结论（2026-09-07） |
|---|---|---|
| P1 | 生成器形态 | **自研 `yarch-init`**（交互拍板）：Jinja2 渲染 + archetype.json 变量声明 + registry 服务名/裸词校验 + CI 生成后冒烟；不用 cookiecutter（与三栈 archetype.json 范式分叉、hook 校验不直白） |
| P2 | PyPI 命名 | **发行名 `yarch-python` / import `yarch_python`**（交互拍板）；`yarch-init` 归生成器；裸名 `yarch` 留给将来全栈统一 CLI（tools/locate-scaffolds 预留方向）。四名已核实可用 |
| P3 | 同步/异步 | **同步核心**（交互拍板）：SQLAlchemy sync Session + psycopg3 + `def` 端点（FastAPI 线程池）+ 同步 redis-py/httpx；Celery 天然同构；async 档（AsyncSession + asyncpg + redis.asyncio）触发式 |
| P4 | celery 批次 | **进第一批**（交互拍板）：装配件封死 celery.md 强制默认，启动触发一次兑现 |
| P5 | 工程形态 | **uv workspace 双发行版**（yarch-python + yarch-init，模板随生成器 wheel 携带）——golang 单 module 与 web workspace 的合体对偶 |
| P6 | 构件切分 | **一规约一 module**：response/errcode/xerror（契约内核组）/ logx / middleware / web / persist / redix / httpx / celeryx / testx |
| P7 | Web 底座 | **FastAPI + pydantic v2 + pydantic-settings**（.env 三环境 local/debug/release，对齐 golang G4）；OpenAPI 文档由 FastAPI 原生免费获得 |
| P8 | ORM 与迁移 | **SQLAlchemy 2.x + psycopg3 + Alembic 纯 SQL 版本化**；禁 `Base.metadata.create_all`（对齐 PG 规约六-1、golang G3） |
| P9 | 日志 | **structlog（stdlib 互操作）ndjson 行协议**，contextvars 承 traceId（对偶 golang slog logx）；uvicorn/access log 经 structlog formatter 合流 |
| P10 | 依赖形态 | 运行时依赖全量必选（不搞 extras 细分，slim 安装触发式再议）；testcontainers 走 `[test]` extra |
| P11 | 模板分发 | **单 ddd 档起步**（users 示例 CRUD + celery 示例任务 + alembic + 共享实例隔离起跑）；simple 档触发式（对齐 golang G6） |
| P12 | 机检第一批 | pytest 契约断言（13 码全表逐码，跨栈 conformance 同表）+ ruff（lint+format）+ **import-linter**（依赖方向，depguard 对偶）+ mypy + testcontainers-python 行为级 + GHA + 生成后冒烟（生成 → uv sync → ruff → pytest → uvicorn 探活） |
| P13 | 版本基线 | **`requires-python >=3.12`**（采用面最大，G8 同逻辑）+ CI **3.12/3.13 双矩阵**；工具链统一 uv |
| P14 | 发版 | `python-publish.yml`：**PyPI trusted publishing（OIDC，无 token/GPG）**，tag `stacks/python/vX.Y.Z` → 双发行版幂等发布（对偶 web-publish 三包；无 java Central 的 GPG 坑） |

一行命令两阶段（对偶 golang README 写法）：

```bash
# 发版前（模板随仓携带）
git clone https://github.com/ydonghao/yarch && cd yarch/stacks/python
uv run yarch-init --service ysaas-scan --out ~/code/ysaas-scan
# 发版后（零 clone）
uvx yarch-init@latest --service ysaas-scan --out ysaas-scan
```

## 七、分批施工清单

**第一批（本策划拍板后启动）**：

1. workspace 骨架（根 pyproject + 双 packages + uv.lock + .python-version）；
2. 契约内核三件（response / errcode / xerror）+ 契约断言；
3. logx（structlog ndjson + contextvars）；
4. middleware 五件（trace / recovery / accesslog / idempotency / ratelimit）；
5. web（setup 装配 + 信封 + 1001/1002 分型 + 分页绑定）；
6. persist（SQLAlchemy + psycopg3 + Alembic 辅助 + 逻辑删除/审计/分页下推）；
7. redix（keys / json cache / lock / idempotency / ratelimiter）；
8. httpx（超时 / traceparent / 信封解包 / 1008·1009）；
9. **celeryx（celery.md 强制默认装配件，行为级断言）**；
10. testx（契约断言 + TC PG/Redis 基座）；
11. yarch-init（渲染引擎 + registry 校验）+ `_template/`（DDD 七包 + users + tasks + alembic）；
12. `python-stack.yml`（全量机检 + 生成后冒烟 + 双矩阵 3.12/3.13）；
13. README + 架构图 + **随批登记**：stacks/README 状态行、docs/architecture.md 第三节与三·五、contract/README 术语对照表加 python 列、tools/locate-scaffolds.cjs 的 STACKS 表。

**触发式增长（一规约一 module，不做配置档）**：auth（JWT 2xxx，golang 第二批对偶件）、captcha、kafka、minio、async 档（AsyncSession + asyncpg）、simple 档模板、qdrant/es 等数据件。

## 八、开放问题与风险

1. **Jinja2 定界符与 Python 源码 `{{` 冲突**：模板源码规约避让（不出现裸 `{{`）+ CI 渲染即编译兜底；
2. **FastAPI 1001/1002 分型**：`RequestValidationError` 混装校验错与格式错，需按错误根部分拣（json decode 错误在 exception handler 之外单独捕获转 1002）；
3. **uvicorn/access log 与 structlog 合流**：uvicorn `log_config` 全量改走 structlog formatter，保证 ndjson 口径统一（logx 断言覆盖）；
4. **PyPI trusted publishing 首配**：pypi.org 账号 + 双项目 publisher 声明（一次性，无 token secret）；
5. 0.x 不承诺兼容；正式发版待第一批全绿；
6. 支持面 macOS + Linux（CI），Windows 不在矩阵；
7. celery 行为级断言依赖真 broker：TC Redis 起 broker，与 redix 断言共享容器，无额外开销。
