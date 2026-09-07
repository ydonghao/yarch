# stacks/web · yarch-web

> pnpm workspace 单仓（W0）：三层底座（共享契约包 + 框架适配 + UI 档模板资产），与 java/golang 栈同构。
> 契约唯一权威：[../contract/](../../contract/README.md)；策划拍板记录：[PLAN.md](PLAN.md)（W0-W3、W5-W9 已冻结，W4 待拍板）。

## 包

| 包 | 描述 | 对应 java/golang |
|---|---|---|
| `packages/contract` | @yarch/contract：信封类型/解包、错误码常量表、ApiError、traceId、fetch 封装、导航端口——全 .ts、零框架依赖 | yarch-common + yarch-http |
| `packages/react` | @yarch/react：注入 react-router 导航（薄适配） | starter |
| `packages/vue` | @yarch/vue：注入 vue-router 导航（薄适配） | starter |
| `packages/create` | **@yarch/create-admin：工程生成器（W6-W9）**——交互问答 + archetype 全量渲染 + registry 命名校验；模板资产在其 `templates/` 下 | golang `cmd/yarch-init` |
| `packages/create/templates/admin-semi` | 默认档资产：Vite + React + **Semi Design**（抖音系） | golang `_template` |
| `packages/create/templates/admin-antd` | antd 档资产：Vite + React + **antd**（蚂蚁系） | golang `_template` |
| `packages/create/templates/admin-arco` | Arco 档资产：Vite + React + **Arco Design**（字节系） | golang `_template` |
| `examples/admin-demo` | 契约联调回归场（仓内 workspace 成员，对接 java examples-ddd） | examples |

> 模板是**声明式资产**（`{{var}}` 占位 + `archetype.json` 变量声明，W9）：不要求自身可安装，工程正确性由 CI「生成后冒烟」保证——与 golang `_template` 同构。

## 创建工程（三档任选，零 clone；完整说明：[packages/create/README.md](packages/create/README.md)）

```bash
npm create @yarch/admin@latest ysaas-console              # Semi 档（默认 · 抖音系）
npm create @yarch/admin@latest ysaas-console -- --ui antd # antd 档（蚂蚁系）
npm create @yarch/admin@latest ysaas-console -- --ui arco # Arco 档（字节系）

cd ysaas-console && pnpm install && pnpm dev              # 起跑 → http://localhost:5173
```

**生成即合规**（对比裸脚手架的优势明细见 create README）：契约底座预接线（信封解包 / 13 码错误码 / traceId / 401 跳登录）· 工程名强制 registry 标准 · 底座 npm 版本化升级（`pnpm update` 一行）· feature-first 分层机检口径。不传 `--ui` = 交互问答三选一（另有描述/端口/代理/分组，全有默认）；脚本/CI 非交互加 `-- --ui antd --yes`；本地调试生成器 `pnpm gen -- <名> --deps file`。

**发版机制（W6）**：推 tag `stacks/web/vX.Y.Z` → CI（web-publish.yml）发布三包（contract → react → create，幂等）——golang「tag → Go module proxy」的 npm 对偶。

## 机检三件（= 前端版 ArchUnit/spotless/契约断言）

1. **depcruise**（`pnpm check:deps`）：pages 薄入口（禁直调 features/*/api.ts）、ui/components 禁反向依赖 features/pages、contract 零框架依赖。
2. **biome**（规范单点，对应 spotless）。
3. **vitest**（契约断言：错误码表与 error-codes.md 逐码核对——防漂移）。

## 验证状态（2026-09-03，W6-W9 落地）

| 检查项 | 结果 |
|---|---|
| @yarch/contract vitest | 4/4 ✅ |
| depcruise（三档模板资产 + contract + admin-demo） | 0 violations ✅ |
| 生成后冒烟（W9：三档 生成 → install → tsc → build） | 全绿 ✅（semi 2.0s · antd 1.3s · arco 1.0s；生成物零残留占位符） |
| 生成器负路径（裸通用词/下划线/大写/非空目录/缺名非 TTY） | 全部拦截 ✅ |
| **零 clone 端到端**（`npm create @yarch/admin@latest` → registry 装依赖 → tsc → build） | **全绿 ✅（2026-09-03 首发 v0.1.0，npmjs.com @yarch org）** |

## 快速开始

```bash
pnpm install
pnpm test          # contract 4/4 绿
pnpm check:deps    # 依赖方向机检
pnpm gen -- ysaas-console --ui semi --yes --out /tmp/ysaas-console   # 生成一个工程看看
```

架构图：[architecture-diagram.svg](architecture-diagram.svg)。

> 扩展名纪律（W5）：有 JSX 用 .tsx，纯逻辑/类型 .ts；Vue 用 .vue SFC（lang="ts"）。
