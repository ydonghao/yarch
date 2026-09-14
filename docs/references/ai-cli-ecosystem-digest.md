# AI 编码代理生态调研 digest —— agent/ 规约立项依据（2026-09-14）

> 调研性质文档（分析文体，非规约条文）。规约落位 [contract/agent/](../../contract/agent/)。
> 方法：2026-09-14 实时核查官方文档与标准站点（agents.md / agentskills.io / modelcontextprotocol.io / Linux Foundation 公告），双调研面（上下文文件 / 扩展出口）并行；未能实时证实者单列「未证实清单」，不作为条文依据。

## 结论先行（十条）

| # | 结论 | 对规约的意义 |
|---|---|---|
| C1 | **AGENTS.md 已是 Linux 基金会托管的开放标准**（Agentic AI Foundation，2025-12-09 与 MCP、goose 同批捐入；发起方 OpenAI Codex / Amp / Jules / Cursor / Factory），官网采用名单 20+ 工具 | A2 的 SSOT 选择成立且有治理背书 |
| C2 | **Claude Code 是唯一主流例外**：官方明示 "reads CLAUDE.md, not AGENTS.md"（截至 v2.1.270），官方解法 = CLAUDE.md 写 `@AGENTS.md` 导入行或符号链接；v2.1.213+ 提供 `/import` 迁移命令 | 派生文件条款（一行 `@AGENTS.md`）是官方推荐路径，不是自创变通 |
| C3 | Gemini CLI 默认只读 GEMINI.md，AGENTS.md 需 `context.fileName` 显式配置；GEMINI.md 支持 `@file.md` 导入 | GEMINI.md 同款一行派生 |
| C4 | 子目录嵌套 AGENTS.md 获 Codex / Cursor / Copilot / VS Code（opt-in）/ opencode / Kilo / Factory 等支持，通行语义"最近者优先" | monorepo 嵌套条款有生态基础 |
| C5 | **容量最严约束 = Codex 合并上限 32 KiB**（`project_doc_max_bytes`）；Factory 80k/40k 字符；Windsurf 12k/规则文件；Claude 4 MiB 最宽 | AGENTS.md 篇幅上限条文的量化依据 |
| C6 | `.mcp.json`（仓根）**只有两家读**：Claude Code（发起）与 VS Code Agent Host；其余各家私有路径且无跨厂商标准声明 | 纳管但定性"弱事实标准"；不生成其他工具的私有 MCP 配置 |
| C7 | **Agent Skills 是 agentskills.io 开放标准**（SKILL.md + frontmatter `name`/`description`，渐进披露），采用方 ~45；**`.agents/skills/` 是跨厂商事实位置**（Codex/Zed 唯一采用，Gemini/Cursor/VS Code/opencode 兼容别名）；Claude Code 只吃 `.claude/skills` | skills 推荐布局 = `.agents/skills/` + `.claude/skills` 链接派生 |
| C8 | Hooks：Claude 的事件名 + JSON-stdio 契约（exit 2 阻断）被 Codex / VS Code 近乎照抄，Cursor 为 camelCase 变体，Gemini 自有 7 事件，opencode 为 JS 插件 | 事实标准是 Claude 系，但配置位置各家私有 → 不定强制布局，参考级 |
| C9 | Subagents 格式分裂：MD+frontmatter（Claude/Cursor/Gemini/opencode）vs TOML（Codex）vs `.agent.md`（VS Code） | 不纳管，项目自选 |
| C10 | Slash commands 各厂正向 skills 合并/废弃（Claude 已并入 skills、Codex prompts 废弃、VS Code prompt 文件废弃、Zed 移除扩展命令） | 可复用提示词一律 skills 载体 |

## 一、上下文文件能力矩阵

| 工具 | 原生上下文文件 | 原生读 AGENTS.md | 用户级文件 | 子目录嵌套 |
|---|---|---|---|---|
| Claude Code | `CLAUDE.md` / `CLAUDE.local.md` / `.claude/rules/` | **否**（官方 workaround：`@AGENTS.md` 导入行 / symlink） | `~/.claude/CLAUDE.md`、`~/.claude/rules/` | ✓ 父目录启动加载、子目录按需，全部拼接；`@path` 导入深度 4 |
| Codex CLI | `AGENTS.md`、`AGENTS.override.md` | **✓**（2025-05-16 首发即支持） | `~/.codex/AGENTS.md` | ✓ git root→cwd 拼接、近者优先；**合并上限 32 KiB**；无导入语法 |
| Gemini CLI | `GEMINI.md`（默认名） | **部分**：需 `context.fileName` 配置（如 `["AGENTS.md","GEMINI.md"]`） | `~/.gemini/GEMINI.md` | ✓ 祖先+子目录；`@file.md` 导入 |
| Cursor | `.cursor/rules/*.mdc` + AGENTS.md | **✓**（2025 年内加入，根+嵌套，"更具体者优先"） | UI 用户规则（非文件） | ✓ |
| GitHub Copilot coding agent / VS Code | `.github/copilot-instructions.md`、`*.instructions.md` | **✓**（coding agent 2025-08-28 起；VS Code 自动检测 + 嵌套 opt-in）；兼读 CLAUDE.md 系 | `~/.copilot/instructions`、`~/.claude/CLAUDE.md` | ✓（nearest wins / 嵌套开关） |
| opencode | `AGENTS.md`（fallback CLAUDE.md） | **✓**（含 glob 如 `packages/*/AGENTS.md`） | `~/.config/opencode/AGENTS.md` | ✓ 向上遍历 |
| Zed | 首匹配列表（`.rules`→`.cursorrules`→…→`AGENTS.md`→`CLAUDE.md`→`GEMINI.md`） | **✓** | `~/.config/zed/AGENTS.md` | 未文档化 |
| Aider | 任意 md 经 `--read`（惯例 CONVENTIONS.md） | **否**（无自动加载，可 `--read AGENTS.md` 手动） | — | — |
| Amp | `AGENTS.md`（fallback `AGENT.md`→`CLAUDE.md`） | **✓**（主文件；`@path` 提及 + globs） | `~/.config/amp/AGENTS.md` 等 | ✓ 父目录常载、子树随读 |
| Factory droid | `AGENTS.md`（含大小写变体、CLAUDE.md） | **✓** | `~/.factory/`、`~/.agents/` | ✓；上限 80k/40k 字符 |
| Jules | `AGENTS.md` | **✓**（2025-06-20 起，仓根） | 未文档化 | 文档仅仓根 |
| Windsurf / Devin | `.devin/rules/*.md`（优选）、`.windsurf/rules/`、AGENTS.md | **✓**（rules 引擎处理：根=always-on，子目录=auto-glob） | `~/.codeium/windsurf/memories/global_rules.md`（6k 字符） | ✓；规则文件 12k 字符/个 |
| Cline / Roo Code / Kilo Code | `.clinerules/`、`.roo/rules/`、`kilo.jsonc instructions` | **✓** 全部（Kilo 已废弃 memory bank 转 AGENTS.md） | `~/.agents/AGENTS.md`（Cline）等 | Roo/Kilo 支持递归/逐目录 |
| ZCode（Z.ai） | **无公开文档** | 未证实（本地证据：用户级 `~/.zcode/AGENTS.md` 加载为默认指令） | `~/.zcode/AGENTS.md`（本地证据） | 未知 |

## 二、扩展出口矩阵

| 出口 | 事实位置 / 格式 | 跨工具状态 |
|---|---|---|
| MCP 项目级配置 | `.mcp.json`（`mcpServers` 键；`${ENV}`/`${ENV:-default}` 展开） | **仅 Claude Code + VS Code Agent Host 原生读**；其余私有：`.cursor/mcp.json`、`.gemini/settings.json`、`.codex/config.toml`（TOML）、`opencode.json`、`.zed/settings.json`、`.vscode/mcp.json`（键为 `servers` 非同构）。无跨厂商标准声明 |
| Skills | agentskills.io 开放标准：`SKILL.md` + YAML frontmatter（`name`/`description` 必备），渐进披露 | 项目级 `.agents/skills/` = 跨厂商事实位置（Codex/Zed 唯一采用；Gemini/Cursor/VS Code/opencode 兼容别名）；**Claude Code 例外**：仅 `.claude/skills/` + 用户级 + 插件 |
| Hooks | Claude 系：settings JSON 内 hooks，32 事件，5 种 handler（command/http/mcp_tool/prompt/agent），exit 2 阻断 | Codex `.codex/hooks.json` 与 VS Code 近乎照抄 Claude 契约；Cursor `hooks.json`（camelCase 变体）；Gemini settings.json `hooks` 键（自有 7 事件）；opencode = JS/TS 插件。配置位置各家私有 |
| Subagents | Claude `.claude/agents/*.md`（MD+frontmatter） | Codex = TOML（`.codex/agents/*.toml`）；VS Code = `.github/agents/*.agent.md`；Cursor/Gemini/opencode = MD+frontmatter 各自目录。格式分裂 |
| Slash commands | 各厂目录私有 | **整体向 skills 合并/废弃**：Claude 自定义命令已并入 skills；Codex `~/.codex/prompts` 废弃转 skills；VS Code `.prompt.md` 对 Agent Host 废弃；Zed 移除扩展 slash 命令转 MCP/skills |

治理背景：MCP / AGENTS.md / goose 于 2025-12-09 捐入 Linux Foundation Agentic AI Foundation（白金会员 AWS / Anthropic / Google / Microsoft / OpenAI）；MCP 最新规范 2026-07-28 版。**押注开放三件套（AGENTS.md + agentskills + MCP）优于押注任何厂商私有件**。

## 三、A1-A6 拍板口径（2026-09-14 用户拍板，按建议落地）

| # | 议题 | 拍板结论 |
|---|---|---|
| A1 | 域归属 | 新设顶层规约层（不塞进 client-shared 或各栈零散约定）。**落位 `contract/agent/`**：拍板时口径为 `contract/ai/`，成文时发现 `domains/ai.md` 占位系 09-03 规划的「LLM 接入」业务域（流式/token/RAG），与本层不同物，改 `agent/` 避撞名 |
| A2 | 唯一事实源 | AGENTS.md 为 SSOT；`CLAUDE.md` / `GEMINI.md` 仅允许一行 `@AGENTS.md` 导入派生，禁内容分叉；目录型配置（`.cursor/rules` 等）不生成不维护 |
| A3 | AGENTS.md 写法标准 | 独立条文成文：四章节定式（工程地图 / 命令表 / 红线清单 / 契约锚点）+ 篇幅上限（Codex 32 KiB 最严口径）+ 禁写内容（分析、流水账、大段拷贝契约） |
| A4 | 生态专有出口纳管 | 首批只纳管 `.mcp.json`（凭证 `${ENV}` 引用）与 skills（`.agents/skills/` 跨厂商位置，`.claude/skills` 链接派生）；hooks / subagents 参考级不定强制 |
| A5 | 与 N2 关系 | 合并立项：AI 可读出口 = N2（契约机器可读出口）首个消费场景，锚点引用格式随 agents-md.md 定稿 |
| A6 | 自家 CLI 交互规约 | 并入本层单独成文 `cli.md`：一行命令铁律成文化 + 非交互 / `--json` / 退出码 / 幂等，约束全部 yarch-init* 生成器 |

## 四、未证实清单与观察哨

**未证实（不作条文依据）**：

1. ZCode 全部行为：无公开文档，仅本机证据（`~/.zcode/AGENTS.md` 用户级加载、`~/.zcode/skills` 与 `~/.agents/skills` 双来源 skills、二进制内含 AGENTS.md 迁移字符串）。
2. Cursor / Windsurf / opencode / Zed / Amp / Factory 等 AGENTS.md 支持的精确起始版本（官方文档无日期）。
3. Aider 在 agents.md 官网采用名单中，但无自动加载（推定 = `--read` 手动）。
4. agents.md 官网采用名单的时效性（含 Phoenix/Semgrep 等未独立验证条目）。

**观察哨**（触发即复审条文）：

- Claude Code 若原生支持 AGENTS.md → 派生文件条款可降级为可选。
- `.mcp.json` 若获更多厂商原生读取 → 升级为强事实标准并补各家派生约定。
- `.agents/skills/` 若被 Claude Code 采纳 → 撤符号链接派生条款。

## 附：主要来源

- 标准与治理：[agents.md](https://agents.md/) · [agentskills.io](https://agentskills.io) · [MCP spec](https://modelcontextprotocol.io/specification/latest) · [LF AAIF 成立公告（2025-12-09）](https://www.linuxfoundation.org/press/linux-foundation-announces-the-formation-of-the-agentic-ai-foundation)
- Claude Code：[memory](https://code.claude.com/docs/en/memory) · [MCP](https://code.claude.com/docs/en/mcp) · [skills](https://code.claude.com/docs/en/skills) · [hooks](https://code.claude.com/docs/en/hooks) · [sub-agents](https://code.claude.com/docs/en/sub-agents) · [slash-commands](https://code.claude.com/docs/en/slash-commands) · [settings](https://code.claude.com/docs/en/settings) · [CHANGELOG](https://raw.githubusercontent.com/anthropics/claude-code/main/CHANGELOG.md)（v2.1.270 全文无 agents.md 匹配）
- Codex：[AGENTS.md 指南](https://learn.chatgpt.com/docs/agent-configuration/agents-md) · [hooks](https://learn.chatgpt.com/docs/hooks.md) · [subagents](https://learn.chatgpt.com/docs/agent-configuration/subagents.md) · [build-skills](https://learn.chatgpt.com/docs/build-skills.md) · [首发公告](https://openai.com/index/introducing-codex/)
- Gemini CLI：[gemini-md](https://google-gemini.github.io/gemini-cli/docs/cli/gemini-md.html) · [settings](https://raw.githubusercontent.com/google-gemini/gemini-cli/main/docs/cli/settings.md) · [using-agent-skills](https://raw.githubusercontent.com/google-gemini/gemini-cli/main/docs/cli/using-agent-skills.md) · [writing-hooks](https://raw.githubusercontent.com/google-gemini/gemini-cli/main/docs/hooks/writing-hooks.md) · [subagents](https://raw.githubusercontent.com/google-gemini/gemini-cli/main/docs/core/subagents.md) · [custom-commands](https://raw.githubusercontent.com/google-gemini/gemini-cli/main/docs/cli/custom-commands.md)
- Cursor：[rules](https://cursor.com/docs/context/rules) · [mcp](https://cursor.com/docs/context/mcp) · [skills](https://cursor.com/docs/context/skills) · [hooks](https://cursor.com/docs/agent/hooks) · [subagents](https://cursor.com/docs/subagents)
- VS Code / Copilot：[vscode-docs agent-customization 目录](https://raw.githubusercontent.com/microsoft/vscode-docs/main/docs/agent-customization/)（custom-instructions / mcp-servers / agent-skills / hooks / custom-agents / prompt-files）· [Copilot coding agent AGENTS.md 支持 changelog（2025-08-28）](https://github.blog/changelog/2025-08-28-copilot-coding-agent-now-supports-agents-md-custom-instructions/)
- 其余：[opencode rules](https://opencode.ai/docs/rules/) · [opencode skills](https://opencode.ai/docs/skills/) · [opencode plugins](https://opencode.ai/docs/plugins/) · [Zed instructions](https://zed.dev/docs/ai/instructions) · [Zed skills](https://raw.githubusercontent.com/zed-industries/zed/main/docs/src/ai/skills.md) · [Aider conventions](https://aider.chat/docs/usage/conventions.html) · [Amp AGENTS.md](https://ampcode.com/docs/customize/agents-md) · [Factory AGENTS.md](https://docs.factory.ai/cli/configuration/agents-md) · [Jules docs](https://jules.google/docs) · [Devin/Windsurf memories](https://docs.devin.ai/desktop/cascade/memories) · [Cline rules](https://docs.cline.bot/features/cline-rules) · [Roo custom-instructions](https://roocodeinc.github.io/Roo-Code/features/custom-instructions) · [Kilo AGENTS.md](https://kilo.ai/docs/customize/agents-md) · [Continue rules](https://docs.continue.dev/customize/deep-dives/rules)
