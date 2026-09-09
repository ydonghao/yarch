# 市场全景调研 digest（SDD · 生成器 · 微前端 · 跨端，2026-09）

> **定位**：服务于 2026-09「规约可执行化 → AI 代理出口」方向决策的市场调研与审阅材料。**规范性条文一律以 [../../contract/](../../contract/README.md) 为准**，本组不承载权威条文。决策清单（N1-N5）见文末。

## 调研范围与一句话结论

四个与 yarch 直接相关的战场，2026-09 市场状态与含义：

| 战场 | 市场现状（一句话） | 对 yarch 的含义 |
|---|---|---|
| Spec-Driven 开发 / AI 编码 | SDD 已成主流工作流（Spec Kit 55k★），AGENTS.md 成 AI 代理事实标准（60k+ 项目、Linux Foundation AAIF 锚定） | yarch「规约可执行化」正处风口，但契约目前**只约束人机检、不出口给 AI 代理**——最大机会窗口 |
| 多栈全栈生成器 | JHipster 验证了「生成器+生态」模式（220 蓝图、OpenAPI-first），JHipster 9 升 Spring Boot 4 | yarch 三档 UI ≈ 蓝图雏形；**契约机器可读出口**（OpenAPI/JSON Schema）是国际市场标配而 yarch 缺位 |
| 微前端载器 | micro-app 仍维护（1.0.0-rc.32，两个月前发布）但**长期 RC 无 GA**；qiankun 2.10 健康活跃（周下载 ~4.8 万） | 规约「观察哨」**未触发**，micro-app 默认档维持；观察哨条文继续有效 |
| 移动端跨端 | Flutter/RN/KMP 三强格局，KMP 上升最快（企业 native-first 团队）；原生开发未被替代 | M1-M7 原生双轨拍板与市场「native-first + 共享逻辑层」趋势一致，无需改判 |

## 一、Spec-Driven 开发与 AI 编码代理（主战场）

**事实**：

- [GitHub Spec Kit](https://github.com/github/spec-kit)（2025-07 发布）把 SDD 推成主流，55k+ stars；2026 年中「每个主流编码工具都内建了某种 SDD 流程」（[BCMS 2026 指南](https://www.thebcms.com/blog/spec-driven-development/)、[ProductBuilder](https://productbuilder.net/learn/spec-driven-development)）；
- [AGENTS.md](https://agents.md/)（2025-08 成文）已是 **60,000+ 开源项目**、30+ 编码代理（Codex / Claude Code / Copilot / Cursor / Gemini CLI / Jules…）共同读取的事实标准；2025 年末进入 **Linux Foundation Agentic AI Foundation**（与 MCP 并列锚定项目，OpenAI/Anthropic/Block 共同发起，[AAIF 公告](https://aaif.io/news/linux-foundation-announces-formation-of-aaif)）；
- 社区反方研究同样明确：AGENTS.md **写空泛指南反而降低代理成功率**，有效的前提是「编码消除协调成本的约束」——错误码表、命名规则、分层禁反向这类可机检条文恰是正确形态。

**与 yarch 的关系**：yarch 痛点表第五条「AI 生成代码结构漂移——模板管结构、契约管行为」过去只有前半句落地（模板 + 机检），契约对 AI 代理没有出口。市场已把「仓库根 AGENTS.md」变成代理读取的标准入口。

**已落地（随本批）**：五份脚手架模板（admin 三档 + base/sub 微前端档）与三份仓内示例全部生成 `AGENTS.md`——内容全部是「消除协调的约束」形态：信封/错误码红线、分层方向、（微前端档）载器归属/事件名/storage 前缀/双模式铁律 + 生成后必跑命令 + DoD。

## 二、多栈全栈生成器（JHipster 对标）

**事实**（[JHipster 8.10](https://www.jhipster.tech/2025/03/31/jhipster-release-8.10.0.html)、[Marketplace](https://www.jhipster.tech/modules/marketplace/)、[API-first 文档](https://www.jhipster.tech/documentation-archive/v8.4.0/doing-api-first-development/)）：

- 蓝图生态 220 个模块（Quarkus/Micronaut/Kotlin/Svelte/Ionic…），核心教训：**生成器单点强不难，「可扩展的生成器生态」才构成壁垒**；
- OpenAPI-first 是其标准工作流：契约文件 → 服务端/客户端代码生成 + 开发期 mock——**契约是机器可读工件**，不是散文；
- JHipster 9（2026 初）升 Spring Boot 4 + Angular 21——yarch java 栈（Boot 4.1）无代差。

**与 yarch 的关系**：yarch 的契约层（25 份规约）比 JHipster 的「生成时选项」深得多，但**契约只有 markdown 形态，没有机器可读出口**。四栈 conformance 测试各自维护错误码表/信封断言，与 contract/ 的对齐靠人肉同步——这正是 JHipster 用 OpenAPI solve 掉的问题。

## 三、微前端载器生态（规约观察哨复审）

**事实**（[micro-app GitHub](https://github.com/jd-opensource/micro-app)、[npm @micro-zoe/micro-app](https://www.npmjs.com/package/@micro-zoe/micro-app)、[qiankun npm](https://www.npmjs.com/package/qiankun)）：

| 载器 | 最新版 | 活跃度 | 评估 |
|---|---|---|---|
| micro-app（京东） | 1.0.0-rc.32（≈2 个月前） | 维护模式：有发布、无 GA——1.0 已在 RC 循环多年 | 规约默认档维持；**「长期 RC 无 GA」记为新风险信号**（与「持续停更」并列观察） |
| qiankun（蚂蚁） | 2.10.16 + 3.0 roadmap | 健康活跃，周下载 ~4.8 万 | 存量档定位不变，具备随时接默认档的成熟度 |
| wujie（腾讯） | — | 维护活跃度不足（规约已裁定不入册） | 维持不入册 |

**结论**：`micro-frontend.md` 载器分档拍板（2026-09-07）**无需改判**；观察哨条文建议补一笔「1.0 长期 RC 无 GA」为并列触发条件（N4，随下次规约修订）。

## 四、移动端跨端格局（M1-M7 复核）

**事实**（[2026 跨端对比](https://stora.sh/blog/2026-04-03-react-native-vs-flutter-vs-kotlin-multiplatform-2026)、[KMP 势头分析](https://blog.devgenius.io/kotlin-multiplatform-is-eating-react-native-and-flutter-e3a655efa9e3)）：Flutter（Impeller 渲染一致性）/ RN / KMP 三强；KMP 增长最快，主打「共享业务逻辑 + 原生 UI」，受 native-first 企业团队青睐；纯跨端替代原生开发的论调退潮。

**与 yarch 的关系**：M1-M7 拍板的原生双轨（Google 官方基准 / Airbnb 风格）与市场「native-first」主线一致；将来若出现跨端诉求，KMP（共享逻辑层 + 原生 UI）与 yarch 现有双端规约**可叠加**（KMP 共享层约等于 contract/clients/client-shared 的代码化），不需要推翻现有拍板。

## N 系决策清单（市场调研产出，供评审）

| # | 决策 | 建议 | 状态 |
|---|---|---|---|
| N1 | **脚手架生成 AGENTS.md**（AI 代理施工守则：契约红线 + 必跑命令 + DoD） | 采纳——web 栈五模板 + 三示例随本批落地；java/python/golang 生成器补齐同款（各栈 AGENTS.md 内容由各栈机检条文直接翻译） | web 栈已落地；其余栈待拍板 |
| N2 | **契约机器可读出口**：`contract/dist/` 生成 error-codes.json + 信封 JSON Schema（唯一权威仍是 contract/ markdown，json 为派生物），四栈 conformance 测试改为读同一份 json | 建议采纳——消除四栈断言表人肉同步，是「规约可执行化」的下一块基石；先出决策清单评审落位与生成方式 | 待拍板 |
| N3 | 仓库根 AGENTS.md（yarch 自身 dogfood：规约先行工作流、提交纪律、验证命令） | 建议采纳，随 N1 后续批次 | 待拍板 |
| N4 | micro-frontend.md 观察哨条文补「1.0 长期 RC 无 GA」并列触发条件 | 建议采纳，随下次规约修订顺带（非破坏性条文补强） | 待拍板 |
| N5 | OpenAPI 导出（契约 → OpenAPI schema → 生成/mock，JHipster 式 API-first 工作流） | 远期——依赖 N2 先行；对 yarch 价值在「前后端并行开发 mock」，暂不立项 | 挂起 |

## 来源

- [github/spec-kit](https://github.com/github/spec-kit) / [GitHub Blog：Spec-driven development](https://github.blog/ai-and-ml/generative-ai/spec-driven-development-with-ai-get-started-with-a-new-open-source-toolkit/) / [TheBCMS：Definitive 2026 Guide](https://www.thebcms.com/blog/spec-driven-development/)
- [agents.md](https://agents.md/) / [InfoQ：AGENTS.md Emerges as Open Standard](https://www.infoq.com/news/2025/08/agents-md/) / [Linux Foundation AAIF 公告](https://aaif.io/news/linux-foundation-announces-formation-of-aaif)
- [JHipster 8.10.0](https://www.jhipster.tech/2025/03/31/jhipster-release-8.10.0.html) / [JHipster Marketplace](https://www.jhipster.tech/modules/marketplace/) / [API-first development](https://www.jhipster.tech/documentation-archive/v8.4.0/doing-api-first-development/)
- [jd-opensource/micro-app](https://github.com/jd-opensource/micro-app) / [@micro-zoe/micro-app npm](https://www.npmjs.com/package/@micro-zoe/micro-app) / [qiankun npm](https://www.npmjs.com/package/qiankun)
- [React Native vs Flutter vs KMP 2026](https://stora.sh/blog/2026-04-03-react-native-vs-flutter-vs-kotlin-multiplatform-2026) / [KMP vs RN vs Flutter（Java Code Geeks）](https://www.javacodegeeks.com/2026/02/kotlin-multiplatform-vs-flutter-vs-react-native-the-2026-cross-platform-reality.html)
