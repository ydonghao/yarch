# yarch 发展策划（2026-09 → 2026-12）

> 基于 [references/market-landscape-digest.md](references/market-landscape-digest.md)（2026-09 四战场调研）与仓内现状。本文是**方向与优先级文档**——列出按序施工的宏观判断；每项进入实施前仍按 [../contract/README.md](../contract/README.md) 机检路线与 [[user-spec-first-workflow]] 先出决策清单，本文不定稿条文。

## 一、定位修订（调研推导）

```
旧：云-边-端全域工程架构平台（契约规约 → 各栈方言实现）
新：AI 时代多栈工程的「契约底座」——规约可执行化，出口到 AI 编码代理
```

市场事实（2026-09）：SDD 已是主流工作流（Spec Kit 55k★），AGENTS.md 是 60k+ 项目、30+ 代理读取的事实标准（Linux Foundation AAIF 锚定）。**约束 AI 编码代理的载体正在标准化，而 yarch 的契约体系（29 份 v1.0 + 机检）恰好是这种约束的最强供给方**。这是 yarch 区别于 JHipster/芋道/ruoyi 的结构性护城河——它们管「生成那一刻」,yarch 管「生成之后的每一次 AI 改码」。

**策略一句话：把契约从「写给人看的规约」升级为「人与 AI 共同消费的机器工件」。**

**目标场景（2026-09-16 EP1 拍板，企业级定位修订）**：**企业级应用/游戏交付**——定位句与护城河判断不变，场景改变优先级：**realtime > 可观测（含审计）> 埋点管道** 三轨立项（EP9 序），多租户/i18n 等既有「不做」边界细化登记于 [../contract/README.md](../contract/README.md) 决策表 EP 行（依据 [references/enterprise-capability-gap-digest.md](references/enterprise-capability-gap-digest.md)）。

## 二、五个支柱（按依赖序）

### 支柱 1 · 契约机器可读出口（**主引擎**,market digest N2 升级为主线）

**进展（2026-09-17）**：P1 主体落地——`contract/dist/` 派生层开张（error-codes.json 含 `{code,key,message}` 三元组即 EP3-R 就绪位 + envelope.schema.json，gen-dist.mjs 从 markdown 表格生成）+ contract-dist CI 漂移门 + **四栈 conformance 改读同一份 json**（java/golang/python/web，缺文件优雅跳过）；N1 三栈 AGENTS.md 三件套收口（java 双 archetype 走 unfiltered fileSet 绕 Velocity `##` 注释坑 + golang/python _template + web 五模板补 CLAUDE/GEMINI 派生）。

剩余：远期挂档 OpenAPI 骨架导出（N5，API-first 工作流，前后端并行开发 mock——JHipster 标配，yarch 暂挂）。

### 支柱 2 · AI 代理出口（market digest N1 的剩余部分 + N3）

- N1 已完成（web 栈）：五模板 + 三示例内置 `AGENTS.md` 工程守则（信封/错误码红线 + 分层方向 + 微前端档载器归属/事件名/storage 前缀 + 双模式铁律 + 必跑命令 + DoD）；
- **N1 剩余**:`stacks/java`（archetype 模板）、`stacks/golang`（yarch-init `_template`)、`stacks/python`(yarch-init `_template`）三栈生成器补同款 `AGENTS.md`——内容 = 各栈机检条文的直接翻译（Java：ArchUnit 分层禁反向、13 码常量、Spotless 4 空格；Golang/Python：depguard/import-linter、契约内核零框架、ndjson 口径）;
- N3：仓根本仓 `AGENTS.md`(yarch 自身 dogfood：规约先行工作流、pathspec 提交纪律、验证命令）;
- 演进方向：从「AGENTS.md 静态守则」向「**契约条文 → AI 审查清单**」(contract/README 机检路线的既定目标 `yarch lint` / AI 审查清单）——条文标「可机检」清单落成结构化清单供代理逐条核对。

### 支柱 3 · 全端生成器统一（clients/create 的收敛）

现状：web `create-admin`、golang `yarch-init`、python `yarch-init`、clients `yarch-init-app`(`--platform miniprogram|game-cocos|android|ios|both`）已是四个生成器，逻辑同源（{{var}} 渲染引擎 + registry 校验 + 残留扫描）但散落三处。

方向：收敛为**一个 CLI、多平台**——`yarch init --platform <web|java|golang|python|android|ios|miniprogram|game-cocos|both>`,registry 校验与模板资产单一来源。低优先：现状各生成器已可用，统一是发现性与维护成本的优化而非功能缺口；裸名 `yarch` 的 PyPI 坐标已预留（python 策划案已查净）。

### 支柱 4 · 维护健康（不新增功能，保住既有资产信用）

- **web 0.2.0 / 0.3.0 发版**:dev 分支四包已 bump 0.2.0（微前端档）,S1 三端化走 0.3.0（未 commit)——合并 main 后推 tag `stacks/web/v0.2.0` → web-publish.yml；模板自身 version 字段 0.1.0 未跟（生成工程版本语义待拍板）;
- **python 发版**：推 tag `stacks/python/v0.1.0` → PyPI trusted publishing（发版日人工前置：pypi.org 两项目 trusted publisher 声明）;
- **存量 P2 backlog 清偿**(python 终审 20 条 + 评审遗留：http.ts 超时、限流调用方维度、幂等回放 Location、InMemoryIdempotencyStore 无界等）——列 GitHub issues 分批消化；
- **微前端观察哨条文补强**(N4):`micro-frontend.md` 附则补「1.0 长期 RC 无 GA」与「持续停更」并列触发条件（市场事实：rc.32 仍两个月前发布，默认档维持，但长期 RC 是新风险信号）。

### 支柱 5 · 企业级能力轨（2026-09-16 EP 立项，按 EP9 序）

- **三轨契约全部成文（2026-09-16 两批拍板）**：第一轨 [realtime](../contract/api/realtime.md)（RT1-RT9）+ 第二轨可观测（OB1-OB9：logging-trace v1.1 spanId/租户行、[audit](../contract/api/audit.md) 审计留存、[prometheus](../contract/infra/prometheus.md) + [grafana](../contract/infra/grafana.md) 新 infra 两份）+ 第三轨 [telemetry](../contract/api/telemetry.md)（TM1-TM8）；EP2-R/EP3-R 随批落地（租户传播行 + 错误码稳定 key 就绪位）；client-shared 增六/七节；
- **实现全部触发式**：首个消费工程立项时施工——客户端内核首批 web + miniprogram + cocos（RT9/TM6 对齐，realtime 与 telemetry 同批摊成本），服务端装配件与观测栈 compose 资产随企业级试点；
- **随批施工项（2026-09-16 已落地）**：EP5 @SignedApi/Masks 跨栈对齐（golang + python）、EP6 CI 安全扫描（Dependabot + CodeQL）；
- **后置登记**：通知通道领域契约（domains/notification.md，三轨后排队 EP7）；支付协议层（EP8）与桌面端（EP10）触发式。

## 三、路线图（市场窗口驱动，约 90 天）

| 阶段 | 内容 | 完成信号 |
|---|---|---|
| **P0 守底（合并即跑）** | dev 分支合 main（微前端 0.2.0 + 移动端规约/模板/生成器 + AGENTS.md 全量）;python 推 tag 发版；web 0.2.0 发版 | 四条 CI 全绿 + 三栈一行命令零 clone 可用 |
| **P1 一致性底座（2-3 周）** | 支柱 1 契约 dist（error-codes.json + envelope schema）+ 四栈 conformance 改同源；N1 剩余三栈 AGENTS.md；仓根 AGENTS.md(N3) | 四栈断言同一份 json;`AGENTS.md` 覆盖全部生成器出口 |
| **P1.5 企业级第一轨（与 P1 并行，契约批已落）** | realtime.md v1.0 成文 + client-shared 六节（2026-09-16）；EP5 签名/脱敏跨栈对齐 + EP6 CI 安全扫描；首个消费工程触发实现 | 五端契约断言 + 三端内核冒烟（触发时）；四栈 EP5 对齐件测试绿 |
| **P2 领域兑现（3-4 周）** | device.md + ai.md 领域契约成文（microduck 合体验证候选）；存量 P2 backlog 消化 | microduck 云端 Go + esp32 固件同表实现跑通 |
| **P3 生态收敛（滚动）** | yarch 统一 CLI（支柱 3);OpenAPI 导出（N5 视 P1 进展）；微前端观察哨条文补强 | `yarch init --platform` 一条命令起任意端 |

## 四、风险与观察哨

| 风险 | 信号 | 预案 |
|---|---|---|
| micro-app 长期 RC 无 GA / 停更 | 连续 6 个月无发布 | 默认档切 qiankun（规约已有观察哨条文，N4 补强为并列条件） |
| AGENTS.md 写空泛反噬代理成功率（社区反方研究） | 生成工程实测代理改码违规率 | 守则只写「消除协调的可机检约束」，禁写泛化指南（已按此形态落地） |
| 契约 dist 双写漂移 | markdown 改而 json 未重新生成 | CI 校验 dist 与 markdown 一致性（同 registry-snapshot 现有机制） |
| 生成器四份逻辑漂移 | 同一 registry 规则多处维护 | 支柱 3 统一 CLI 时收编 |

## 五、不做什么（明确裁撤）

- **不自建 SDD 流程框架**（Spec Kit 已有）——yarch 只供给契约工件，不抢流程层；
- **不做业务中台**（芋道/ruoyi 的业务模块：工作流/支付通道/字典/监控页）——yarch = 架构平台，G7-G10 拍板已定；**多租户按 EP2（2026-09-16）拆两层**：账号模型归业务仓（ysaas），租户上下文传播机制契约（header → 日志 → trace）归 yarch、随可观测轨批出决策清单；
- **dotnet/php 不回摆**（2026-09-03 裁撤）;
- **商城领域契约不立**（2026-09-11 拍板）;
- **rust/embedded 不预写**——触发登记制不变（device/ai 契约成文后 esp32 随 microduck 触发）。

---

**给审查者的提示**：本策划只改方向不改成文——P1 的契约 dist 方案与 N4 条文补强进入实施前，按工作流先出决策清单供拍板（变更流程见 contract/README.md「变更流程」)。
