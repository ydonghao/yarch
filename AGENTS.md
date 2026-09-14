# AGENTS.md — yarch 仓工程守则

> yarch = 规约先行的多栈脚手架体系：`contract/` 是唯一权威契约，各栈（stacks/clients/embedded）是实现方言。
> 任何 AI 编码代理改码前先读本文件；条文与直觉冲突时以 `contract/` 为准。本文件结构遵循 [contract/agent/agents-md.md](contract/agent/agents-md.md)（v1.0）。

## 工程地图

| 目录 | 职责 |
|---|---|
| `contract/` | 全域契约唯一权威：api（四件套）/ infra（20 份）/ web / clients / domains / agent + `registry.md` 命名登记处 |
| `stacks/` | 云栈实现：java / golang / web / python（rust 规约已立待施工） |
| `clients/` | 端侧：android / ios / miniprogram / game + `create/` 客户端生成器 |
| `embedded/esp32/` | 固件轨（esp-idf-hal std，规约已立待施工） |
| `docs/` | 架构与发展策划；`docs/references/` = 调研 digest（分析文体的合法归宿） |
| `.github/workflows/` | CI（含 template-smoke 生成器冒烟） |

## 命令表

| 栈 | 验证命令 |
|---|---|
| web | `pnpm -C stacks/web test`（lint：`pnpm -C stacks/web lint`） |
| golang | `cd stacks/golang && go test ./...` |
| python | `cd stacks/python && uv run pytest` |
| java | `cd stacks/java && mvn -q test` |
| android | `cd clients/android && ./gradlew test` |
| ios | `cd clients/ios && swift test` |
| 生成器冒烟 | `npm create @yarch/admin@latest` / `npm create @yarch/app@latest`（CI template-smoke 同款） |

## 红线清单

| 红线 | 规则 | 出处 |
|---|---|---|
| 契约唯一权威 | 改契约必先改 `contract/`（含版本与变更记录）再改实现；实现与契约不一致 = 实现 bug | contract/README.md 治理节 |
| 决策清单先行 | 契约改动先出决策清单供审阅拍板，再成文；拍板口径登记 README 决策表 | contract/README.md 决策登记表 |
| 条文不是分析 | 规约文档只写条文（【强制】/【推荐】/【参考】）；调研与过程进 `docs/references/` digest | contract/README.md 机检路线 |
| 登记先行 | 服务名 / 前端应用名 / App 名 / 小程序与游戏名，先登记 `registry.md` 再施工 | contract/registry.md 一/四/五/六 |
| 一行命令铁律 | 新工程由生成器产出（`npm create` / `go run` 式），禁 cp -r 模板 | contract/agent/cli.md 一-1 |
| AGENTS.md SSOT | `CLAUDE.md`/`GEMINI.md` 仅一行 `@AGENTS.md` 派生；生成器模板必须内置合规三件套 | contract/agent/agents-md.md 一-1/一-2 |
| 共享基础设施 | 全项目共用 yuandonghao-linux 的 PG/Redis/MQ 等，新服务按前缀隔离并登记 | contract/registry.md 头部 + infra/* |

## 契约锚点

- 治理 / 组合依赖 / 决策登记：[contract/README.md](contract/README.md)
- 命名登记处：[contract/registry.md](contract/registry.md)
- API 四件套：[contract/api/](contract/api/)；infra：[contract/infra/](contract/infra/)；前端：[contract/web/micro-frontend.md](contract/web/micro-frontend.md)
- 客户端：[contract/clients/](contract/clients/)；领域：[contract/domains/device.md](contract/domains/device.md)；AI 施工：[contract/agent/](contract/agent/)

## 完成定义（DoD）

契约改动：决策清单 → 拍板 → 条文成文 → README 决策表与变更记录登记。实现改动：对应栈测试绿 + CI 绿；生成器改动：template-smoke 绿。
