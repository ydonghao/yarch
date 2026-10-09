# extensions · AI 生态扩展出口纳管（MCP / skills / hooks / 运行产物）（v1.1 已定稿）

> **状态：已定稿**（v1.0 2026-09-14 经 A1-A6 拍板口径成文；v1.1 2026-10-08 增三-5 运行产物目录禁入库。生态依据见 [ai-cli-ecosystem-digest.md](../../docs/references/ai-cli-ecosystem-digest.md) C6-C10）。
> 等级：**【强制】**违反即缺陷；**【推荐】**默认遵守、评审后可豁免；**【参考】**正向引导。
> 约束对象：yarch 体系全部工程中被 AI 编码代理消费的扩展出口（MCP 服务器登记 / 可复用技能 / 自动化钩子 / 运行时产物目录）。
> 协作参考（软）：[agents-md.md](agents-md.md)（上下文唯一事实源）、[cli.md](cli.md)（生成器交互规约）。

## 一、MCP 配置（.mcp.json）

1. 【强制】项目级 MCP 服务器登记的唯一入库位置 = 仓根 **`.mcp.json`**（`mcpServers` 键 JSON）。定性：弱事实标准——Claude Code 发起、VS Code Agent Host 原生读，其余工具各有私有路径（digest C6）；选它因为开放件押注优先且无更好替代。
2. 【强制】**凭证禁字面量入库**：token / key / 密码一律 `${ENV_VAR}` 环境变量引用（`.mcp.json` 支持 `${VAR}` 与 `${VAR:-default}` 展开）；CI secret 扫描命中即失败。
3. 【强制】生成器模板只产 `.mcp.json`，禁产其他工具的私有 MCP 配置（`.cursor/mcp.json`、`.codex/config.toml`、`.gemini/settings.json` 等）；开发者自用私有配置属个人工具链选择。
4. 【推荐】服务器命令优先 `npx -y <pkg>` / `uvx <pkg>` 免安装形态；需常驻服务（数据库、内网中间件）的 MCP 在 `.mcp.json` 同仓 README 注明前置条件。
5. 【参考】迁移辅助：Claude Code v2.1.213+ 的 `/import` 命令可将 AGENTS.md / MCP / commands / subagents / skills 从其他工具布局迁入——业务仓从别的工具链切换时优先用它，不手抄。

## 二、Skills（可复用技能）

1. 【推荐】技能载体 = **Agent Skills 开放标准**（agentskills.io）：目录制，`SKILL.md` 带 YAML frontmatter（`name` / `description` 必备），渐进披露（名称描述 → 正文 → 随目脚本与参考件）。
2. 【推荐】项目级唯一位置 = **`.agents/skills/<技能名>/SKILL.md`**——跨厂商事实位置（Codex / Zed 唯一采用，Gemini CLI / Cursor / VS Code / opencode 兼容别名，digest C7）。
3. 【推荐】Claude Code 消费走派生：`.claude/skills` 符号链接指向 `../.agents/skills`（macOS/Linux）；Windows 工程或禁符号链接场景，逐技能链接或 Claude Code 插件包装，**禁双份拷贝**。
4. 【推荐】可复用提示词一律 skills 载体，不写 slash commands——各厂 commands 正向 skills 合并/废弃（Claude 已并入、Codex prompts 废弃、VS Code prompt 文件对 Agent Host 废弃、Zed 移除，digest C10）。
5. 【参考】用户级技能（`~/.agents/skills`、`~/.claude/skills`）属个人环境，同 agents-md.md 一-5 不入库。

## 三、Hooks、subagents 与运行产物目录

> hooks / subagents 仍参考级不定强制（理由：hooks 配置位置各家私有（Claude `.claude/settings.json` / Cursor `hooks.json` / Codex `.codex/hooks.json` / Gemini settings.json）；subagents 格式分裂（MD+frontmatter vs TOML vs `.agent.md`），首批不纳管强制只给口径）。运行产物目录禁入库（三-5）为 v1.1 增设的【强制】项。

1. 【参考】hooks 事实标准 = Claude 系事件名 + JSON-stdio 契约（exit 2 阻断），Codex / VS Code 近乎同构；项目确需 hooks 时放 `.claude/settings.json` 并在业务仓 `docs/waivers.md` 登记（豁免流程对齐 README 治理节）。
2. 【参考】`.claude/settings.local.json` 为个人本地覆盖，禁入库（gitignore）。
3. 【参考】subagents 项目自选工具与位置，本规约不定布局；跨工具复用诉求出现时升级评审。
4. 【参考】机检类强制约束（lint / 测试 / 红线扫描）优先进 CI 与 AGENTS.md 命令表，不用 hooks 实现——hooks 是交互期提醒，CI 才是门禁。
5. 【强制】**AI 工作流运行产物目录禁入库**：`.superpowers/`、`.claude/`、`.zcode/`、`.codegraph/`、`.reasonix/`、`.playwright-mcp/` 等 AI 代理运行时产物目录（审阅 diff、修复报告、任务简报、进度日志等运行态文件）一律不入库，私有库与开源库同规；清单开放式，新工具的运行态目录出现即适用。三要件：
   - a) 生成器产出的 `.gitignore` 必含上述目录前缀；经 npm 分发的生成器模板以无点 `gitignore` 存名、生成时改名还原（npm 打包剔除点号 `.gitignore`）；
   - b) `.gitignore` 只拦未跟踪文件——已被跟踪的此类目录须 `git rm -r --cached` 显式移出索引，仅追加 ignore 对已跟踪路径无效；
   - c) 开源发布走快照重建时，树中含上述前缀即视为发布事故，回退重发。
   `.claude/` 整目录禁入库：二-3 符号链接与三-1 hooks 本地配置均为本地派生件，不入库（三-2 settings.local.json 禁入库即本条在 `.claude/` 下的特例）。

## 四、可机检条文清单

> 对齐 [../README.md](../README.md)「机检路线」。

| 条文 | 机检手段 | 阶段 |
|---|---|---|
| 一-1 MCP 唯一入库位置 | 仓内 MCP 配置路径扫描（`.mcp.json` 之外无同类文件入库） | CI |
| 一-2 凭证禁字面量 | secret 扫描（gitleaks 口径）+ `.mcp.json` 值域正则须为 `${…}` 或非敏感项 | CI |
| 二-1 skills frontmatter | `.agents/skills/*/SKILL.md` frontmatter 校验（`name`/`description` 必备） | CI（存在时） |
| 二-3 禁双份拷贝 | `.claude/skills` 存在则必须为符号链接 | CI（存在时） |
| 三-5 运行产物目录禁入库 | `git ls-files` 按前缀扫描（`.superpowers/` `.claude/` `.zcode/` `.codegraph/` `.reasonix/` `.playwright-mcp/`，含嵌套路径），命中即失败；另断言各生成器模板 `.gitignore` 含前缀清单 | CI |

---

## 附：拍板记录

- 拍板口径 A4（2026-09-14）：首批只纳管 `.mcp.json` 与 skills 两处出口，hooks / subagents 参考级不定强制——依据 digest C6（`.mcp.json` 弱事实标准）、C7（`.agents/skills/` 跨厂商位置）、C8/C9（hooks/subagents 位置与格式分裂）。
- 拍板口径（2026-10-08）：增三-5 运行产物目录禁入库【强制】——前缀清单开放式（.superpowers/.claude/.zcode/.codegraph/.reasonix/.playwright-mcp 首批）；`.claude/` 整目录口径覆盖二-3 符号链接与三-1 hooks 本地配置（均本地派生不入库，两条文文字不动）；机检 = `git ls-files` 前缀扫描进 CI + 生成器模板 `.gitignore` 前缀完备性断言。
- 观察哨（触发即复审本规约，清单见 digest 四节）：`.mcp.json` 获更多厂商原生读取；`.agents/skills/` 被 Claude Code 采纳。
