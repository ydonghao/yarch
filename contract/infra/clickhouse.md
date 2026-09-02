# ClickHouse 开发规约（v1.0 已定稿）

> **状态：已定稿**（2026-09-01 评审通过）。供人阅读、供 AI 作为规范上下文使用。
> 等级：**【强制】**违反即缺陷；**【推荐】**默认遵守、评审后可豁免；**【参考】**正向引导。适用 ClickHouse 23.x+。分工与来源见文末附录。

## 一、定位与分工

1. 【强制】ClickHouse 只做 OLAP：日志/事件/遥测的批量分析与报表；禁 OLTP 场景（高频点查、单行更新、小事务）——此类需求回 PG/MySQL。
2. 【强制】OLAP 双引擎分工（已落决策，可推翻）：**ClickHouse 管日志与遥测事件流的存储分析；StarRocks 管业务实时数仓与交互式 BI**（见 [starrocks.md](starrocks.md)）。同一数据域不双写两引擎，需评审。
3. 【强制】库与表前缀 = 服务/数据域名（`{服务名}_{业务域}`），共享集群按前缀隔离并授权——同 Kafka topic 前缀哲学。

## 二、表设计

1. 【强制】表引擎 MergeTree 家族按语义选：明细 `MergeTree`、去重 `ReplacingMergeTree`（最终一致语义，查询带 `argMax`/`FINAL` 慎用）、聚合 `AggregatingMergeTree`（配合物化视图）；禁随意 `Distributed` 裸建。
2. 【强制】`ORDER BY` = 核心查询的过滤与聚合维度（等值/范围列前置），建表期定妥——ORDER BY 是唯一索引，事后不可低成本改。
3. 【强制】分区键按时间粒度显式（`toYYYYMM` / `toYYYYMMDD`），单分区目标 GB~几十 GB 级；禁无分区大表与按高基数列分区。
4. 【强制】低基数维度用 `LowCardinality(String)`；枚举用 `Enum8/16` 或字典表；禁把高基数字符串当维度存大宽表。
5. 【推荐】物化视图预聚合热点报表；`sample` 子句支持的大表按需采样。

## 三、写入

1. 【强制】批量写入：单批 ≥1MB 或 ≥1000 行，频率不高于每秒一批（异步 insert 或客户端攒批均可）；禁逐条 insert（parts 爆炸是首要故障源）。
2. 【强制】写入通道经 Kafka 消费攒批（吞吐削峰），消费组与 topic 命名对齐 [kafka.md](kafka.md)；禁多写端无序竞争同一表。
3. 【强制】`ReplacingMergeTree` 的去重键（版本列）显式设计；去重语义按最终一致消费，禁当强一致读。
4. 【推荐】大促/高峰预评估写入配额；`insert_quorum` 按一致性需求显式选。

## 四、查询

1. 【强制】查询显式列清单（禁 `SELECT *`），大宽表按投影/物化视图收敛。
2. 【强制】聚合查询必须有时间分区裁剪条件（`WHERE ts >= ...`）；禁全分区扫描裸聚合。
3. 【强制】`GROUP BY` 高基数（千万级去重值）与 `ORDER BY` 无上限结果集必须评审：加 `LIMIT`、分层聚合或预估内存。
4. 【强制】JOIN 纪律：右表为小表（可内存），优先字典/物化视图替代；大表 JOIN 大表改预关联或分层。
5. 【推荐】交互查询超时与最大内存（`max_execution_time`、`max_memory_usage`）显式限制；报表外查询走只读账号。

## 五、数据生命周期

1. 【强制】TTL 显式（`TTL ts TO DELETE`）按数据域登记保留期；与 Kafka retention、ES ILM 同数据域统一规划。
2. 【推荐】冷热分层（TO DISK/VOLUME）与物化视图聚合后删明细（TTL 联动）按成本评审。

## 六、运维基线

1. 【强制】生产副本表（ReplicatedMergeTree + Keeper），分片按容量与吞吐规划；单机仅限开发。
2. 【强制】监控：parts 数量与合并积压（mutations）、写入拒绝、查询 P99 与超时率、磁盘水位（70% 治理）、副本延迟。
3. 【强制】`ALTER ... DELETE/UPDATE`（mutation）视为高危：批量小、低峰执行、变更评审——mutation 不是常规更新手段。
4. 【强制】备份用 clickhouse-backup / 快照方案，恢复演练季度化；`ALTER` 大操作先在影子表验证。

---

## 附：分工与来源

- **OLAP 分工（已落决策，可推翻）**：ClickHouse=日志/遥测/事件流分析（Kafka 直连攒批链路成熟）；StarRocks=业务实时数仓与交互式 BI（主键模型点查与 JOIN 更强）。重叠场景（通用 OLAP 报表）默认 StarRocks。
- 写入批量口径（≥1MB/1000 行、每秒一批）、parts 爆炸、mutation 高危、ORDER BY 即索引：ClickHouse 官方文档通行口径。
