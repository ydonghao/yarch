# agents-md · AI 施工上下文唯一事实源（v1.0 已定稿）

> **状态：已定稿**（2026-09-14 经 A1-A6 拍板口径成文，决策清单与生态依据见 [ai-cli-ecosystem-digest.md](../../docs/references/ai-cli-ecosystem-digest.md)）。
> 等级：**【强制】**违反即缺陷；**【推荐】**默认遵守、评审后可豁免；**【参考】**正向引导。
> 约束对象：yarch 体系全部工程中被 AI 编码代理（Claude Code / Codex / Gemini CLI / Cursor / Copilot / ZCode 等）消费的施工上下文——含各栈生成器产出物与 yarch 仓自身。
> 协作参考（软）：[../README.md](../README.md)（治理/变更流程）、[extensions.md](extensions.md)（MCP/skills/hooks 扩展出口）、[cli.md](cli.md)（生成器交互规约）。

## 一、唯一事实源与派生

1. 【强制】**AGENTS.md 是工程施工上下文的唯一事实源**，仓根必有（生成器模板内置，N1 条文化）。依据：AGENTS.md 系 Linux 基金会 Agentic AI Foundation 托管的开放标准，主流代理中仅 Claude Code 不原生读取（digest C1/C2）。
2. 【强制】`CLAUDE.md` 与 `GEMINI.md` 只允许**一行导入派生**——内容为 `@AGENTS.md`（两家官方各自的导入语法，恰好同形）；禁内容分叉、禁两份维护。
3. 【强制】生成器模板禁内置/维护第三方目录型或旧式上下文配置（`.cursor/rules/`、`.windsurfrules`、`.github/copilot-instructions.md` 等）——这些工具均原生读 AGENTS.md；业务仓开发者自行添加属个人工具链选择，不入机检。
4. 【推荐】monorepo 子栈/子包可嵌套 AGENTS.md，语义从生态通行口径"最近者优先"；嵌套文件只写与根文件的**增量差异**（命令、红线增补），禁整篇复制根文件。
5. 【强制】用户级全局文件（`~/.claude/CLAUDE.md`、`~/.codex/AGENTS.md`、`~/.zcode/AGENTS.md` 等）属个人环境，禁入库存储、禁生成器写入。

## 二、结构定式

1. 【强制】正文四章节定式，顺序固定：

| 章节 | 内容 | 要求 |
|---|---|---|
| 工程地图 | 目录/模块职责速览 | 一屏内；只写"在哪改"，不写架构论述 |
| 命令表 | 构建 / 测试 / lint / 冒烟命令 | **每条必须 AI 可机跑**（非交互、有明确退出码）；写不了的命令不进表 |
| 红线清单 | 表格：红线 \| 规则 \| 出处 | 违反 = 机检失败 / CR 必拒；每行必须带条文出处（见三-1） |
| 契约锚点 | 本工程启用的规约链接清单 | 相对链接或固定 URL，指向 contract/ 具体文档 |

2. 【强制】红线禁口号式表述（"注意代码质量"之类）；每条红线必须可机检或带契约条文号，否则移出红线表。
3. 【推荐】可选章节：完成定义（DoD）、方言备注、环境前置。可选章节排在四必备章节之后。
4. 【强制】**篇幅上限：正文 ≤150 行**。量化依据：最严消费方 Codex 对拼接后上下文文件设 32 KiB 合并上限（digest C5）；超限内容一律移入被锚点引用的契约/文档，AGENTS.md 只留指针。
5. 【强制】禁写内容（同"规约文档必须是条文不是分析"铁律）：分析过程、调研综述、变更流水账、与契约正文的大段重复拷贝。

## 三、契约锚点引用

1. 【强制】引用契约条文必须带「文档路径 + 章节号」双要素（例：`contract/web/micro-frontend.md 十二-2`）；禁裸写"见规约""按规范"。
2. 【推荐】yarch 仓内用相对链接（可点击跳转）；业务仓引用 yarch 仓条文用文字锚（路径+章节号）或 tag 锚定的固定 URL——业务仓不 vendor 契约文档副本。
3. 【强制】禁把契约条文正文大段拷入 AGENTS.md；单一事实源在 `contract/`，AGENTS.md 只做红线索引与指针（N2 机器可读出口首个消费场景，digest A5）。

## 四、生效与维护

1. 【强制】生成器产出的工程必须含合规 AGENTS.md + 一行派生的 CLAUDE.md / GEMINI.md，且生成器冒烟自检覆盖三者存在性（生成器侧义务，交互口径见 [cli.md](cli.md)）。
2. 【强制】契约变更时同步检查引用它的 AGENTS.md 锚点（挂接 [../README.md](../README.md) 变更流程：先改契约再改实现，锚点检查列契约变更 PR 检查项）。
3. 【推荐】yarch 仓自身带头合规：仓根本文件三件套（AGENTS.md + 两个派生文件）随本规约同批落地。

## 五、可机检条文清单

> 对齐 [../README.md](../README.md)「机检路线」。落地载体：生成器冒烟自检 + 业务仓 CI。

| 条文 | 机检手段 | 阶段 |
|---|---|---|
| 一-1 仓根必有 AGENTS.md | 文件存在性检查 | 生成器冒烟 + CI |
| 一-2 派生文件仅一行 | `CLAUDE.md`/`GEMINI.md` 内容 === `@AGENTS.md`（或文件不存在） | CI |
| 一-3 禁第三方目录型配置 | 生成器模板目录扫描（路径黑名单） | 生成器 CI |
| 二-1 四章节定式 | 章节头正则匹配四个必备标题 | CI |
| 二-4 篇幅 ≤150 行 | `wc -l` 门禁 | CI |
| 三-1 锚点双要素 | 红线表/锚点节每行正则须含 `contract/… 章节号` | CI |

---

## 附：拍板记录

- 拍板口径（A1-A6，2026-09-14 用户指令「就按照你的建议进行推进」按建议落地，正式登记见 [../README.md](../README.md) 关键架构决策登记表）：
  - A1 新设顶层规约层（落位 `contract/agent/`；原口径 `contract/ai/` 因 `domains/ai.md`「LLM 接入」占位撞名而改）；A2 AGENTS.md 唯一事实源 + CLAUDE.md/GEMINI.md 一行派生；A3 写法标准独立成文（本文件）；A4 扩展出口纳管范围（[extensions.md](extensions.md)）；A5 并入 N2 机器可读出口首个消费场景（本文件三节）；A6 自家 CLI 交互规约（[cli.md](cli.md)）。
- 生态依据：AGENTS.md 为 LF/AAIF 托管开放标准（20+ 工具原生读取）；Claude Code 官方 workaround 即 `@AGENTS.md` 导入行；Gemini CLI 需 `context.fileName` 配置方读 AGENTS.md，故 GEMINI.md 派生保留——详见 digest C1-C5。
