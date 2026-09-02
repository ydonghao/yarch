# stacks/java · yarch-java 脚手架策划案

> **状态：第一批施工完成（2026-09-01），J1–J8 全部拍板并落地**。拍板记录：J1=D 双模板；J2=A（MyBatis-Plus 3.5.17 boot4-starter + jsqlparser 构件）；J3=双形态（DDD 模块化 + 简单贫血）+ Boot 4.1.1 + 工具链 JDK 25 + 字节码 release 21；J4=A 细粒度（Boot 4 同构目录分组）；J5=按建议（幂等第一批、鉴权触发式）；J6=全采纳（Lombok provided + MapStruct + springdoc 默认 + Knife4j optional 皮肤）；J7=三件全做（契约断言/ArchUnit/CI）；J8=按建议（maven-archetype + registry 校验）。
>
> **验收记录（2026-09-01，本机 Temurin 25.0.4 + OrbStack）**：① 全 reactor `mvn verify` 11/11 SUCCESS；② 契约断言全绿（错误码 13 码全表 / 信封形状 / ndjson 行协议字段级 / PG ORM G1-G10 行为级 / Redis key·锁·幂等）；③ 双 archetype 生成工程各 9/9 全绿（信封、201、traceId 贯穿、分页 D6 越界、1001 校验、逻辑删除 + 部分唯一索引放行、幂等回放/1007、业务码 3001、ArchUnit 分层机检）；④ CI 就绪（.github/workflows/java-stack.yml：JDK 21/25 双矩阵 + 双 archetype 冒烟）。
>
> 输入：[contract/](../../contract/README.md) 24 份定稿规约（唯一权威）· [docs/architecture.md](../../docs/architecture.md)（既定框架）· coli-architecture 参考（用户沉淀的 coli-java 技能 DDD 七包分层 + coli-develop-platform 平台文档纪要；源码在内网 GitLab submodule 未检出）· [alibaba-java-manual-digest.md](../../docs/references/alibaba-java-manual-digest.md) A1–A7 吸收清单。

## 一、定位与既定约束（来自 architecture.md 与 contract，本策划不再议）

1. yarch-java = 平台构件仓（Maven 多模块单 reactor），最终发 Maven Central；ysaas 经 parent/BOM 引用，clone 即拉件。
2. 坐标：groupId `io.github.yuandonghao`；版本 **0.1.0 起步**（0.x 不承诺兼容，阿里"必须 1.0.0 起"不采用，digest A5 已定）；starter 命名 `yarch-{功能}-spring-boot-starter`（第三方式后缀，官方 `spring-boot-starter-*` 是保留位）。
3. 铁律：不含任何业务语义（租户/SaaS/INV 语义一律归业务仓）。
4. 实现与 contract/ 不一致即 bug。术语对照已由契约锁定：`RestResponse<T>` / `PageData<T>` / `GlobalErrorCode` / `BusinessException` / `TraceIdFilter`(MDC)；`yarch-logging-spring-boot-starter` 的存在已被 logging-trace.md「各栈实现锚点」点名。
5. 横切件归属（architecture.md 四）：日志、错误码/信封、幂等/锁、测试基座、CI 模板、DDD archetype 归 yarch。
6. 施工批次（architecture.md 六）：java 属第二批（与 web-react 并行），跟项目走不铺摊子。

## 二、与 coli-architecture 的对标结论

| 处置 | 内容 | 说明 |
|---|---|---|
| **直接吸收** | DDD 七包分层：`api / application / domain / crossdomain / infrastructure / types / common` | 与 golang/rust 栈的 DDD 声明同构；archetype 直接生成此结构 |
| | Maven 多模块工程、Lombok + MapStruct（assembler 层） | coli 同构；Lombok provided 不传染业务方 |
| | MyBatis-Plus 作 ORM 基线 | PG 规约五-3 点名"Java 用显式 resultMap/@Column"，MP 兼容该口径 |
| | 服务名 kebab-case | yarch registry.md 一-1 已同构 |
| **明确不采用** | `cp -r` 复制式分发 | yarch 走发版式升级（构件 + version 变更自动跟进）+ archetype 生成 |
| | 飞鱼式全家桶基座 / 平台模板管控中心 | 违背规约组合模型（按需组合、无全家桶假设） |
| | 第一批引入 Spring Cloud / Nacos / Sentinel / Seata / SCG | 单体 DDD 起步；微服务化当天触发（对齐"规约按业务触发不预写"） |
| | Sa-Token / Keycloak / Knife4j 设为默认 | 鉴权实现触发时按 2xxx 契约再议；API 文档默认 springdoc，Knife4j 仅作可选皮肤 |
| **yarch 增量** | 统一信封 / 错误码段位表 / ndjson + traceId 进程内自带 | coli 可见文档无统一响应与错误码表，链路依赖 SkyWalking；yarch 契约四件套可执行化 |
| | ArchUnit 分层机检 + 契约断言测试 | "规范的可执行化"定位在 Java 栈的第一批落点 |

## 三、目标形态（模块树）

目录分组与 Spring Boot 4 同构（core/module/starter/platform），按 yarch 规模裁剪——Boot 需要的 module/starter 两层分离与 `-test` 成对变体不采用（理由见 J4 对标结论）：

```
stacks/java/                                # Maven reactor 根（聚合 POM，非发布件）
├── platform/                               # 工程底座：版本与插件仲裁
│   ├── yarch-parent/                       #   插件链：compiler/enforcer/jacoco/发布配置（central-portal + GPG profile）
│   └── yarch-bom/                          #   三方版本唯一仲裁（import spring-boot-dependencies + 增补项）
├── core/                                   # 无技术内核（对齐 Boot core/ 原则）
│   └── yarch-common/                       #   RestResponse/PageData/GlobalErrorCode/BusinessException/ErrorCode 接口（最小依赖，不绑 Spring Web）
├── starter/                                # 规约落地件（yarch 合并 Boot 的 module+starter 为一层）
│   ├── yarch-web-spring-boot-starter/      #   信封封装、全局异常→码表映射、参数校验→1001、分页绑定、Jackson ISO-8601 UTC、springdoc
│   ├── yarch-logging-spring-boot-starter/  #   ndjson（logstash-logback-encoder）、MDC、TraceIdFilter（traceparent 优先，filter 序列最先）
│   ├── yarch-persistence-spring-boot-starter/ # MyBatis-Plus 定制：is_deleted↔deleted 映射链、审计自动填充、分页下推、keyset 游标、Flyway（PG 档起步）
│   ├── yarch-redis-spring-boot-starter/    #   key 前缀强制（首段=服务名）、JSON 序列化（禁 JDK 序列化）、分布式锁、幂等存储
│   └── yarch-test-spring-boot-starter/     #   业务侧测试基座：AIR/BCDE 基线、Testcontainers（PG/Redis）、契约断言工具、ArchUnit 规则集
└── archetype/                              # 工程生成器（双模板，J1-D）
    ├── yarch-archetype-ddd/                #   DDD 七包（默认档：业务规则密集型服务）
    └── yarch-archetype-simple/             #   阿里五层裁剪（CRUD/管理面/短生命周期服务）
```

依赖方向：`core` ← 各 starter；BOM 管全部三方版本；parent 管插件链。业务工程只 import BOM + 按需引 starter，不写三方版本号（版本仲裁唯一出口）。分组目录只影响仓库导航与增长归处（第二批 kafka/rocketmq/xxl-job/nacos/minio 直接进 `starter/`），不影响 Maven 坐标与发布形态。

## 四、契约 → 构件映射（脚手架的灵魂）

| 契约条文（出处） | 落点 | 机检 / 验收 |
|---|---|---|
| RestResponse 四字段（[rest-response.md](../../contract/api/rest-response.md)） | common + web | 契约断言测试：字段名/必填/序列化形状 |
| 错误码 13 码 + 默认文案 + HTTP 映射（[error-codes.md](../../contract/api/error-codes.md)） | common `GlobalErrorCode` | 全量断言（code/标识/message/HTTP 映射），防实现漂移 |
| 分层异常纪律（A4 吸收：infra 吞栈转译、Web 层统一兜底） | web `GlobalExceptionHandler` | MockMvc 异常矩阵测试 |
| 参数校验 → 1001/400；D6 越界空页（[rest-conventions.md](../../contract/api/rest-conventions.md)） | web + jakarta-validation | 校验与分页集成测试（真实 PG） |
| PageData 形状 + `nextCursor` 游标通道 | web + persistence | 分页集成测试 |
| ISO-8601 UTC / 金额字符串 / ID 不透明 string / 枚举小写 / 空集合 `[]` | web Jackson 定制 | 序列化断言 |
| ndjson 行协议 + MDC + traceId 入口/回显/贯穿（[logging-trace.md](../../contract/api/logging-trace.md)） | logging starter | 捕获 stdout 逐行解析断言；traceId filter 序列最先 |
| PG ORM G1–G10（[postgresql.md](../../contract/infra/postgresql.md) 五） | persistence starter | Testcontainers PG：显式列清单、三层映射链、`updated_at` 自动填充、分页下推、变更集更新（禁全字段 updateById 默认路径） |
| 迁移版本化进仓（postgresql.md 六-1，Flyway 点名） | archetype 内置 `db/migration` | 生成工程含迁移目录与示例迁移 |
| Redis key 前缀 / JSON value / 锁 / 客户端超时基线（[redis.md](../../contract/infra/redis.md)） | redis starter | Testcontainers：断言 key 首段=服务名、value 非 JDK 序列化 |
| 幂等 `Idempotency-Key`（rest-conventions.md 幂等总则-1，Redis `SET NX PX`） | web + redis | 同键回放 / 异参 1007 集成测试 |
| JVM 基线（A7：Xms=Xmx / HeapDumpOnOutOfMemoryError / 远程调用必超时） | archetype Dockerfile + `JAVA_OPTS` | 模板评审项 |

## 五、决策清单

### J1【必须拍板】archetype 分层路线

背景：digest A4 遗留"阿里分层 vs DDD 分层需拍板"；本次用户点名参考 coli-architecture（DDD 七包）。经核实：七包源自 [coze-studio](https://github.com/coze-dev/coze-studio)（Go 项目，`api/application/domain/crossdomain/infra/types`），coli 将其方言化为 Java——七包是语言无关布局，Java 用、将来 golang 栈可直接同构对照。

| 选项 | 内容 |
|---|---|
| A | DDD 七包照搬 coli（api/application/domain/crossdomain/infrastructure/types/common） |
| B | 阿里 Web/Service/Manager/DAO 五层（digest A4 蓝本） |
| C | 结构取 A + 阿里分层异常纪律融进各层职责 |
| D | 双模板：`yarch-archetype-ddd` + `yarch-archetype-simple`（五层档），配选择决策表 |

分层不进契约层：契约四件套在两种分层下落点相同（§四映射表不变），双模板不伤"规范唯一"；增量成本仅一个 archetype artifact + 双倍模板维护面（示例/ArchUnit 规则/README/AI 上下文）。

**拍板记录（2026-09-01）：J1 选 D，双模板进第一批**（用户定向）。ddd 档结构 = 选项 C（A 的七包 + B 的异常纪律）；simple 档 = 正宗阿里五层裁剪，不是缩水 ddd：

- `yarch-archetype-ddd`（**默认档**）：DDD 七包，适用业务规则密集型服务（ysaas 这类）；
- `yarch-archetype-simple`：controller / service / manager（按需，模板含包位与使用说明）/ dao(mapper) / model（DO/DTO/VO/Query 五族），适用 CRUD/管理面/短生命周期服务。

两模板**共享**：全部平台构件（BOM + 五 starter）、基础设施模板（Flyway `db/migration`、docker-compose、Dockerfile、registry 一-1 服务名校验与登记提示、README）、验收口径（§六）。**差异仅三处**：分层骨架、ArchUnit 规则集（ddd 约依赖倒置与 domain 零框架依赖；simple 约经典单向——controller 不直接依赖 dao、业务逻辑不进 controller）、示例代码。维护成本 = 示例与规则集双份，契约与构件零分叉。

模板选择决策表（随两模板 README 落盘）：业务规则主要住在 DB/SQL、字段直出为主 → simple；有状态机/不变式/计费配额类可演化规则 → ddd；拿不准 → ddd（七包向下兼容简单场景，反之不适）。

子决策 **J1a 领域模型风格**：推荐"充血但克制"——业务行为尽量进 `domain/model`，api 层零业务逻辑；ArchUnit 只约束**依赖方向**（见 J7），不约束模型贫血/充血，不做风格警察。

### J2【必须拍板】ORM 选型

| 选项 | 内容 |
|---|---|
| A | MyBatis-Plus 3.5.13+（`mybatis-plus-spring-boot4-starter`） |
| B | Spring Data JPA |
| C | JOOQ |

**推荐 A**：与 coli 一致、SQL 显式可控（G1 显式列清单友好）、复杂查询不憋屈。G8（禁全字段 `updateById` 默认路径）由 starter 基类与 ArchUnit/规约约束。第一批只做 **PG 档**（新项目默认库）；MySQL 档（`create_time`/`datetime` 口径差异）第二批随存量需求。

### J3【必须拍板】运行形态与版本基线

- **J3a 形态**：单体 DDD 模块化（推荐）vs 微服务全家桶（coli 形态）。理由：ysaas 未微服务化，Nacos/MQ 规约明确"微服务化当天"触发；Spring Cloud 依赖不进第一批。
- **J3b JDK（2026-09-01 细化拍板）**：**工具链 JDK 25（Temurin 25.0.4）+ 字节码基线 `--release 21`**，CI 矩阵双运行时（21/25）验证。理由：yarch 是发 Maven Central 的开源平台，字节码 21 采用面最大（2026 企业主流在 21，25 爬坡中）——Spring 自身也是"编 17、支持 17–25"同一逻辑；运行时 25 白拿紧凑对象头（JEP 519，小对象密集堆省 ~10-20%）、AOT 启动改进与更长支持窗口（Temurin 25 到 2030+，21 到 2028+）；脚手架用不到 25-only 语法（虚拟线程 21 即有，Scoped Values 定稿在 25 但 MDC 生态未跟进），无牺牲。若只单选：新平台选 25，代价是采用者必须 ≥25，0.x 阶段可接受但无必要。
- **J3c Spring Boot**：**4.1.x（推荐）** vs 3.5.x。依据（2026-09 核实）：4.1 为当前稳定线（2026-06 发布，OSS 支持至 2027-07），4.0 OSS 支持今冬截止、3.5 为企业维护线；MyBatis-Plus 3.5.13+ 已提供 Boot4 专用 starter。动工首日先跑依赖矩阵 spike（springdoc/MapStruct/Lombok × Boot 4.1 × JDK 21），有适配坑则回退 3.5.x 并靠 BOM 隔离。
  - 版本事实来源：[Spring Boot 4.1 发布公告](https://spring.io/blog/2026/06/10/spring-boot-4)、[endoflife.date/spring-boot](https://endoflife.date/spring-boot)、[MyBatis-Plus 安装文档](https://baomidou.com/getting-started/install/)、[GitHub Issue #6966](https://github.com/baomidou/mybatis-plus/issues/6966)

### J4【必须拍板】构件切分与 starter 粒度

| 选项 | 内容 |
|---|---|
| A | §三 的细粒度：一规约一 starter（web/logging/persistence/redis/test） |
| B | 粗粒度：单 `yarch-spring-boot-starter` 全含 |
| C | 折中：core 一个 + infra 各一 |

**推荐 A**：契约层的"规约组合模型（按需组合、无全家桶）"在构件层的同构表达——不启用的规约，其 starter 不进 classpath。

**对标 Spring Boot 4.x 实测拆分**（2026-09 核实 [main 分支](https://github.com/spring-projects/spring-boot)）：仓库按 `core/`（8 个无技术内核模块）+ `module/`（约 120 个技术模块，一个可替换技术一个：jackson/gson、flyway/liquibase、jdbc/jpa/jooq、webmvc/webflux、kafka/rabbitmq…）+ `starter/`（约 160 个纯依赖聚合壳，零代码，每模块配 `-test` 变体）+ `platform/spring-boot-dependencies`（BOM 唯一仲裁）组织。三条结论：

1. 九模块不是"太简单"，而是**同构于 Boot 4 的终点形态**——Boot 3.x 单体 jar 全量类进 classpath 的教训就是粗粒度的账单，4.0 付了 classic 兼容层才拆开；yarch 起步即细粒度，无包袱。数量差异源于粒度轴不同：Boot 按技术选择点拆（服务异构大众），yarch 按规约启用点拆（opinionated，已替用户选型）。
2. 吸收两条 Boot 结构原则：① core 无技术（`yarch-common` 零 Spring Web 依赖，同构）；② 测试跟技术走——每个 starter 模块内自带 Testcontainers 集成测试，业务侧另由 test starter 提供统一基座。**不吸收** module/starter 两层分离：Boot 分两层因一个 module 对应多变体 starter（webmvc/webflux/jersey），yarch 每个 starter 即唯一形态，分两层是空转。
3. **修正一处**：MySQL 档将来 = 新增 `yarch-persistence-mysql-spring-boot-starter`（"一技术一模"原则），不是同模块配置档；第二批的 kafka / rocketmq / xxl-job / nacos / minio 各自独立 starter 同理。模块数增长只走"新规约触发"一条路。

### J5【建议拍板】鉴权与幂等的批次

- **鉴权**：2xxx 契约已定（Bearer JWT、RS/ES 优先），但 yarch 只能做"凭证校验 + 2xxx 映射"机制件，账号/权限模型属业务工程。推荐**第一批不做**，IAM 需求落地时再议 `yarch-auth-spring-boot-starter`（Spring Security filter 自研 vs Sa-Token 届时拍板）。
- **幂等**：契约核心条文，且是 web+redis 协作的样板价值。推荐**第一批做**（注解 + filter + Redis `SET NX PX` 存储）；想再瘦身可降为第二批。

### J6【建议拍板】工具链

- Lombok（provided scope，不传染）+ MapStruct（assembler 层）：**推荐都用**，coli 同构；注解处理器顺序在 parent 固化。
- API 文档：springdoc-openapi（Boot4 兼容版）随 web starter 默认；Knife4j 作 optional 皮肤，不默认。

### J7【建议拍板】机检第一批落地范围

推荐三件，正合 contract/README「机检路线」：

1. **契约断言测试**（错误码 13 码全表 + 信封形状 + ndjson 行协议）进 CI 必跑——"各栈必须等价实现"的 Java 侧守门；
2. **ArchUnit 规则集**（随 test starter 提供、archetype 生成工程预置）：DDD 依赖方向（api→application→domain；infrastructure 实现 domain 接口；domain 不依赖 org.springframework/org.apache.ibatis）、命名后缀（Controller/ApplicationService/Repository 实现）；
3. **GitHub Actions**：构建 + 测试 + JaCoCo 覆盖率。`yarch lint` CLI 仍按契约遗留项排期，不在第一批。

### J8【建议拍板】archetype 与分发

- 分发：**maven-archetype 打包**（Maven 原生、IDE 友好）；将来 `yarch init` CLI（tools/locate-scaffolds 演进）包一层。不采用 coli 的 `cp -r`。
- 生成参数：groupId / artifactId（=服务名）/ 包名。**archetype 内置 registry.md 一-1 校验**（`^[a-z][a-z0-9-]{1,31}$` + 禁裸通用词表），生成尾注提示去 registry.md 登记。
- 模板内容：DDD 七包骨架 + Flyway `db/migration` + Dockerfile（多阶段，`JAVA_OPTS` 含 A7 基线）+ docker-compose（本地 PG/Redis）+ README（启动/登记指引）+ **示例 CRUD**（`users` 资源：POST 创建 / GET 分页列表 / GET 单查，含逻辑删除与审计列，信封与 traceId 眼见为实）。

## 六、分批施工清单

### starter 全景目录（设计完整，实现按触发）

20 份 infra 规约按 Java 落地形态分三类。**命名与归宿在此预注册**，触发条件到达即新增 starter（J4：一技术一模，不做配置档）：

| infra 规约 | Java 落地形态 | 触发条件 |
|---|---|---|
| postgresql | `yarch-persistence-spring-boot-starter` | **第一批**（ysaas） |
| redis | `yarch-redis-spring-boot-starter` | **第一批**（ysaas） |
| mysql | `yarch-persistence-mysql-spring-boot-starter` | 存量库接入 |
| timescale | persistence 内（同 PG 连接；压缩/保留策略属迁移层） | 随用，无独立 starter |
| pgvector | persistence 薄扩展（同库连接 + HNSW 迁移辅助 + 检索模板） | AI 场景（向量起步档） |
| redisearch | redis starter 内可选模块（同实例） | 随用 |
| kafka | `yarch-kafka-spring-boot-starter`（traceId 注入/继承、幂等消费、DLQ） | ysaas 事件流/日志管道 |
| rocketmq | `yarch-rocketmq-spring-boot-starter`（同上 + 事务/延时消息） | ysaas 在线业务消息 |
| xxl-job | `yarch-xxljob-spring-boot-starter`（每轮新 traceId、幂等三级手段辅助） | 时间驱动调度需求 |
| nacos | `yarch-nacos-spring-boot-starter`（配置+注册，Spring Cloud Alibaba 集成） | **ysaas 微服务化当天** |
| minio-s3 | `yarch-minio-spring-boot-starter`（预签名 ≤15min、bucket 纪律、元数据在 DB 辅助） | 对象存储需求 |
| mongodb | `yarch-mongodb-spring-boot-starter` | 文档型刚需经评审 |
| elasticsearch | `yarch-elasticsearch-spring-boot-starter`（别名滚动纪律辅助） | 全文检索需求 |
| qdrant | `yarch-qdrant-spring-boot-starter` | 专用向量档升级 |
| milvus | `yarch-milvus-spring-boot-starter` | 亿级/GPU（大概率长期不触发） |
| clickhouse | `yarch-clickhouse-spring-boot-starter`（攒批写入） | 日志/遥测分析 |
| starrocks | `yarch-starrocks-spring-boot-starter` | 实时数仓/BI |
| celery | 无 Java 形态（Python 任务队列） | 不适用 |
| nginx / higress | 无 Java starter（网关侧规约，配置进 git/IaC，归 deploy 域） | 不适用 |

> 鉴权（`yarch-auth-spring-boot-starter`，JWT + 2xxx 映射）同表管理：IAM 需求触发。幂等已由 web + redis 协作覆盖（第一批，J5）。

### 构件路线图 2.0（2026-09-02 复审：补空白 + 对标吸收）

**对标结论**：构件化对标主线为 **阿里 COLA components**（定位最像：架构组件化、无业务语义——吸收其轻量状态机与异常断言设计，不吸收其扩展点组件）、**yudao 的 starter 分层方法论**（每横切能力一 starter，与 yarch 同构，但业务语义件如 data-permission/tenant 归业务仓）、**Spring 官方生态**（结构与正统，目录分组已同构）、**JHipster**（生成器体验，yarch init 演进参照）；Spring Modulith 的模块边界验证理念已由 ArchUnit 覆盖，不引依赖。

| 新增构件 | 内容 | 触发 | 对标出处 |
|---|---|---|---|
| `yarch-http-spring-boot-starter` | **下游调用契约件：唯一"契约已定义无实现"的空白**——超时强制（A7）、traceparent/X-Trace-Id 注入（传播矩阵出口行）、RestResponse 解包、下游错误→1008/1009 转译 | **第一批（补契约空白）** | 传播矩阵（logging-trace.md）；yudao http 封装 |
| web 增强：`@RateLimited` | 限流（1006 实现缺位） | 第一批（G1） | ruoyi @RateLimiter |
| web 增强：`@OperationLog` | 操作日志切面（ndjson + 存储 SPI） | 第一批（G2） | ruoyi @Log / yudao operatelog |
| web 增强：`@SignedApi` + 脱敏 | HMAC 签名防重放 + Jackson 序列化脱敏注解 | 第一批（E4） | yudao @ApiAccessLog 同位；ruoyi @Sensitive |
| bom+web：Excel 协议 | FastExcel 纳管 + 导入导出协议（错误行对齐 1001 信封） | 第一批（E1/G3） | EasyExcel/FastExcel |
| `yarch-captcha-spring-boot-starter` | 图形/算术验证码 + Redis token + 限流联动；OTP 存储校验框架 | 第一批（E2） | ruoyi captcha / AJ-Captcha |
| `yarch-auth-spring-boot-starter` | JWT 签发/解析 + 2xxx 映射 + @RequireRoles（机制件；账号模型归业务仓） | 第一批（E3，G4 提前） | Sa-Token（形态参照）|
| `yarch-observability-spring-boot-starter` | Micrometer/OTel 指标与跨度导出（进程内 traceId 已有，导出归运维） | 触发式（接入监控栈时） | Micrometer Tracing / OTLP |
| `yarch-statemachine`（common 级轻量库） | 领域状态机（状态字段→状态机→流程引擎三档的中间档，支撑简单审批） | 第三批（E5 教学案例先行） | **COLA cola-component-statemachine** |
| common 增强：`Asserts` | 异常断言（`Asserts.notNull(x, ErrorCode, detail)` 抛 BusinessException） | 随第一批顺手 | COLA ExceptionAssert |
| 登记不做 | COLA 扩展点组件（extension）、Spring Modulith 依赖、CQRS/Axon | — | 理由：单一结构真理源 / ArchUnit 已覆盖 / 过重 |

**第一批（本策划拍板后）**：reactor / parent / bom / common / web / logging / persistence(PG) / redis / test 基座 / **双 archetype（ddd + simple）** / CI / README + 架构图（五层架构图，coli-architecture 风格，见 architecture.md 三·五）。✅ 2026-09-01 完成（13/13 绿）。

**第二批 = 精细打磨批（路线图 2.0 第一批）✅ 2026-09-02 完成（reactor 16/16 绿）**：yarch-http（出口传播/超时强制/信封解包/1008-1009 转译）、web 增强（@RateLimited 1006、@OperationLog 存储 SPI、@SignedApi HMAC 签名，web 达 18 测试）、yarch-captcha（图形码+Redis 一次性 token）、yarch-auth（JWT+2xxx 矩阵+@RequireRoles）、common 增 Asserts/Masks（@Mask 注解因 Jackson 3 注解桥接受限降级为工具，G 表已注）、bom 纳管 fastexcel/nimbus。状态机教学案例（E5）顺延。

**第三批（按触发，不预铺）**：`yarch-persistence-mysql` 档 / minio / kafka·rocketmq / xxl-job / nacos + Spring Cloud（ysaas 微服务化当天）/ observability / statemachine 正式件 / `yarch lint` 条文机检扩展——新增组件一律新 starter（J4 对标结论），不做配置档。

## 七、开放问题与风险

1. **Boot 4.1 生态边角**：springdoc 等对 Boot 4.x 适配版本需 spike 验证（J3c 已设回退路径）。
2. **MapStruct × Lombok 注解处理器协作**：已知成熟组合，parent 模板固化处理器顺序即可。
3. **Testcontainers 依赖 Docker**：本地与 CI 均需容器运行时；README 写明前置条件。
4. **Maven Central 发布**：central-portal + GPG 签名在 parent 预置 profile；第一批 `mvn install` 本地仓即可供 ysaas 引用，正式发布随 0.1.0 稳定后。
