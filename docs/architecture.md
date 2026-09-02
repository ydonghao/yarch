# yarch · 工程架构设计

> Yuan's Architecture —— 多栈工程架构体系：一种规范，多种方言。  
> 2026-08-31 定稿（本文件是工程结构的权威落盘）

## 一、定位

yarch 是跨项目复用的**工程架构平台**（个人开源项目）：

- **钢筋水泥厂**：规范的可执行化（parent 插件 / CI 门禁 / archetype 固定结构），而非文档自觉；
- **AI 施工的模具**：skill 管规范、模板管结构，AI 生成代码不漂移；
- **发版式升级**：平台构件走 version 变更，业务工程自动跟进，不手工同步。

ysaas 是第一个客户；任何 Java/Go/Rust/前端工程都可以是客户。

## 二、核心设计：契约层（contract/）

多栈脚手架的灵魂不是每栈一套模板，而是**语言无关的统一契约**——各栈长得不一样没关系，必须说同一种接口语言：

| 契约 | 内容 | 收益 |
|---|---|---|
| RestResponse 形状 | code/message/data/traceId 的 schema | Java 与 Go 返回同构，前端一套适配 |
| 错误码全局段位 | 一张跨语言 errno 段位表（如 1xxx 通用、2xxx IAM…） | yagent 报错与 ysaas 报错语义同源 |
| 日志 + trace | 统一 JSON schema + traceId 贯穿（OpenTelemetry 口径） | 跨栈排障一条链拉通 |
| REST 约定 | 命名/分页/状态码 | 各栈 API 无方言 |

契约层已扩展收录数据与中间件规约：`infra/` 共 18 份（索引见 [../contract/README.md](../contract/README.md)）**全部 20 份已定稿 v1.0（2026-09-01 评审通过；higress 已对照官方文档校准）**：mysql、postgresql、mongodb、redis、redisearch、timescale、pgvector、qdrant、milvus、kafka、rocketmq、nacos、nginx、higress、elasticsearch、clickhouse、starrocks、minio-s3、xxl-job、celery；规约组合与依赖模型（前置硬 / 参考软 / 互斥分工）见 [../contract/README.md](../contract/README.md)。关键架构决策（MQ 双轨 / OLAP 双引擎 / 向量起步 pgvector / 网关分工 / 默认 PostgreSQL）与规约治理（等级定义、豁免与变更流程）**唯一登记处为 [../contract/README.md](../contract/README.md)**；服务名（租户边界标识）登记处为 [../contract/registry.md](../contract/registry.md)。

## 三、仓库结构

```
yarch/
├── contract/                  # 跨栈契约与规约（API 四件套 + mysql/postgresql/redis 规约）
├── stacks/                    # 栈模板层（构件发各自生态）
│   ├── java/                  # yarch-java：parent/dependencies/common/framework/archetype → Maven Central
│   ├── golang/                # yarch-golang：Hertz+DDD → Go module（从 yagent 反向沉淀）
│   ├── rust/                  # yarch-rust：axum+DDD → crates.io
│   ├── web-react/             # yarch-react → npm
│   └── web-vue/               # yarch-vue
├── clients/                   # 客户端预留位（按项目需要生长）
│   ├── mobile/                # uni-app / Flutter
│   ├── miniprogram/           # 小程序
│   └── desktop/               # Tauri（Rust+Web，兼 Rust 练手落点）
├── tools/
│   └── locate-scaffolds.cjs   # 模板定位器（将来长成 yarch init CLI）
└── docs/                      # 工程规范
```

## 三·五、架构图

- 平台总览：[architecture-diagram.png](architecture-diagram.png)（contract → stacks → 发布生态 → 客户工程）
- 各栈脚手架详细图（每栈一张，随栈 README 展示）：
  - Java：[../stacks/java/architecture-diagram.png](../stacks/java/architecture-diagram.png)（五层架构：业务工程 / Web 装配 / 平台构件 / 统一契约 / 运行底座 + 发版与升级链路，coli-architecture 风格）
  - Golang：[../stacks/golang/architecture-diagram.png](../stacks/golang/architecture-diagram.png)（pkg 构件 + Hertz 请求生命周期）
  - Rust：[../stacks/rust/architecture-diagram.png](../stacks/rust/architecture-diagram.png)（crate 模块 + axum 接入与请求流）
  - web-react：[../stacks/web-react/architecture-diagram.png](../stacks/web-react/architecture-diagram.png)（包模块 + 一次请求的数据流）

## 四、横切件分界线（yarch vs 业务工程）

| 归宿 | 收什么 | 判断标准 |
|---|---|---|
| **yarch**（通用） | 日志、错误码/RestResponse、幂等/锁、审计埋点框架、测试基座、CI 模板、DDD archetype | 任何工程都需要，不带业务语义 |
| **业务工程**（如 ysaas） | 领域横切，如 ysaas-tenant-spring-boot-starter（tenant_id 拦截器）、INV 不变式门禁、配额原语 | 一出现就带领域语义 |

铁律：yarch 不得包含任何 SaaS/租户语义。

## 五、命名规范

- starter 一律第三方式后缀：`yarch-logging-spring-boot-starter`（官方 `spring-boot-starter-*` 是保留位，勿占）；
- groupId：`io.github.yuandonghao`（Central Portal 经 GitHub 验证；将来域名到位换 `dev.yarch`）；
- 四生态坐标（GitHub / Maven Central / npm / crates.io）`yarch` 均查净（2026-08-31）；GitHub 唯一同名为 alfa-laboratory 的 iOS 架构（小写 yarch + topics 消歧）。

## 六、施工节奏（跟项目走，不为完整性铺摊子）

**当前策略（2026-09-01 调整）：规约层先行。**

1. ~~第一步：`contract/` 四件套梳理清楚、评审定稿~~ ✅ 2026-09-01 定稿 v1.0（D1-D6 按"业界标准优先于阿里手册"拍板）；infra 规约 18 份同步成文（3 定稿 + 15 草案）；
2. **第二批**：`stacks/java` + `stacks/web-react`（ysaas 硬需求）；
3. **第三批**：`stacks/golang`——从 yagent 既有实践反向沉淀，不重写；
4. **按需**：rust / web-vue / clients / archetype（`yarch init`）。

> 2026-08-31 曾提前落地过四栈脚手架实现（构建与测试全绿），为聚焦规约层已清空 stacks/，定稿后按契约重做。

## 七、与 ysaas 的关系

ysaas 单仓库，经 parent/BOM 引用 yarch-java 的 Maven 构件——采用者 clone ysaas 构建时自动拉件，无需关心第二个 git 仓库。license 建议 yarch = Apache-2.0（脚手架最大化采用；ysaas 核心另议 AGPL+SDK Apache 分层）——**待最终拍板**。
