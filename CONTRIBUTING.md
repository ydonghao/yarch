# 贡献指南

感谢关注 yarch。本文告诉你怎么报问题、怎么提 PR、以及这个仓库的铁律。

## 这个仓库的铁律（先读这个）

1. **契约唯一权威**：[contract/](contract/README.md) 是所有栈实现的唯一权威来源。改行为必须先改契约（含决策记录），再改实现——**实现与契约不一致即 bug**。
2. **不含业务语义**：平台构件里出现任何具体业务（订单/租户/…）就是越界，错误码 3xxx-8xxx 段归业务工程，不得进平台件。
3. **机检必须全绿**：PR 触发的 CI（java/python/web/golang 四条流水线）必须全绿才可合入。

## 环境（按栈按需）

| 栈 | 位置 | 前置 | 验证命令 |
|---|---|---|---|
| Java | `stacks/java/` | JDK 21+（CI 双验 21/25）、Docker | `mvn -f stacks/java/pom.xml verify` |
| Python | `stacks/python/` | uv、Python 3.12+、Docker | `cd stacks/python && uv sync --all-packages && uv run pytest` |
| Web | `stacks/web/` | Node 20+、pnpm | `cd stacks/web && pnpm install && pnpm check` |
| Golang | `stacks/golang/` | Go 1.24+、Docker | `cd stacks/golang && go test ./...` |

> 集成测试（Testcontainers）需要本机 Docker 可用。

## 报问题

提 issue 请使用模板（bug / feature），bug 尽量带上：

- 复现步骤与最小工程（哪个栈、哪个构件、版本号）；
- ndjson 日志行与 `traceId`——本项目的排障凭证就是它。

安全漏洞**不要开公开 issue**，走 [SECURITY.md](SECURITY.md)。

## 提 PR

1. fork → 分支（`feat/xxx`、`fix/xxx`）→ 改动；
2. 提交信息沿用 conventional commits 风格（`feat(stacks/python): …`、`fix(contract): …`，参照 git log 现有习惯）；
3. 改了行为 → 先确认 [contract/](contract/) 是否需要同步改（含对应栈的防漂移断言测试）；
4. 本地跑对应栈的验证命令（见上表），CI 全绿后请求评审。

PR 模板见 [.github/PULL_REQUEST_TEMPLATE.md](.github/PULL_REQUEST_TEMPLATE.md)。

## 文档与语言立场

- 文档以中文为主，代码标识符/日志字段/契约 JSON 字段一律英文（camelCase）；
- 规约类文档写**条文**（【强制】/【推荐】/【参考】），不写分析文章——参照 [contract/infra/celery.md](contract/infra/celery.md) 的形态。

## 许可

提交即表示你同意以 [Apache-2.0](LICENSE) 许可发布你的贡献。
