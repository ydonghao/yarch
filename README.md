# yarch

> **Y**uan's **Arch**itecture —— 一种规范，多种方言。 · [English](README.en.md)

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![java-stack](https://github.com/ydonghao/yarch/actions/workflows/java-stack.yml/badge.svg)](https://github.com/ydonghao/yarch/actions/workflows/java-stack.yml)
[![python-stack](https://github.com/ydonghao/yarch/actions/workflows/python-stack.yml/badge.svg)](https://github.com/ydonghao/yarch/actions/workflows/python-stack.yml)
[![web-stack](https://github.com/ydonghao/yarch/actions/workflows/web-stack.yml/badge.svg)](https://github.com/ydonghao/yarch/actions/workflows/web-stack.yml)
[![golang-stack](https://github.com/ydonghao/yarch/actions/workflows/golang-stack.yml/badge.svg)](https://github.com/ydonghao/yarch/actions/workflows/golang-stack.yml)
[![Contract](https://img.shields.io/badge/Contract-25%20specs%20v1.0-gold.svg)](contract/)

跨项目复用的**工程架构平台**——不是又一个 CRUD 框架，而是让多个技术栈说同一种接口语言的契约体系。

## 为什么

| 痛点 | yarch 的答案 |
|---|---|
| 每个 API 长得不一样 | 统一信封 `RestResponse` + 错误码段位表，任何栈报出同一个 `code` 语义唯一 |
| 排障跨栈拉不齐 | ndjson 日志 + traceId 贯穿（W3C traceparent），Java/Go/Python/TS 一条链拉通 |
| 规约靠自觉、CR 靠人肉 | 契约断言测试 + ArchUnit / depcruise 机检——违反即测试失败 |
| 脚手架 fork 改码后升级困难 | 平台构件发版式升级，业务工程只改一行 version |
| AI 生成代码结构漂移 | 模板管结构、契约管行为，AI 施工不越界 |

## 从 0 到 1：一条命令生成工程

四个栈同一套契约，全部**生成即合规**——统一信封 · 13 码错误码 · traceId 贯穿 · 分页（页码 D6 + keyset 游标）· 幂等 · 逻辑删除 · 机检预置，业务代码只写业务。

### Java · Maven archetype（零 clone，Central 已发版）

前置：JDK 21+、Docker。

```bash
# ① 生成 DDD 七包工程（换 -DarchetypeArtifactId=yarch-archetype-simple 即阿里五层档）
mvn archetype:generate -B \
  -DarchetypeGroupId=io.github.ydonghao \
  -DarchetypeArtifactId=yarch-archetype-ddd \
  -DarchetypeVersion=0.1.0 \
  -DgroupId=com.example -DartifactId=my-svc \
  -Dpackage=com.example.mysvc

# ② 起跑
cd my-svc
docker compose up -d      # PG17 + Redis7（生成工程自带）
mvn spring-boot:run       # Flyway 自动迁移 + 示例 CRUD → http://localhost:8080
```

可选验收：`mvn verify` 跑 ArchUnit 分层机检 + 契约断言 + Testcontainers 全链路。

### Web · npm create（零 clone，npm 已发版）

前置：Node 20+、pnpm。

```bash
# ① 生成中后台工程（三档 UI 任选：Semi 默认 · 抖音系 / antd · 蚂蚁系 / arco · 字节系）
npm create @yarch/admin@latest ysaas-console               # Semi 档（默认）
npm create @yarch/admin@latest ysaas-console -- --ui antd  # 换 antd（arco 同理）

# ② 起跑
cd ysaas-console && pnpm install && pnpm dev   # → http://localhost:5173
```

比裸脚手架多给：契约底座预接线（信封解包 / traceId / 401 跳登录）· 工程名强制 registry 标准 · 底座 npm 版本化升级 · feature-first 分层机检。完整说明书：[stacks/web/packages/create/README.md](stacks/web/packages/create/README.md)

### Golang · yarch-init 生成器（clone 本仓一次；发版后零 clone）

前置：Go 1.24+；PG/Redis 可达（共享实例，或本机 Docker）。

```bash
# ① 生成 Hertz DDD 工程（服务名过 registry 校验：小写短横线、禁裸通用词）
git clone https://github.com/ydonghao/yarch.git
cd yarch/stacks/golang
go run ./cmd/yarch-init -module github.com/you/order-svc -out ~/code/order-svc

# ② 起跑
cd ~/code/order-svc
cp .env.example .env && vi .env   # 填 PG/Redis 地址（主路径连共享实例：独立 database 自动建库+迁移，无需本地 compose）
docker compose up -d              # 没有共享实例时：本机起 PG17 + Redis7，.env 主机改 localhost
go mod tidy && go run .           # → http://localhost:8080
```

> 生成工程 go.mod 的 `replace` 行指向你 clone 的本仓（联调期平台构件走本地源码）；正式发版后删除该行、版本改正式 tag。

### Python · yarch-init 生成器（clone 本仓一次；发版后零 clone）

前置：uv、Python 3.12+；PG/Redis 可达（共享实例，或本机 Docker）。

```bash
# ① 生成 FastAPI DDD 工程（服务名过 registry 校验：小写短横线、禁裸通用词）
git clone https://github.com/ydonghao/yarch.git
cd yarch/stacks/python
uv sync --all-packages && uv run yarch-init --service order-svc --out ~/code/order-svc

# ② 起跑
cd ~/code/order-svc
cp .env.example .env && vi .env   # 填 PG/Redis 地址（web 启动自动建库 + Alembic 自动迁移）
uv sync && uv run uvicorn main:app --reload   # → http://localhost:8000/docs（users 示例 /api/v1/users）
uv run celery -A celery_app worker -Q order-svc.default   # ③ 需要异步任务时另进程 worker/beat
```

发版后（tag `stacks/python/vX.Y.Z` → PyPI trusted publishing）零 clone 一条命令：`uvx yarch-init@latest --service order-svc --out order-svc`。

<details>
<summary><b>升级：平台发版式，业务工程只改版本号</b></summary>

```bash
# Java：改 pom 继承的 yarch-parent 版本一行（BOM 仲裁全链版本）
# Web：pnpm update @yarch/contract @yarch/react
# Golang：go get github.com/ydonghao/yarch/stacks/golang@vX.Y.Z（发版后）
# Python：uv add "yarch-python@X.Y.Z"
```
</details>

<details>
<summary><b>契约速览</b></summary>

```json
{ "code": 0, "message": "成功", "data": { }, "traceId": "0af7651916cd43dd8448eb211c80319c" }
```

- `code`：0 成功 · 1xxx 通用 · 2xxx 认证 · 3xxx+ 业务注册
- 日志：ndjson 一行一条（`ts/level/service/env/traceId/logger/msg`）
- 分页：页码通道（D6 越界返回空页）+ 游标通道（keyset）
- 幂等：`Idempotency-Key` 头 + Redis `SET NX PX`
</details>

<details>
<summary><b>国内访问加速（换源，三栈三行）</b></summary>

镜像均为官方源的代理缓存（发布只发官方源，镜像自动同步；刚发版的版本可能延迟几分钟）：

```bash
# npm（web 栈）
npm config set registry https://registry.npmmirror.com

# Maven（java 栈）——写入 ~/.m2/settings.xml 的 <mirrors>
# <mirror><id>aliyun</id><mirrorOf>central</mirrorOf>
#   <url>https://maven.aliyun.com/repository/public</url></mirror>

# Go（golang 栈）
go env -w GOPROXY=https://goproxy.cn,direct
```
</details>

## 仓库结构

```
yarch/
├── contract/          # 契约层（唯一权威来源，25 份 v1.0 定稿）
│   ├── api/           #   四件套：信封 · 错误码 · 日志/traceId · REST 约定
│   ├── infra/         #   20 份：PG · MySQL · Redis · Kafka · MQ · 向量 · OLAP · 网关 · …
│   ├── web/           #   微前端规约（micro-app 默认档）
│   └── registry.md    #   服务名（租户边界）唯一登记处
│
├── stacks/            # 栈实现层（发各自生态的包）
│   ├── java/          #   ✅ 16 模块：parent/BOM + 8 starter + 双 archetype + 双 examples
│   ├── python/        #   ✅ uv workspace 双发行版：yarch-python 构件 + yarch-init 生成器（DDD 模板）
│   ├── web/           #   ✅ 5 包：contract + react/vue 适配 + create-admin 生成器（3 档模板资产）
│   ├── golang/        #   ✅ 3 module：契约内核 + 六构件 + DDD 模板
│   └── rust/          #   预留（触发式）
│
├── clients/           # mobile · miniprogram · desktop(Tauri) 按需生长
├── tools/             # locate-scaffolds → 将来 yarch init CLI
└── docs/              # 架构文档 + 对标解析（ruoyi/yudao/coli 缺口 G/E 决策清单）
```

## 已交付

### Java（`stacks/java/`）

| 构件 | 能力 |
|---|---|
| `yarch-parent` + `yarch-bom` | 构建基线 + 版本唯一仲裁（Boot 4.1 / JDK 25 编 21 / Spotless 4 空格） |
| `yarch-common` | 信封 / 13 码错误码表 / PageData / BusinessException / Asserts / Masks / TraceIds |
| `yarch-web-…` | 异常矩阵 / 分页 / **幂等** / **@RateLimited(1006)** / **@OperationLog** / **@SignedApi** |
| `yarch-logging-…` | **ndjson 行协议** / TraceIdFilter（traceparent 优先） |
| `yarch-persistence-…` | PG + MyBatis-Plus：**逻辑删除** / 审计填充 / **分页下推** / keyset 游标 |
| `yarch-redis-…` | **RedisKeys**（首段=服务名）/ JSON+jsr310 / **分布式锁** / 跨实例幂等 |
| `yarch-http-…` | **出口传播**：traceparent 注入 / 超时强制 / 信封解包 / 1008-1009 |
| `yarch-captcha-…` | 图形验证码 + Redis 一次性 token |
| `yarch-auth-…` | JWT + 2xxx 矩阵 + @RequireRoles |
| `yarch-test-…` | **契约断言** / ArchUnit 双规则集 / PG·Redis 容器基座 |
| `yarch-archetype-ddd` | DDD 七包（api/app/domain/crossdomain/infra/types/common） |
| `yarch-archetype-simple` | 阿里五层（controller/service/manager/dao/model） |
| `yarch-examples-×2` | 商品+订单双档活案例：幂等下单 / 锁竞争 / cache-aside / keyset / 状态机 |

### Web（`stacks/web/`）

| 包 | 能力 |
|---|---|
| `@yarch/contract` | 信封类型/解包 / 错误码常量表 / ApiError / traceId / fetch 封装 / 导航端口 |
| `@yarch/react` / `@yarch/vue` | 框架薄适配（注入各 router 导航） |
| `@yarch/create-admin` | **工程生成器**：交互问答 + archetype 全量渲染 + registry 命名校验（golang `yarch-init` 对偶） |
| `create/templates/admin-semi` | 默认档资产（Vite + React + Semi Design，抖音系） |
| `create/templates/admin-antd` | antd 档资产（蚂蚁系） |
| `create/templates/admin-arco` | Arco Design 档资产（字节系） |
| `examples/admin-demo` | 全链路对接 Java 后端（验证码/分页/幂等/状态机） |

### Python（`stacks/python/`）

| 构件 | 能力 |
|---|---|
| `yarch-python`（PyPI） | 契约内核三件（response/errcode/xerror）+ logx ndjson + trace/recovery/accesslog/幂等/限流中间件 + persist（逻辑删除/审计/分页 D6/Alembic）+ redix（key 规约/锁/幂等三态/固定窗口限流）+ httpx（traceparent 注入/1008·1009）+ celeryx（celery 规约强制默认）+ testx 契约断言 |
| `yarch-init`（PyPI） | 工程生成器：Jinja2 渲染 + registry 服务名校验 + 残留占位扫描；DDD 七包模板（users/celery 示例 + alembic + web/worker 双入口） |

### 机检（违反即测试失败）

| 层 | Java | Web | Python |
|---|---|---|---|
| 契约断言 | 错误码 13 码逐码核对 + 信封形状 + ndjson 字段 | 错误码表逐码核对 + 解包语义 | testx：13 码全表 + 信封/ndjson/PageData（跨栈 conformance 同表） |
| 架构守卫 | ArchUnit（DDD 依赖倒置 / 五层单向） | depcruise（pages 薄入口 / 禁反向 / contract 零框架） | import-linter（契约内核零框架 + 禁反向依赖） |
| 代码风格 | Spotless AOSP 4 空格（check 挂 verify） | biome（规范单点） | ruff check + format + mypy |

## 契约与实现的关系

```
contract/（唯一权威，语言无关）
    ↓ 每栈一份方言实现
stacks/java    → RestResponse<T> · GlobalErrorCode · TraceIdFilter
stacks/web     → RestResponse<T> · errorCodes     · trace-id.ts
stacks/golang  → response.Response · errcode.Code  · middleware.Trace()
stacks/python  → response.Response · errcode.Code  · middleware.TraceMiddleware()
                   ↑
         实现与契约不一致 = bug（两侧均有防漂移断言）
```

## 状态

- **契约层**：25 份 v1.0 定稿（api 四件套 + web 微前端 + infra 20 份 + registry）
- **Java**：16 模块 reactor verify 全绿 · CI（JDK 21/25）
- **Web**：contract 4/4 · depcruise 0 违规 · 生成后冒烟三档全绿（生成 → install → tsc → build）· CI（Node 20/22）
- **Golang**：3 module 全绿
- **Python**：99 tests 全绿 · CI（Python 3.12/3.13，含生成工程冒烟）
- **发版**：Java 13 件 0.1.0 已上 Maven Central（2026-09-03，`archetype:generate` 零 clone 即用；后续推 tag `stacks/java/vX.Y.Z` 走 `java-publish.yml`）；Web 三包 0.1.0 已上 npm（`npm create @yarch/admin@latest` 即用；后续推 tag `stacks/web/vX.Y.Z` 走 CI 发版）；Golang tag 发版路径就绪（`stacks/golang/vX.Y.Z` → module proxy，无需注册任何平台），**tag 待推送**——当前生成器随本仓使用（见上方从 0 到 1）；Python PyPI trusted publishing 就绪（`stacks/python/vX.Y.Z`），**tag 待推送**——当前从本仓跑生成器（见上方从 0 到 1）

## License

[Apache-2.0](LICENSE)
