# TimescaleDB 开发规约（v1.0 已定稿）

> **状态：已定稿**（2026-09-01 评审通过）。供人阅读、供 AI 作为规范上下文使用。
> 等级：**【强制】**违反即缺陷；**【推荐】**默认遵守、评审后可豁免；**【参考】**正向引导。适用 TimescaleDB 2.x（PostgreSQL 扩展）。**前置：本规约叠加于 [postgresql.md](postgresql.md) 之上，命名/建表/索引/SQL/迁移/运维条文全部继承**；本篇只写时序增量。

## 一、定位与选型

1. 【强制】TimescaleDB 用于 PG 体系内的时序负载（设备/监控/事件时序、时间区间聚合）；日志与遥测的大规模分析归 ClickHouse（[clickhouse.md](clickhouse.md) 一-2 分工），禁用 TimescaleDB 承接日志全量检索。
2. 【强制】时序表与业务表同库共存时，时序负载独立的库/schema 隔离（`timeseries` schema），防 autovacuum 与查询资源互相干扰。
3. 【推荐】纯时序新项目（无关系join需求）先评估 CH/StarRocks；需要 SQL/JOIN/事务与主业务同库时选 TimescaleDB。

## 二、超表设计

1. 【强制】超表必须显式 `create_hypertable(..., chunk_time_interval => ...)`：分区键即时间列；chunk 间隔按写入量定目标——单 chunk（未压缩）大小控制在可用内存 25% 量级，常用小时~天级。
2. 【强制】时间列 `timestamptz not null`（对齐 PG 规约），写入统一 UTC；禁用无时区类型存时序。
3. 【强制】空间分片（`partitioning_column` + `number_partitions`）仅单机高并发或设备数巨大时启用，分片键为设备/主体 ID，数量登记。
4. 【强制】常规查询必须带时间范围条件（分区裁剪）；禁跨全历史无过滤扫描。
5. 【推荐】唯一索引与主键必须包含时间列（超表约束）；业务唯一性用（主体 ID, 时间, 序号）复合表达。

## 三、写入

1. 【强制】批量写入（multi-row insert / COPY），单批 ≥1000 行或 ≥1MB（对齐 CH 攒批口径）；禁逐条高频 insert。
2. 【强制】乱序写入容忍但受控：大批量历史回填走低峰；实时链路时间接近 now 为主。
3. 【推荐】写入侧背压：写入积压走 Kafka 削峰（topic 命名对齐 [kafka.md](kafka.md)）。

## 四、压缩与保留

1. 【强制】超表启用原生压缩策略：`compress_segmentby`（主体 ID）+ `compress_orderby`（时间），`compress_after` 按查询新鲜度需求定（常见 7 天内不压缩）；压缩收益与查询模式在评审记录。
2. 【强制】保留策略 `drop_chunks` 显式（按数据域登记保留期），配合归档层（对象存储导出）按需。
3. 【强制】禁对已压缩 chunk 的高频更新/删除：修正类操作走回填新数据（upsert 语义）或解压窗口设计。
4. 【推荐】热点聚合用连续聚合（continuous aggregate）：实时物化 + 自动刷新，查询走物化视图而非明细全聚合。

## 五、查询

1. 【强制】时间桶聚合用 `time_bucket`（对齐 `date_trunc` 语义），步长与前端图表粒度一致；禁 `generate_series` + 自联结暴力对齐。
2. 【推荐】last 点查询用 `last(column, time)`；最新值表（每个主体一行）与明细超表分离（物化维护）。
3. 【推荐】近似函数（`approx_percentile` 等）用于监控大盘，精确分位数走离线。

## 六、运维基线

1. 【强制】监控增量：chunk 数量与大小分布、压缩任务（job 成功/失败）、drop_chunks 执行、超表膨胀（dead tuples）。
2. 【强制】备份继承 PG 基线（WAL + PITR）；恢复演练验证超表与压缩数据可用。
3. 【推荐】多节点（Multi-node）仅超大规模评审使用，默认单机超表 + 只读副本扩展读。

---

## 附：来源与决策

- chunk 大小 ~25% 内存、compress_segmentby/orderby、drop_chunks、连续聚合：Timescale 官方文档最佳实践。
- **已落决策**：时序负载在 PG 生态内用 TimescaleDB，日志/遥测大规模分析归 ClickHouse；时序表独立 schema 隔离；批量写入口径与 CH 统一。
