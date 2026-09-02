# stacks/web · yarch-web

> pnpm workspace 单仓（W0）：三层底座（共享契约包 + 框架适配 + UI 档模板），与 java 栈同构。
> 契约唯一权威：[../contract/](../../contract/README.md)；策划拍板记录：[PLAN.md](PLAN.md)（W0/W5 已冻结，W1-W4 待拍板）。

## 包

| 包 | 描述 | 对应 java |
|---|---|---|
| `packages/contract` | @yarch/contract：信封类型/解包、错误码常量表、ApiError、traceId、fetch 封装、导航端口——全 .ts、零框架依赖 | yarch-common + yarch-http |
| `packages/react` | @yarch/react：注入 react-router 导航（薄适配） | starter |
| `packages/vue` | @yarch/vue：注入 vue-router 导航（薄适配） | starter |
| `templates/admin-antd` | 默认档：Vite + React + **antd** | archetype |
| `templates/admin-semi` | Semi 档：Vite + React + **Semi Design**（抖音系） | archetype |
| `templates/admin-arco` | Arco 档：Vite + React + **Arco Design**（字节系） | archetype |
| `examples/admin-demo` | 模板生成工程 + 契约联调回归场（后置） | examples |

## 机检三件（= 前端版 ArchUnit/spotless/契约断言）

1. **depcruise**（`pnpm check:deps`）：pages 薄入口（禁直调 features/*/api.ts）、ui/components 禁反向依赖 features/pages、contract 零框架依赖。
2. **biome**（规范单点，对应 spotless）。
3. **vitest**（契约断言：错误码表与 error-codes.md 逐码核对——防漂移）。

## 快速开始

```bash
pnpm install
pnpm test          # contract 4/4 绿
pnpm check:deps    # 依赖方向机检
```

架构图：[architecture-diagram.svg](architecture-diagram.svg)。

> 扩展名纪律（W5）：有 JSX 用 .tsx，纯逻辑/类型 .ts；Vue 用 .vue SFC（lang="ts"）。
