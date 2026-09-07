# @yarch/create-admin

> 一条命令生成**生成即合规**的中后台工程——契约底座、命名标准、结构纪律全部预置，三档 UI 任选。
> npm：[@yarch/create-admin](https://www.npmjs.com/package/@yarch/create-admin)（v0.1.0+）

## 优势：比裸脚手架（create-vite）多给什么

| 维度 | 裸脚手架 | 本生成器 |
|---|---|---|
| 接口层 | 空壳，信封/错误码自己搭 | `@yarch/contract` 预接线：RestResponse 信封解包 · 13 码错误码表 · traceId 透传 · 401 自动跳登录 |
| 工程名 | 随手起 | 强制 registry 标准（小写短横线 + 禁裸通用词）；工程名 = npm 包名 = 服务名，生成即登记提醒 |
| UI 库 | 自己选、自己集成 | **三档一键选**：Semi / antd / Arco——契约底座与结构不变，只换 UI 壳 |
| 升级 | 模板拷走即断亲 | 底座是 npm 版本依赖：`pnpm update @yarch/contract @yarch/react` 一行跟进平台修复 |
| 结构 | 分层靠自觉 | feature-first 分层 + depcruise 同款机检口径（pages 薄入口、禁反向依赖） |
| 跨栈 | — | 与 java / golang 栈同一套契约：一个 traceId 前后端拉通，错误码语义唯一 |

## 创建：三档 UI，各一条命令

```bash
# Semi 档（默认 · 抖音系）
npm create @yarch/admin@latest ysaas-console

# antd 档（蚂蚁系）
npm create @yarch/admin@latest ysaas-console -- --ui antd

# Arco 档（字节系）
npm create @yarch/admin@latest ysaas-console -- --ui arco
```

不传 `--ui` 则交互三选一（回车落默认 Semi）；交互同时问描述 / 端口 / API 代理目标 / GitLab 分组，全部有默认值。三档底座与目录结构完全一致，切换档位 = 换个名字重新生成。

## 起跑（两分钟）

```bash
cd ysaas-console
pnpm install
pnpm dev        # http://localhost:5173
pnpm build      # tsc --noEmit + vite build
```

- **接后端**：`vite.config.ts` 已预配 `/api/v1` 反向代理，改 target 即指向你的服务；
- **升级底座**：`pnpm update @yarch/contract @yarch/react`；
- **登记**：按生成后的控制台提醒，去 yarch 仓 `contract/registry.md` 登记服务名（PR 即登记）。

<details>
<summary><b>附录：交互问答明细 · flags 全表 · 命名规则 · 原理 · 发布（点开）</b></summary>

### 交互问答（6 项，回车走默认）

| # | 问题 | 默认 |
|---|---|---|
| 1 | 工程名（= npm 包名 = registry 服务名，须过校验） | 必填 |
| 2 | UI 档：1) Semi（默认）2) antd 3) Arco | semi |
| 3 | 工程描述 | `<工程名>：基于 yarch web 脚手架生成的中后台工程` |
| 4 | dev 端口 | 5173 |
| 5 | API 代理目标 | `http://localhost:8080` |
| 6 | GitLab 分组（登记提示用） | 空 |

### 命名规则（contract/registry.md 一-1/一-2，与 golang 生成器同款）

- 格式 `^[a-z][a-z0-9-]{1,31}$`：小写字母/数字/短横线，字母开头，2~32 字符——大写、下划线、中文一律当场拒绝；
- 禁裸通用词：`api app service server backend web admin main common system demo user gateway` 不得单独成名（加前缀消歧，如 `ysaas-admin`）。

### 非交互 flags（脚本 / CI）

`npm create @yarch/admin@latest <名> -- --ui antd --yes`（npm 传 flag 须 `--` 分隔；pnpm 直接跟）。
维护者本地直跑：`node stacks/web/packages/create/bin/create-admin.mjs <名> --ui antd --yes --deps file`。

| flag | 取值 | 说明 |
|---|---|---|
| `--name` | 工程名 | 也可用第一个位置参数 |
| `--ui` | `semi` \| `antd` \| `arco` | 非法值报错 |
| `--desc` | 文本 | 不得含双引号/反斜杠 |
| `--port` | 1024~65535 | |
| `--proxy` | `http(s)://…` | API 代理目标 |
| `--group` | 文本 | GitLab 分组 |
| `--deps` | `version`（默认）\| `file` | `file` = 指向 yarch 本仓源码（发版前联调） |
| `--src` / `--out` | 目录 | 模板根 / 输出目录（默认 `./<工程名>`，须空） |
| `--yes` | | 非交互，缺省全走默认 |

### 原理（一段话）

maven-archetype / cookiecutter 模式：三档模板是声明式资产（`{{var}}` 占位 + `archetype.json` 变量声明），生成器做问答 → 变量集 → 目录/文件/内容全量替换 + 残留占位符扫描；模板自身不要求可安装，工程正确性由 CI「生成后冒烟」（生成 → install → tsc → build）保证。

### 发布（维护者）

推 tag `stacks/web/vX.Y.Z` → [web-publish.yml](../../../.github/workflows/web-publish.yml)：断言 + 机检 → contract → react → create 顺序发布（幂等）。
</details>
