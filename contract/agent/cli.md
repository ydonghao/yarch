# cli · yarch 自家 CLI 交互规约（v1.0 已定稿）

> **状态：已定稿**（2026-09-14 经 A1-A6 拍板口径成文，A6 条：一行命令铁律成文化）。
> 等级：**【强制】**违反即缺陷；**【推荐】**默认遵守、评审后可豁免；**【参考】**正向引导。
> 约束对象：yarch 体系全部生成器/工具 CLI——`@yarch/create-admin`、`@yarch/create-app`（bin `yarch-init-app`）、golang `cmd/yarch-init`、远期 `yarch lint` 与未来新增成员。生态通道非自家 CLI（rust 的 cargo-generate）不受本规约约束。
> 协作参考（软）：[agents-md.md](agents-md.md)（生成物须含合规 AGENTS.md 三件套）、[extensions.md](extensions.md)（生成物扩展出口）。

## 一、一行命令入口（铁律成文）

1. 【强制】建工程必须**一行命令式入口**：npm 侧 `npm create @yarch/<域>@latest`（对应包名 `@yarch/create-<域>`）；go 侧 `go run <module>/cmd/yarch-init@latest`；**cp -r 模板不可接受**。
2. 【强制】家族命名标准化：npm 包名 `@yarch/create-<域>`，bin 名 `yarch-init-<域>`；存量 bin `create-admin` 作别名保留（发版兼容），新成员不再另起 bin 名。
3. 【强制】命名分组随 contract 域走（web / app / …），新增分组先入六节登记表再施工；禁裸通用词（呼应 [../registry.md](../registry.md) 一-2）。

## 二、参数与交互

1. 【强制】flags 一律 kebab-case（`--platform`、`--template`、`--yes`）；位置参数 ≤1（工程名）。
2. 【强制】**交互模式默认对人，全非交互对机**：`--yes`（接受全部默认值）/`--defaults` 下禁任何 prompt 阻塞；非交互模式缺必需参数即报错退出（exit 2），不降级回交互。CI 必须能一行跑通。
3. 【推荐】`--help` 输出：选项并排 + 一行命令示例打头（可扫读，同说明书写法铁律）。
4. 【推荐】参数冲突一次性全部报出，不修一个报一个。

## 三、输出

1. 【强制】默认人读：分步摘要 + 结束时「下一步三行」（cd / install / run）。
2. 【强制】`--json` 机读模式：结果单对象走 stdout，一切人读信息（进度/警告/下一步）走 stderr——不污染管道。
3. 【推荐】日志分级 `--quiet` / 默认 / `--verbose`；错误信息带可执行的下一步指引，禁裸堆栈糊脸。

## 四、退出码

1. 【强制】三档定式：`0` 成功；`1` 执行期失败（网络/模板/写盘/子命令）；`2` 用法错误（参数非法/缺参/组合冲突）。≥3 保留给未来细分，现状禁占。

## 五、幂等与覆盖

1. 【强制】目标目录已存在且非空：默认拒绝并提示；`--force` 显式覆盖。
2. 【强制】同参重跑幂等：第二次运行产出与第一次一致（生成器内时间戳/随机量须可复现或与内容分离）。
3. 【推荐】生成物 README/AGENTS.md 注明生成器版本（排障锚点）。

## 六、成员登记表

> 新 CLI 成员入表即受本规约全条文约束；改 bin 名 = 发版兼容评审。

| CLI（bin） | npm 包 / 入口 | 覆盖域 | 状态 |
|---|---|---|---|
| `create-admin`（存量别名，新规名 `yarch-init-admin` 待收编） | `@yarch/create-admin` / `npm create @yarch/admin@latest` | web 前端 | 0.2.0 已发版 |
| `yarch-init-app` | `@yarch/create-app` / `npm create @yarch/app@latest` | clients（android/ios/miniprogram/game-cocos，`--platform` 分档） | 仓内已交付，待发版 |
| `yarch-init` | golang `cmd/yarch-init` / `go run …@latest` | golang 栈 | 存量；远程零 clone 改造为 backlog |
| `yarch lint`（远期） | 未定 | 全契约机检 | 规划 |

## 七、可机检条文清单

> 对齐 [../README.md](../README.md)「机检路线」。落地载体：生成器自身 e2e + CI template-smoke。

| 条文 | 机检手段 | 阶段 |
|---|---|---|
| 二-2 非交互零 prompt | CI 一行 `--yes` 跑通全模板（template-smoke 已有雏形） | CI |
| 三-2 stdout 纯净 | `--json` 输出 jq 可解析；stderr/stdout 分离断言 | e2e |
| 四-1 退出码三档 | 参数错误用例断言 exit 2；执行失败用例断言 exit 1 | e2e |
| 五-1 非空目录拒绝 | 已存在目录用例断言拒绝 + `--force` 断言覆盖 | e2e |
| 五-2 幂等 | 同参双跑 diff 为空 | e2e |

---

## 附：拍板记录

- 拍板口径 A6（2026-09-14）：一行命令铁律（口头/memory 约定）正式成文，并补全非交互、`--json`、退出码、幂等四组条文；命名现状（`create-admin` 与 `yarch-init-app` 双约定并存）按"存量别名 + 新成员统一"收口。
- 落地 backlog：golang 远程零 clone 改造（须先改生成器，memory 既有项）；`create-admin` bin 收编为 `yarch-init-admin` 别名（随下个 minor 发版评审）。
