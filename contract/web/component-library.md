# 组件库规约（v1.0 已定稿）

> **状态：已定稿**（2026-09-18 经 CL1-CL6 决策拍板，随 ycomp 立项 D1-D9 设计文档终审通过；`contract/web/` 第二份）。供人阅读、供 AI 作为规范上下文使用。
> 等级：**【强制】**违反即缺陷；**【推荐】**默认遵守、评审后可豁免；**【参考】**正向引导。
> 约束对象：yarch 体系**前端资产包**（组件 / design token / 页面区块）的形态、命名与预览约定。资产平台（ycomp）只消费本约定，不定义它。

## 一、定位与适用范围

1. 【强制】前端资产分四类管理：**业务组件**（React/Vue）、**design token/主题包**、**页面模板/区块**；跨端共享 token 走本规约（clients 游戏端「三层共享」条文的设计 token 落点即此）。
2. 【强制】资产包归 yarch 仓 `stacks/web/packages/`，经公共 npm `@yarch` org 分发；资产平台（ycomp）只做元数据/预览/检索/追踪，**不做分发**。
3. 【强制】组件库按**框架各选生态**（D4 拍板）：React 基线 **antd v5**、Vue 基线 **ElementPlus**。微前端「同一体系 UI 档唯一」（[micro-frontend.md](micro-frontend.md) 六-1）仅约束单体系内部基座与子应用，不约束组件库跨系统选型；单体系引入组件库时组件基线必须与该体系定档一致（基座定 antd 则子应用组件库用 @yarch/pro-react，基座定 ElementPlus 系则用 @yarch/pro-vue），跨档混用仍按六-1 否决。
4. 【参考】组件选型原则：只封装「横切多系统的高频形态」（CRUD 表格、查询表单、详情布局、信封对接 hooks），不复制单系统业务形态；单系统特有形态留在该系统 features/。

## 二、包命名与登记

1. 【强制】资产包命名 `@yarch/<域>`，格式同 [../registry.md](../registry.md) 一-1（小写字母数字短横线）；首批定名：**`@yarch/tokens`**（design token，框架无关）、**`@yarch/pro-react`**（React 业务组件）、**`@yarch/pro-vue`**（Vue 业务组件，二批）；页面区块包命名随首批区块立项定。
2. 【强制】包一经 npm 发布即冻结命名；改名 = 破坏性变更走评审并登记变更记录。
3. 【强制】发版走既有 tag→npm 管道（tag `stacks/web/vX.Y.Z` → web-publish.yml），语义化版本；组件 breaking 变更必须升 minor（0.x 期）。

## 三、demo manifest 预览约定

1. 【强制】每个资产包必须携带**可预览 demo 元数据**，经包**子路径导出 `./demos`** 提供（类型安全 + 应用构建可整棵摇掉，不进生产 bundle）：

```ts
export interface ComponentDemo {
  /** kebab-case，包内唯一（列表键） */
  id: string;
  title: string;
  description?: string;
  /** demo 源码字符串——平台预览页展示与复制用 */
  source: string;
  /** 渲染入口：平台沙箱以容器节点调用；返回清理函数（unmount 收尾）或 void */
  render: (container: HTMLElement) => void | (() => void);
}
export declare const demos: ComponentDemo[];
```

2. 【强制】`render` 必须幂等可重入：同容器重复调用前由清理函数收尾（对齐微前端五-2 卸载清理纪律）；禁止向 `window` 挂全局（七-2 同源）。
3. 【强制】token 类资产以 `tokens.json`（四-1 schema）替代 demo manifest；平台以**通用 token 渲染器**直接消费，无需运行时。

## 四、tokens 包约定

1. 【强制】`tokens.json` 为机器可读唯一权威，五组必备：`color`（primary/semantic{success,warning,error,info}/neutral 阶梯）、`typography`（fontFamily/fontSize/lineHeight）、`spacing`（xs→xxl）、`radius`（sm→full）、`shadow`（sm→lg）；暗色模式经 `modes.dark` **只覆盖 color 组**，其余组跨模式共享。
2. 【强制】键名 kebab-case；色值 `#RRGGBB[AABBCCDD]` 形态；尺寸/圆角带 px/rem 单位后缀的字符串。
3. 【强制】CSS custom properties 与 TS 常量导出**必须与 tokens.json 同源**：键集合一致性由包内测试断言（防双写漂移，对齐 contract-dist 门哲学）；CSS 变量名 `--yarch-<组>-<键>`，暗色经 `[data-theme="dark"]` 选择器重定义。

## 五、组件包约定

1. 【强制】组件数据流对齐 [../api/rest-response.md](../api/rest-response.md)：表格类组件直接消费 `@yarch/contract` 的 `PageData<T>` 分页负载与 `ApiError`；业务 hooks（如 useApiQuery）统一走 `createClient` 信封解包，禁组件内自造 fetch。
2. 【强制】错误码 → UI 态映射在组件层统一（ApiError.code → 表格空态/表单校验态/toast），业务侧只处理业务分支。
3. 【推荐】组件 props 遵循「薄透传 + 少数语义 props」：不重造 antd/ElementPlus 全量 props 面，透传原库 props 并只增语义层（如 `request` 分页钩子）。

## 六、预览约定边界

1. 【强制】本规约条文边界 = demo manifest（三）与 tokens.json schema（四）——**资产包自带什么**；平台如何渲染（import map 构造、esm.sh CDN、沙箱机制）属 ycomp 实现细节，不入条文、平台可演进替换（含自举 CDN/快照退路）。

## 七、模板预装

1. 【强制】`create-admin` 的 antd 档模板预装 `@yarch/pro-react`（版本锚定 `^0.1.0` 起）；semi/arco 档不预装（基线不符，三-3）。
2. 【推荐】模板生成工程的列表/表单页优先改用组件库形态（ProTable/ProForm），保持模板与新系统视觉与数据流同构。

## 八、可机检条文清单

| 条文 | 机检手段 | 阶段 |
|---|---|---|
| 三-1 demos 子路径导出存在 | 包 exports 字段静态检查（CI） | CI |
| 三-2 render 清理约定 | 组件测试：render 两遍不泄漏（订阅/DOM 计数断言） | 单测 |
| 四-3 三载体同源 | tokens 包测试断言 json/css/ts 键集合一致 | 单测 |
| 五-1 数据流信封解包 | depcruise/源码扫描：组件包禁出现裸 fetch | CI |
| 七-1 antd 档预装 | 生成后冒烟：admin-antd 模板生成物含依赖 | CI |

---

## 附：拍板记录

- **CL1-CL6（2026-09-18 拍板，随 ycomp 设计文档 D1-D9 终审通过）**：CL1 demo manifest 经 `./demos` 子路径导出（源码字符串 + render 容器注入）；CL2 tokens.json 五组必备 + modes.dark 仅覆盖 color + 三载体同源断言；CL3 首批包名 @yarch/tokens、@yarch/pro-react、@yarch/pro-vue（区块包随立项定）；CL4 条文边界=manifest/schema，渲染机制留实现；CL5 create-admin antd 档预装 pro-react（semi/arco 不装）；CL6 React=antd v5、Vue=ElementPlus 成文为基线锁定。
- 与微前端规约关系：六-1「档唯一」在单体系内仍硬约束；组件库跨系统按框架各选生态（D4）。
- 依据 digest：ycomp 设计文档（docs/superpowers/specs/2026-09-18-ycomp-component-platform-design.md）。
