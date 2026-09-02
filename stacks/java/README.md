# stacks/java · yarch-java

> yarch Java 栈平台构件（0.1.0-SNAPSHOT，开发期本地仓库；稳定后经 central-portal 发 Maven Central）。
> 契约唯一权威来源：[../contract/](../contract/README.md)——实现与契约不一致即 bug。
> 策划与拍板记录：[PLAN.md](PLAN.md)（J1-J8 全部已拍板）。

## 模块（目录分组与 Spring Boot 4 同构）

```
stacks/java/
├── platform/          # 工程底座
│   ├── yarch-parent/  #   插件链 + 版本仲裁入口（业务工程直接继承，零版本号）
│   └── yarch-bom/     #   三方版本唯一仲裁（Boot 4.1.x + MyBatis-Plus 3.5.17 + springdoc 3.1 …）
├── core/yarch-common/ # 无技术内核：RestResponse / GlobalErrorCode / PageData / BusinessException / TraceIds
├── starter/           # 一规约一 starter（J4 细粒度，按需组合）
│   ├── yarch-web-spring-boot-starter/         # 信封 + 全局异常→码表 + 参数校验→1001 + 分页 + Idempotency-Key + springdoc
│   ├── yarch-logging-spring-boot-starter/     # ndjson 行协议 + MDC + TraceIdFilter（traceparent 优先）+ 请求完成日志
│   ├── yarch-persistence-spring-boot-starter/ # PG + MyBatis-Plus + Flyway：逻辑删除/审计填充/分页下推/keyset
│   ├── yarch-redis-spring-boot-starter/       # RedisKeys（首段=服务名）+ JSON 序列化 + 分布式锁 + 跨实例幂等存储
│   ├── yarch-http-spring-boot-starter/        # 下游调用契约件：超时强制 + traceparent 注入 + 信封解包 + 1008/1009
│   ├── yarch-captcha-spring-boot-starter/     # 图形验证码 + Redis 一次性 token（GET /api/v1/captcha）
│   ├── yarch-auth-spring-boot-starter/        # JWT 机制件（HS256 默认/RS-ES 可扩）+ 2xxx 映射 + @RequireRoles
│   └── yarch-test-spring-boot-starter/        # 契约断言 + ArchUnit 双规则集 + PG/Redis 容器基座
└── archetype/
    ├── yarch-archetype-ddd/     # DDD 七包（默认档）
    └── yarch-archetype-simple/  # 阿里五层（简单贫血档）
```

另含 `examples/` 活案例组（2026-09-02 增）：[yarch-examples-ddd](examples/yarch-examples-ddd/) 与 [yarch-examples-simple](examples/yarch-examples-simple/)，同域（商品+订单）双档实现，展示锁/幂等/keyset/cache-aside 并作为平台特性端到端回归场，随 reactor 一起 verify。

脚手架对标缺口与对齐决策：[../docs/references/ruoyi-yudao-coli-gap-digest.md](../docs/references/ruoyi-yudao-coli-gap-digest.md)（G1-G10 待拍板）。

架构图：[architecture-diagram.svg](architecture-diagram.svg)（五层：业务工程 / Web 装配 / 平台构件 / 统一契约 / 运行底座）。

## 版本基线（2026-09-01 spike 实测）

| 项 | 值 |
|---|---|
| 工具链 / 字节码 | Temurin JDK 25 / `--release 21`（CI 双运行时 21+25） |
| Spring Boot | 4.1.x（Jackson 3 主线，ISO-8601 默认即契约口径） |
| MyBatis-Plus | 3.5.17（boot4-starter + jsqlparser 插件件） |
| Testcontainers | 1.21.x（容器基座内封装，2.x 的 macOS/OrbStack 探测缺陷已绕开） |

## 快速开始

```bash
# 1. 构建安装平台构件（本仓 stacks/java）
export JAVA_HOME=<jdk25>
mvn -f stacks/java/pom.xml install

# 2. 生成业务工程（二选一，服务名须过 registry.md 一-1 校验）
mvn archetype:generate -B \
  -DarchetypeGroupId=io.github.yuandonghao -DarchetypeVersion=0.1.0-SNAPSHOT \
  -DarchetypeArtifactId=yarch-archetype-ddd \
  -DgroupId=com.example -DartifactId=my-svc -Dpackage=com.example.mysvc

# 3. 生成工程一键跑通
cd my-svc && docker compose up -d && mvn spring-boot:run
```

生成工程 `mvn verify` 自带三层验收：ArchUnit 分层机检、契约断言、Testcontainers 全链路（信封 / traceId 贯穿 / 分页 D6 / 逻辑删除 / 幂等 / 业务码 3xxx）。

## 模板选择决策表（J1-D）

| 信号 | 模板 |
|---|---|
| 业务规则主要在 DB/SQL、字段直出的 CRUD/管理面/短生命周期服务 | `yarch-archetype-simple` |
| 状态机 / 不变式 / 计费配额类可演化规则（ysaas 这类 SaaS 核心） | `yarch-archetype-ddd` |
| 拿不准 | ddd（七包向下兼容简单场景，反之不适） |

两模板共享全部平台构件与验收口径，差异仅分层骨架、ArchUnit 规则集、示例代码——分层不进契约层。

## starter 全景与触发条件

设计完整、实现按触发（终态目录见 [PLAN.md](PLAN.md) §六）：MySQL 档、kafka、rocketmq、xxl-job、nacos、minio、mongodb、es、qdrant、milvus、clickhouse、starrocks、auth(JWT) 均为新 starter 事件，不做配置档。

## 工程一致性三件套（代码风格 / 编辑器 / git 忽略）

- **Spotless**（构建期强制，随 yarch-parent 继承，业务工程零配置获得）：AOSP 风格 = **4 空格缩进**（阿里手册强制条）+ import 排序 + 去未用导入；`spotless:check` 挂在 `verify`（CI 门禁），`mvn spotless:apply` 一键修复。archetype 模板已按 formatter 精确输出校准——生成工程开箱即过格式检查。
- **.editorconfig**（仓根，编辑器层兜底）：Java 4 空格、XML 4、yaml/json/md 2、LF、UTF-8、去尾空白。Spotless 管构建，editorconfig 管 IDE，两者口径一致。
- **.gitignore**（仓根）：构建产物（`target/`、`*.class`、`.flattened-pom.xml`、`*.versionsBackup`）、IDE、OS、环境变量文件；多栈预置（node_modules/dist 等）。

## 版本管理与发版流程

- **平台侧（多模块统一）**：`mvn versions:set -DnewVersion=X -DprocessAllModules=true` 一条命令改全部 11 个 pom（parent/bom/模块/reactor 根），确认 diff 后 `mvn versions:commit` 落盘（`versions:revert` 可回滚，已实测往返）。插件 2.21.0 已入 parent 插件链。
- **消费侧（已是单点）**：业务工程只继承 `yarch-parent` 一行版本（BOM 经 parent import），yarch 升版时业务工程仅改该行。
- **注意**：`archetype-metadata.xml` 的 `yarchVersion` 默认值是模板资源，versions 插件不改它——发版时随 release 手动同步（或生成时显式传 `-DyarchVersion`）。
- 备选（暂不启用）：CI Friendly `${revision}` + flatten-maven-plugin（单点属性、CI 按分支定版），高频发版时再切换；当前发版频率下 versions:set 更简单且零结构成本。

## 本机开发备注（macOS + OrbStack）

Testcontainers 走 `~/.docker/desktop/docker.sock → ~/.orbstack/run/docker.sock` 软链（TC 的 Docker Desktop 探测位）；OrbStack daemon 需在运行。CI（ubuntu-latest）无需处理。
