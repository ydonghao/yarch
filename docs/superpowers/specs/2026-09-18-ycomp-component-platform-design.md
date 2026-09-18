# ycomp 组件资产平台设计（2026-09-18）

> 状态：设计定稿（本对话四节逐节确认），待用户终审。本文是后续 `contract/web/component-library.md` 契约决策清单的底稿与三个子工程的施工总纲。
> 编号口径：CL* = 组件库契约决策清单项（成文前须正式拍板）；SP* = 子工程批次。

## 一、背景与目标

用户要在 yarch 规约体系之上建一个**前端组件资产平台**（重平台形态：登记 / 版本管理 / 在线预览 / 使用追踪），服务于个人名下各系统（ysaas、ybookreading、microduck 及后续工程）的构建复用。平台本身是 **yarch 首个完整 dogfood 消费工程**，同时充当 **rust 云栈的触发工程**。

**边界（明确不做）**：不做分发（npm `@yarch` org 是唯一分发通道）、不做构建流水线（预览零构建）、不做多租户/多贡献者（个人自用）、不做私有 registry、**不自建认证/用户体系（后续接 ysaas 平台 SSO，触发式——届时再立决策清单）**。

## 二、拍板记录（2026-09-18 本对话）

| # | 议题 | 结论 |
|---|---|---|
| D1 | 平台形态 | 组件资产平台（重平台）：登记 + 预览 + 同步 + 追踪 |
| D2 | 受众 | 个人自用；**无认证/用户体系（后续接 ysaas SSO，触发式）**、无多租户与权限分级 |
| D3 | 资产范围 | React 业务组件、Vue 业务组件、design token/主题包、页面模板/区块（四类全管） |
| D4 | UI 档关系 | 按框架各选生态：React → antd v5；Vue → ElementPlus。微前端「档唯一」仅约束单体系内部，不约束组件库 |
| D5 | 分发通道 | 公共 npm `@yarch` org；平台只管元数据/预览/检索，不碰分发 |
| D6 | 仓归属 | 组件包进 yarch `stacks/web/packages/`；平台独立仓，与 yarch 同级（`/Users/yuandonghao/sidejob/sources/ycomp/`），结构参考 ysaas（frontend/backend/docs/AGENTS.md） |
| D7 | 平台后端栈 | Rust（用户指定，兼作 rust 栈触发工程）；先施工 `stacks/rust` 批次一/二，模板/发版流水线（批次三）后置，平台以 git tag 依赖两 crate |
| D8 | 首批交付范围 | 闭环 + 自动同步 + 使用追踪（登记 → 同步 → 预览 → 追踪 → 总览全链路） |
| D9 | 平台技术核心 | 方案 A 轻运行时：元数据服务 + iframe 沙箱 esm.sh import map 运行时预览 + npm registry 轮询同步 + lockfile 扫描追踪 |

## 三、工程结构与命名

### 三个子工程

| 子工程 | 位置 | 内容 | 依赖 |
|---|---|---|---|
| SP0 · rust 栈施工 | yarch `stacks/rust/` | 批次一/二：契约内核 + axum 装配 + 中间件五件 + sqlx 装配 + cargo-generate 模板 | 无（前置） |
| SP1 · 组件库首批包 | yarch `stacks/web/packages/` | `@yarch/tokens` + `@yarch/pro-react` + 预览约定 | SP0 无关（可与 SP0 并行） |
| SP2 · ycomp 平台 | 独立仓 `sources/ycomp/` | rust 后端 + admin 前端 + 沙箱 + 同步/追踪 | SP0（crate 依赖）、SP1（预览货物） |

### ycomp 仓结构（对齐 ysaas）

```
sources/ycomp/
├── frontend/     # npm create @yarch/admin --ui antd 产出；页面：资产列表/包详情/预览沙箱/登记/同步管理/usage 总览
├── backend/      # cargo generate 产出的 rust 服务（DDD 七包）+ Dockerfile + .env(.example)
├── docs/         # requirements / architecture / plans / waivers / errno.md（3xxx 错误码段登记）
└── AGENTS.md     # yarch agent 规约同款工程守则
```

### 命名与登记（registry 校验通过，实施批次 0 正式登记）

- 服务名 `ycomp`（格式 `^[a-z][a-z0-9-]{1,31}$`、非裸通用词）→ registry 二节登记；共享 PG 新库 `ycomp`（库名=服务名，一应用一库）；**不启用** Redis（元数据服务无需缓存层）
- 前端应用名 `ycomp-console`（首段=已登记服务名）→ registry 四节登记；单应用非微前端（规约一-4「不得为微前端而微前端」）
- 组件包：`@yarch/tokens`、`@yarch/pro-react`（首批）；`@yarch/pro-vue`、页面区块包（二批）

## 四、SP0 · rust 栈最小施工范围

严格按 `stacks/rust/PLAN.md` 已拍板批次，平台需要什么先立什么：

1. **批次一**：cargo workspace 两 crate 空壳 + `yarch-contract`（response/errcode/trace 三模块）+ 13 码全表断言（同源读 `contract/dist/error-codes.json`，对齐四栈 conformance）——`cargo test` 绿。
2. **批次二**：`yarch-axum` 中间件五件（Trace/Recovery/AccessLog/Idempotency/Rate，对照 python `test_middleware_composition.py` 逐条翻译）+ sqlx 装配（分页/逻辑删除）+ `templates/service/`（DDD 七包 + users 示例）。
3. **批次三后置**：crates.io 发版流水线、MSRV 双档矩阵不挡平台——平台以 git tag 依赖引用两 crate，发版后切 crates.io。
4. CI 基本盘（cargo test / clippy -D warnings / rustfmt --check）在 SP0 内即须绿，否则 DoD 不成立。

## 五、SP1 · 组件库首批包与预览约定

### `@yarch/tokens`（框架无关）

- 载体：CSS custom properties + 机器可读 `tokens.json`（色板/字体/间距/圆角/阴影 + 暗色模式集，token 心法对齐 HI UI 5.0 经验）+ TS 常量导出。
- 定位：web/小程序/游戏三端共享 design token 的落点，接 clients 规约「跨形态复用走共享层（design token）」条文。

### `@yarch/pro-react`（antd v5 基线）首批 CRUD 三件套

- **ProTable**：查询表单 + 表格 + 分页一体，直接消费 `@yarch/contract` 的 `ListData<T>` 分页信封。
- **ProForm**：查询/编辑表单；错误码 → 校验态映射。
- **useApiQuery / useApiMutation**：信封解包 / 错误码 / traceId 透传的 React hooks。
- 每组件携带 **demo manifest**（预览约定载体，候选形态见 CL1）。
- 发版走既有 tag→npm 管道（web-publish.yml 同款链路）。
- 模板侧后置一步：`create-admin` antd 档预装 `@yarch/pro-react`（新工程开箱即用）。

### 预览约定（新契约 `contract/web/component-library.md` 核心条文候选）

- demo manifest（CL1 定形态）：每个资产包自带可预览元数据（demo 列表：标题/描述/源码/入口），平台沙箱统一解释。
- tokens.json schema（CL2 定结构）：tokens 类资产的机器可读约定，平台通用渲染器直接消费。
- import map / esm.sh 属平台实现细节，**不入条文**（条文只约束资产包自带什么，不管平台怎么渲染）。

## 六、SP2 · ycomp 平台系统设计（方案 A）

### 形态与部署

- rust axum 单体（cargo generate 产出，DDD 七包）+ sqlx + 共享 PG 库 `ycomp`。
- **单二进制**：rust-embed 内嵌 frontend/dist（tower-http ServeDir + history 路由 fallback），systemd 或 docker 部署 yuandonghao-linux；Dockerfile 多阶段（cargo release 构建 + 静态资源 + slim runtime）。
- **无认证暴露面**：服务只绑内网地址或反代限内网访问（不公网裸奔）；ysaas SSO 接入前保持此口径。
- 观测 = tracing + ndjson（rust 栈中间件自带 AccessLog/Trace），prometheus 触发式后置。

### 数据模型（postgresql.md 口径：snake_case/单数表/`is_` 布尔/timestamptz/逻辑删除）

| 表 | 要点 |
|---|---|
| `package` | npm_name 唯一；kind（component/token/block）；framework（react/vue/none）；ecosystem（antd/element-plus/none）；title/description/repo_url |
| `package_version` | version、dist_tag、manifest jsonb、readme_md、published_at、synced_at |
| `consumer_repo` | name、git_url、branch、is_active、last_scan_at、last_scan_status |
| `package_usage` | 当前态使用关系（repo × 包 × 锁定版本 + 来源 package.json/lockfile），扫描 upsert |
| `sync_run` / `scan_run` | 两 job 审计流水（起止/增量/错误） |

### API（REST 四件套口径：信封 + 错误码 + traceId；错误码 3xxx 段登记 ycomp/docs/errno.md）

- 认证：**无**（见一、边界；后续 ysaas SSO 接入为触发项）。
- 包：`POST /api/v1/packages`（登记 npm 包名，首次即拉元数据）、`GET /api/v1/packages`（kind/framework/关键词筛选，keyset 分页）、`GET /api/v1/packages/{id}`、`GET /api/v1/packages/{id}/versions`、`GET .../versions/{version}`。
- 仓：`POST /api/v1/repos`、`GET /api/v1/repos`、`GET /api/v1/repos/{id}`（含 usage）。
- 触发：`POST /api/v1/syncs/npm`、`POST /api/v1/scans/repo`（手动按钮同 job 逻辑）。
- 总览：`GET /api/v1/usage/overview`（包 × 系统 × 锁定版本矩阵——升级影响面板）。
- `GET /healthz`。

### 同步 job（tokio 定时小时级 + 手动）

登记包名集合 → `GET registry.npmjs.org/{name}` → diff versions vs 库 → 落 `package_version` + 拉 README；幂等 upsert；reqwest + tracing。

### 追踪 job（日级 + 手动）

浅 clone 各 active consumer_repo（https + PAT 环境变量；clone 缓存挂卷避免全量重拉）→ 解析 package.json（直接依赖）+ pnpm-lock.yaml（锁定版本）中的 `@yarch/*` → upsert `package_usage` + `scan_run` 流水。

### 预览沙箱（平台灵魂）

- 前端路由 `/preview/:pkg/:version/:demo` → iframe `sandbox="allow-scripts"`（禁同源、无 cookie 访问）。
- iframe 内动态生成 import map：react/react-dom/antd/dayjs 按**该版本 manifest 的 peerDeps** 解析到 esm.sh 固定版本 → import 包的 demos 入口 → 渲染 demo。
- tokens 类资产走平台内置通用 token 渲染器（直接读 tokens.json，无需运行时）。
- 降级：esm.sh 不可达 → 预览页报错 + npmjs 外链，元数据功能不受影响。
- 预览对象是自家公共 npm 包，攻击面小；sandbox 属性兜底。

### 平台前端自举

`npm create @yarch/admin --ui antd` 产出（布局/CRUD 范式现成；**模板登录流剔除**——暂无认证，接入路由直进），console 自身消费 `@yarch/pro-react` 管理包元数据——组件库第零号消费者，狗粮闭环。

## 七、组件库契约决策清单（成文前须正式拍板）

| # | 议题 | 倾向 |
|---|---|---|
| CL1 | demo manifest 形态 | 包导出 `demos` 入口（含源码字符串，沙箱运行时读取）vs 根目录 `demos/` 随包发布文件；倾向导出入口（类型安全 + 无文件 IO） |
| CL2 | tokens.json schema | 分组结构（color/typography/spacing/radius/shadow + 暗色模式集）与命名口径 |
| CL3 | 包命名定稿 | `@yarch/pro-react` / `@yarch/tokens` / 二批 `@yarch/pro-vue`；区块包命名后议 |
| CL4 | 预览约定条文边界 | manifest/tokens schema 入条文；import map/esm.sh 留实现细节 |
| CL5 | 模板预装策略 | `create-admin` antd 档预装 `@yarch/pro-react` 的版本锚定方式 |
| CL6 | 基线库锁定条文 | React=antd v5、Vue=ElementPlus 是否入 `component-library.md` 成文（延伸 W1 UI 档口径） |

## 八、施工顺序与 DoD

```
批次 0：本文终审 → registry 登记（ycomp / ycomp-console）+ README 决策表
批次 1（SP0-一）：yarch-contract 内核 + 13 码断言        DoD：cargo test 绿
批次 2（SP0-二）：yarch-axum 五中间件 + sqlx + 模板       DoD：cargo test/clippy/rustfmt 绿 + 模板生成冒烟
     ∥（SP1）：CL1-CL6 拍板 → component-library.md 成文 → tokens + pro-react 三件套
                                                          DoD：pnpm test 绿 + 包可发版
批次 3（SP2-骨架）：cargo generate ycomp 仓 + PG 库 + 迁移          DoD：healthz + 包登记 API 闭环
批次 4（SP2-API/jobs）：包/仓/版本 API + 同步 job + 追踪 job        DoD：集成测试（真实 PG）绿
批次 5（SP2-前端/沙箱）：console 页面 + 预览沙箱 + usage 总览       DoD：端到端剧本绿
批次 6（部署）：单二进制内嵌前端 → yuandonghao-linux 上线           DoD：curl healthz + 预览可访问
```

端到端验收剧本：登记 `@yarch/pro-react` → 同步出版本 → 预览能渲染 ProTable demo → 登记 ysaas 仓 → 扫描出 usage → 总览面板可见锁定版本。

## 九、风险与观察哨

| 风险 | 预案 |
|---|---|
| esm.sh 第三方 CDN 可用性 | 降级不致命（预览报错 + 外链）；观察哨：连续不可用再评估自举 CDN 或快照方案（方案 B 退路） |
| rust 栈施工超预期（async 上下文/tower 版本矩阵） | PLAN 已列；平台后端如被阻塞可临时以更薄手写 axum 骨架先行、回头对齐（须登记 waiver） |
| 追踪 job clone 凭证管理 | PAT 走环境变量；只读最小权限 token |
| 服务无认证被公网暴露 | 只绑内网/反代限内网；接入 ysaas SSO 前不公网开放 |
| sqlx 离线 CI 校验 | 预置 `.sqlx/` 快照（PLAN 七-1） |
| 组件库与平台互相等待 | SP1 与 SP0 并行；沙箱开发期可用本地 mock manifest 先行 |
