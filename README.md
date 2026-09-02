# yarch

> **Y**uan's **Arch**itecture —— 一种规范，多种方言。

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-16%20modules%20%E2%9C%85-brightgreen.svg)](stacks/java/)
[![Web](https://img.shields.io/badge/Web-4%20packages%20%E2%9C%85-brightgreen.svg)](stacks/web/)
[![Contract](https://img.shields.io/badge/Contract-24%20specs%20v1.0-gold.svg)](contract/)

跨项目复用的**工程架构平台**——不是又一个 CRUD 框架，而是让多个技术栈说同一种接口语言的契约体系。

## 为什么

| 痛点 | yarch 的答案 |
|---|---|
| 每个 API 长得不一样 | 统一信封 `RestResponse` + 错误码段位表，任何栈报出同一个 `code` 语义唯一 |
| 排障跨栈拉不齐 | ndjson 日志 + traceId 贯穿（W3C traceparent），Java/Go/Rust/TS 一条链拉通 |
| 规约靠自觉、CR 靠人肉 | 契约断言测试 + ArchUnit / depcruise 机检——违反即测试失败 |
| 脚手架 fork 改码后升级困难 | 平台构件发版式升级，业务工程只改一行 version |
| AI 生成代码结构漂移 | 模板管结构、契约管行为，AI 施工不越界 |

## 快速体验

```bash
git clone https://github.com/ydonghao/yarch.git
cd yarch
```

<details>
<summary><b>Java —— 生成一个 DDD 工程</b></summary>

```bash
# 安装平台构件到本地 Maven 仓库
cd stacks/java && mvn install

# 生成工程（DDD 七包 / 五层贫血 双档可选）
mvn archetype:generate -B \
  -DarchetypeGroupId=io.github.yuandonghao \
  -DarchetypeArtifactId=yarch-archetype-ddd \
  -DarchetypeVersion=0.1.0-SNAPSHOT \
  -DgroupId=com.example -DartifactId=my-svc \
  -Dpackage=com.example.mysvc

# 一键跑通
cd my-svc
docker compose up -d     # PG17 + Redis7
mvn spring-boot:run      # Flyway 迁移 + 示例 CRUD
```

生成即合规：信封 / traceId 贯穿 / 分页 / 逻辑删除 / 幂等 / ArchUnit 分层机检，全部预置。
</details>

<details>
<summary><b>Web —— 三档 UI 模板</b></summary>

```bash
cd stacks/web && pnpm install

# antd / Semi / Arco 三档，任选一个
cd templates/admin-antd && npx vite
```

三档共享 `@yarch/contract` 契约包（信封解包 / 错误码 / traceId），只换 UI 薄壳。
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

## 仓库结构

```
yarch/
├── contract/          # 契约层（唯一权威来源，24 份 v1.0 定稿）
│   ├── api/           #   四件套：信封 · 错误码 · 日志/traceId · REST 约定
│   ├── infra/         #   20 份：PG · MySQL · Redis · Kafka · MQ · 向量 · OLAP · 网关 · …
│   └── registry.md    #   服务名（租户边界）唯一登记处
│
├── stacks/            # 栈实现层（发各自生态的包）
│   ├── java/          #   ✅ 16 模块：parent/BOM + 8 starter + 双 archetype + 双 examples
│   ├── web/           #   ✅ 4 包 + 3 档模板：contract + react/vue 适配 + antd/Semi/Arco
│   ├── golang/        #   ✅ 3 module：契约内核 + 六构件 + DDD 模板
│   └── rust/          #   预留
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
| `templates/admin-antd` | 默认档（Vite + React + antd） |
| `templates/admin-semi` | Semi Design 档（抖音系） |
| `templates/admin-arco` | Arco Design 档（字节系） |
| `examples/admin-demo` | 全链路对接 Java 后端（验证码/分页/幂等/状态机） |

### 机检三件（违反即测试失败）

| 层 | Java | Web |
|---|---|---|
| 契约断言 | 错误码 13 码逐码核对 + 信封形状 + ndjson 字段 | 错误码表逐码核对 + 解包语义 |
| 架构守卫 | ArchUnit（DDD 依赖倒置 / 五层单向） | depcruise（pages 薄入口 / 禁反向 / contract 零框架） |
| 代码风格 | Spotless AOSP 4 空格（check 挂 verify） | biome（规范单点） |

## 契约与实现的关系

```
contract/（唯一权威，语言无关）
    ↓ 每栈一份方言实现
stacks/java    → RestResponse<T> · GlobalErrorCode · TraceIdFilter
stacks/web     → RestResponse<T> · errorCodes     · trace-id.ts
stacks/golang  → response.Response · errcode.Code  · middleware.Trace()
                   ↑
         实现与契约不一致 = bug（两侧均有防漂移断言）
```

## 状态

- **契约层**：24 份 v1.0 定稿（api 四件套 + infra 20 份 + registry）
- **Java**：16 模块 reactor verify 全绿 · CI（JDK 21/25）
- **Web**：contract 4/4 · depcruise 0 违规 · tsc+vite build ×3 绿 · CI（Node 20/22）
- **Golang**：3 module 全绿
- **发版**：暂走 git clone + `mvn install`；JitPack / Maven Central / npm 随 0.1.0 稳定后

## License

[Apache-2.0](LICENSE)
