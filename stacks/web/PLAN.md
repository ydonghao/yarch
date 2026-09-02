# stacks/web · yarch-web 策划案

> **状态：W0 已冻结（2026-09-02），W1-W3 待拍板后开工模板档。**
> 输入：contract/ 四件套（唯一权威）· java 栈 16 模块的同构经验 · 对标：Vite（构建底座标准）/ AntD Pro·Arco Pro·Semi（中后台成品标准）/ Next.js（全栈标准，非本栈场景）/ shadcn（组件标准）。

## W 决策清单

| # | 议题 | 推荐 | 状态 |
|---|---|---|---|
| W0 | 三层结构 + pnpm workspace 单仓（packages/contract 跨框架契约包 + react/vue 薄适配 + templates UI 档薄壳）；合并原 web-react/web-vue 两目录 | 采纳（对齐 java：common/starters/archetype 同构） | **已冻结** |
| W1 | UI 档：**三档并行**——antd（蚂蚁，默认）/ Semi（抖音）/ Arco（字节），每档独立模板薄壳，共享底座不变 | **已拍板（2026-09-02）：三档都要** |
| W2 | 组件分层流派：feature-first（features/ 按业务域）vs layer-first | feature-first（中后台会长大） | 待拍板 |
| W3 | 机检：dependency-cruiser 依赖方向规则（pages→features→components/ui，禁反向、pages 禁直调 api.ts） | 采纳（= 前端版 ArchUnit） | 待拍板 |
| W4 | 构建底座 Vite（默认）vs Rsbuild | Vite；Rsbuild 登记备选（构建速度成瓶颈时） | 待拍板 |
| W5 | 文件扩展名纪律：有 JSX 用 .tsx，纯逻辑/类型 .ts；Vue 用 .vue SFC（lang="ts"） | 采纳 | 已冻结 |

## 结构基准（W0 冻结）

```
stacks/web/
├── package.json · pnpm-workspace.yaml · tsconfig.base.json · biome.json · depcruise.config.c8
├── packages/
│   ├── contract/        # @yarch/contract：rest-response · error-codes · api-error · trace-id · navigator(端口) · http（全 .ts，零框架依赖）
│   ├── react/           # @yarch/react：注入 react-router 导航（薄）
│   └── vue/             # @yarch/vue：注入 vue-router 导航（薄）
├── templates/admin-antd/  # 默认档：Vite + React + antd；src: app/ layouts/ pages/ features/ components/ ui/ stores/ types/
└── examples/admin-demo/   # 模板生成工程 + 契约联调回归场（后置）
```

## 验收口径

1. workspace `pnpm test` 绿（contract 包含错误码表防漂移断言——契约断言前端版）；
2. depcruise 依赖方向检查通过；
3. templates 生成工程 `pnpm build` 绿 + 登录/列表页对接 java examples（信封解包/traceId 透传/401 跳转眼见为实）；
4. CI（web-stack.yml，随模板完成后接入）。

> SVG 架构图：[architecture-diagram.svg](architecture-diagram.svg)（三层底座 + UI 档 + 契约引用，java 栈同款风格）。
