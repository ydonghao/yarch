# stacks/web · yarch-web 策划案

> **状态：W0-W3、W5-W9 已冻结（W6-W9 于 2026-09-03 拍板），W4 待拍板。**
> 输入：contract/ 四件套（唯一权威）· java 栈 16 模块的同构经验 · golang 栈 `_template + yarch-init`（cookiecutter/archetype 模式，web 生成器同构蓝本）· 对标：Vite（构建底座标准）/ create-vite（工程生成标准）/ Semi·AntD Pro·Arco Pro（中后台成品标准）/ shadcn（组件标准）。

## W 决策清单

| # | 议题 | 推荐 | 状态 |
|---|---|---|---|
| W0 | 三层结构 + pnpm workspace 单仓（packages/contract 跨框架契约包 + react/vue 薄适配 + templates UI 档薄壳）；合并原 web-react/web-vue 两目录 | 采纳（对齐 java：common/starters/archetype 同构） | **已冻结** |
| W1 | UI 档：**三档并行**——Semi（抖音，**默认**）/ antd（蚂蚁）/ Arco（字节），每档独立模板薄壳，共享底座不变 | **已拍板（2026-09-02）：三档都要；2026-09-03 修订：默认档 antd → Semi** |
| W2 | 组件分层流派：feature-first（features/ 按业务域） | **已拍板（2026-09-02）** |
| W3 | 机检：dependency-cruiser 依赖方向规则 | **已拍板（2026-09-02）** |
| W4 | 构建底座 | **已冻结（2026-09-02）：Vite 默认**；Rsbuild 触发式（信号：模块过万或 build>30s；届时 Rolldown 可能已稳定） |
| W5 | 文件扩展名纪律：有 JSX 用 .tsx，纯逻辑/类型 .ts；Vue 用 .vue SFC（lang="ts"） | 采纳 | 已冻结 |
| W6 | 工程生成分发通道 | **npm 为主**：发 `@yarch/create-admin`，用法 `npm create @yarch/admin@latest <name> -- --ui semi`（create-vite 惯例，= golang `go run …@version` 的 npm 对偶）；GitLab 仅作源码托管。未发版阶段本地 `node bin/create-admin.mjs --deps file`。**发版机制（2026-09-03 补）**：推 tag `stacks/web/vX.Y.Z` → web-publish.yml CI 发布三包（contract → react → create，幂等；publishConfig access public）。**v0.1.0 已首发（2026-09-03，npmjs.com @yarch org，零 clone 端到端验证通过）** | **已拍板（2026-09-03）** |
| W7 | 生成期标准化方式 | **交互式问答**（create-vite 式）：工程名（registry.md 一-1 校验：`^[a-z][a-z0-9-]{1,31}$` + 禁裸通用词）→ UI 档（semi 默认/antd/arco）→ 描述 → 端口 → API 代理 → GitLab 分组；答案即渲染变量。`--yes` + flags 走非交互（CI 用） | **已拍板（2026-09-03）** |
| W8 | 底座依赖解耦 | **业界标准 = maven-archetype 式全量渲染**：目录/文件/内容按变量替换；`@yarch/contract`、`@yarch/react` 以 npm 版本依赖进生成工程（发版前 `file:` 绝对路径过渡 = golang replace 行对偶，发版后换 `^X.Y.Z`） | **已拍板（2026-09-03）** |
| W9 | 模板资产化 | `templates/admin-*` 改为**声明式资产**（`{{var}}` 占位 + archetype.json 变量声明），迁入 `packages/create/templates/`（随包发布）；模板自身不再要求可安装，正确性由 CI「生成后冒烟」保证（生成 → install → tsc → build）——golang `_template` 同构 | **已拍板（2026-09-03）** |
| W10 | 微前端模板与回归场：`--micro base\|sub` 模板档 + `examples/micro-demo` + depcruise 微前端规则 + 双模式冒烟 | **触发式·未启动**——规约已定稿先行（[contract/web/micro-frontend.md](../../contract/web/micro-frontend.md) v1.0，2026-09-07，含第十三章机检清单）；登记触发：首个客户项目接入微前端 | 触发式 |

## 结构基准（W0 冻结；W9 修订 2026-09-03：templates/ 资产化迁入 create 包）

```
stacks/web/
├── package.json · pnpm-workspace.yaml · tsconfig.base.json · biome.json · depcruise.config.js
├── packages/
│   ├── contract/        # @yarch/contract：rest-response · error-codes · api-error · trace-id · navigator(端口) · http（全 .ts，零框架依赖）
│   ├── react/           # @yarch/react：注入 react-router 导航（薄）
│   ├── vue/             # @yarch/vue：注入 vue-router 导航（薄）
│   └── create/          # @yarch/create-admin：工程生成器（W6-W9）——交互问答 + 渲染引擎 + 命名校验
│       └── templates/   #   admin-semi（默认）/ admin-antd / admin-arco：声明式资产（{{var}} + archetype.json，不要求可安装）
└── examples/admin-demo/   # 契约联调回归场（仓内 workspace 成员，对接 java examples-ddd）
```

## 验收口径

1. workspace `pnpm test` 绿（contract 包含错误码表防漂移断言——契约断言前端版）；
2. depcruise 依赖方向检查通过（含模板资产 src）；
3. **生成后冒烟（W9，替代原"模板自身构建"）：生成器三档各生成一工程 → `pnpm install` → `tsc --noEmit` → `vite build` 全绿 + 生成物零残留占位符**；
4. 生成工程登录/列表页对接 java examples（信封解包/traceId 透传/401 跳转眼见为实）；
5. CI（web-stack.yml：双 Node 矩阵 + 生成后冒烟）。

> SVG 架构图：[architecture-diagram.svg](architecture-diagram.svg)（三层底座 + UI 档 + 契约引用，java 栈同款风格）。
