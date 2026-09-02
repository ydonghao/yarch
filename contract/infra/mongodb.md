# MongoDB 开发规约（v1.0 已定稿）

> **状态：已定稿**（2026-09-01 评审通过）。供人阅读、供 AI 作为规范上下文使用。
> 等级：**【强制】**违反即缺陷；**【推荐】**默认遵守、评审后可豁免；**【参考】**正向引导。适用 MongoDB 5.x/6.x/7.x。来源与选型边界见文末附录。

## 一、定位与选型边界

1. 【强制】新项目默认 PostgreSQL（[postgresql.md](postgresql.md) 已落决策）；引入 MongoDB 必须评审并满足其一：文档结构真正无 schema 且演进频繁、深层嵌套聚合查询是核心负载、或既有生态依赖。
2. 【强制】MongoDB 不做跨文档强一致主存储：需要多文档事务与关系完整性的领域归 PG。
3. 【强制】库名 = 服务名（与 PG 一应用一库同构）；跨服务取数走 API，禁跨库直连。

## 二、命名规约

1. 【强制】集合名 snake_case 复数（`users`、`order_items`），禁 `$` 前缀与系统保留（`system.*`、`oplog.rs`）。
2. 【强制】字段名 lowerCamelCase（对齐 JSON 输出，RestResponse camelCase 风格）；禁 `.` 与 `$` 字符入字段名。
3. 【强制】布尔字段语义化命名（`deleted`、`verified`），入库统一 UTC（`createdAt`/`updatedAt`，Date 类型）——字段命名随 Mongo 生态 camelCase，与 PG 的 snake_case 分生态（同 mysql.md 六的口径分野原则）。

## 三、文档设计

1. 【强制】文档上限 16MB 是硬边界，实践控制在 1MB 内：嵌入数组长度可预估且 ≤1000（对齐 redis.md 集合元素口径），超限改引用。
2. 【强制】嵌入 vs 引用判断：一起读写、有限基数 → 嵌入；独立生命周期、无限增长 → 引用（子集合）；禁无脑全嵌入。
3. 【强制】`_id` 策略显式定：默认 ObjectId；有天然幂等键的场景用业务键 `_id`（导入/同步类防重）。
4. 【强制】必备字段：`createdAt`、`updatedAt`（ORM/中间件统一维护，禁客户端时钟）；软删用 `deleted: true` + 部分索引（对齐 PG 逻辑删除哲学）。
5. 【推荐】大文本/二进制走对象存储，文档内只存 key（与 [minio-s3.md](minio-s3.md) 二-4 呼应）。
6. 【参考】schema 校验器（`$jsonSchema`）对核心集合开宽松档（新增字段不阻断、类型不符拒绝）。

## 四、索引规约

1. 【强制】组合索引按 **ESR 顺序**设计：Equality（等值）→ Sort（排序）→ Range（范围）。
2. 【强制】查询字段必须有索引支撑；上线前 `explain("executionStats")` 验证 `COLLSCAN` 不出现在核心查询。
3. 【强制】TTL 需求用 TTL 索引（`expireAfterSeconds`）表达，禁应用定时全表删。
4. 【推荐】覆盖查询（covered query）优先；部分索引（`partialFilterExpression`）服务软删与可选字段。
5. 【推荐】索引数量控制：单集合 <10，冗余前缀索引合并；索引变更走评审登记。

## 五、读写与事务

1. 【强制】读写关注（read/write concern）默认档起步（多数派写 `w:1` → 按业务升 `majority`），选型显式记录；禁盲目全量抬到最强档（性能代价）。
2. 【强制】多文档事务短小（<1s）且仅限必须原子的小范围；跨事务补偿优先——与 G9（事务克制）同源。
3. 【强制】查询显式投影（projection），禁默认全文档返回直接当 API DTO——字段白名单进映射层（G1/G6 同源）。
4. 【强制】`find` 必带 limit；批量写用 `bulkWrite`，禁循环单条。
5. 【推荐】变更流（change stream）用于缓存失效与通知，resume token 持久化。

## 六、运维基线

1. 【强制】副本集（≥3 节点）为生产最低形态；单机仅限开发。
2. 【强制】备份禁依赖 `mongodump` 当唯一手段（大库不可行）：快照级备份（PITR 方案）+ 恢复演练季度化。
3. 【强制】监控：oplog 窗口、慢查询（profiler 阈值 100ms）、连接数、副本延迟、磁盘水位（70% 治理）。
4. 【强制】生产开认证（SCRAM/x.509）与内网访问；账号按库最小权限。

---

## 附：来源与决策

- ESR 索引规则、16MB 上限、TTL 索引、事务边界：MongoDB 官方文档最佳实践。
- `mongodump` 不构成完整备份：官方 Ops 手册口径（推荐快照/PITR）。
- **已落决策**：新项目默认 PG、Mongo 仅文档型刚需经评审引入；集合复数 snake_case、字段 lowerCamelCase（随 Mongo 生态，同"各随生态"口径分野）；软删与必备时间字段对齐 PG 哲学。
