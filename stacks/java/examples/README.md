# examples/ · 活案例（双档同域）

同一业务域（商品 + 订单）的两档实现，既是"脚手架用起来长什么样"的活样例，
也是平台特性（锁 / 幂等 / keyset / cache-aside）的端到端回归场——随 reactor 一起 `mvn verify`。

| 模块 | 档位 | 展示重点 |
|---|---|---|
| `yarch-examples-ddd/` | DDD 七包 | 依赖倒置（缓存端口在 domain、实现在 infra）、领域服务、MapStruct |
| `yarch-examples-simple/` | 阿里五层 | manager 层通用下沉（StockManager：分布式锁 + 原子扣减）、DO 即模型 |

**覆盖的平台特性**（两档验收语义一致）：

1. 幂等下单（Idempotency-Key 回放，库存只扣一次）；
2. 分布式锁互斥 + 存储层条件扣减兜底（幂等总则-2）；
3. cache-aside（miss 回源回填带 TTL 抖动、写路径删缓存）；
4. keyset 游标分页（严格递增、末窗 nextCursor 省略）；
5. 业务码 3xxx（3001/3002/3003）+ 错误信封 + ArchUnit 分层机检。

本地跑：`docker compose up -d`（仓 stacks/java 根目录）+ `mvn -f examples/yarch-examples-ddd/pom.xml spring-boot:run`（8081）/ simple（8082）。

> 案例 port 8081/8082；数据库/缓存连接按各 application.yaml，测试用 Testcontainers 自动起容器。
