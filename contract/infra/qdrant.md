# Qdrant 开发规约（v1.0 已定稿）

> **状态：已定稿**（2026-09-01 评审通过）。供人阅读、供 AI 作为规范上下文使用。
> 等级：**【强制】**违反即缺陷；【推荐】默认遵守、评审后可豁免；**【参考】**正向引导。适用 Qdrant 1.x。向量三档选型与来源见文末附录。

## 一、定位与选型

1. 【强制】向量检索三档选型（已落决策，可推翻）：**pgvector 默认起步**（[pgvector.md](pgvector.md)）→ 专用向量库升级档**默认 Qdrant**（本规约）→ **Milvus** 仅超大规模（亿级向量）/GPU 索引/超复杂标量组合（[milvus.md](milvus.md)）。升级评审条件：向量规模千万级以上、检索 QPS 拖垮主库、过滤组合复杂度超出 pgvector 舒适区。
2. 【强制】Qdrant 只存向量与检索必需 payload；业务真相源在 PG/MySQL——标量字段同步经 CDC/管道，同步字段清单与延迟 SLA 登记。
3. 【强制】多模型向量用**命名向量（named vectors）**隔离：同一 collection 内不同 embedding 模型各占一个 named vector，检索必须指定向量名——禁跨模型互检（与 pgvector/milvus 的模型隔离纪律同源）。
4. 【强制】生产开启 API Key 认证（读写键分离按需），禁裸奔端口；内网或 TLS（gRPC TLS / REST 反代见 [nginx.md](nginx.md)）。

## 二、Collection 设计

1. 【强制】collection 命名 `{服务名}_{业务域}`（`yagent_knowledge`），共享实例按前缀隔离授权——同 Kafka topic / MinIO bucket 前缀哲学。
2. 【强制】vector 参数显式：维度固定、`distance = Cosine`（归一化向量口径，与 pgvector `<=>`、Milvus COSINE 对齐）；模型标识写入 payload 或 point 级元数据可追溯。
3. 【强制】payload 只放检索过滤必需的标量（租户、类型、状态、时间），禁把正文/大 JSON 塞 payload——正文在源库或对象存储（[minio-s3.md](minio-s3.md)）。
4. 【强制】过滤字段建 payload 索引（keyword/integer/float/datetime 按类型），建集合时与首版数据一起评估——无索引的过滤是全扫。
5. 【推荐】多租户用 single collection + payload 过滤（tenant_id 索引），优于每租户一 collection（数量爆炸）；点数极大再评估分 collection。

## 三、索引与检索

1. 【强制】HNSW 参数（`m` / `ef_construct`）显式并登记；查询 `search_params.hnsw_ef` 按召回率压测冻结默认值——参数变更走评审（同 pgvector/milvus 纪律）。
2. 【强制】检索固定形态：显式 top-k（`limit`）、带过滤与模型/租户条件；禁无 limit 查询。
3. 【强制】过滤表达式（filter DSL）用户输入必须参数化/转义——注入面与 SQL 同级对待。
4. 【推荐】内存吃紧先启用**标量量化（int8）**与 payload `on_disk`，再评估 binary 量化；量化后召回率回归必跑（评测集固定，同 pgvector 六-3）。
5. 【推荐】混合检索（稀疏向量/全文）与 rerank 按效果评审引入。

## 四、写入与一致性

1. 【强制】批量 upsert：按批（千点级）写入，管道经 Kafka/队列削峰（topic 命名对齐 [kafka.md](kafka.md)）；禁逐点高频写。
2. 【强制】point id 用业务 UUID：内容变更重算按同 id upsert 覆盖，删除显式 delete——禁孤儿向量（同 Milvus 纪律）。
3. 【强制】`wait` 语义显式：默认 `wait=false`（吞吐优先），强一致读路径（写后立查）显式 `wait=true` 并说明。
4. 【推荐】embedding 生成走异步任务（幂等、内容哈希跳过未变更——同 pgvector 五）。

## 五、运维基线

1. 【强制】监控：检索 P99/召回率抽检、写入积压、segment 数与优化器状态、内存与磁盘水位（70% 治理）。
2. 【强制】备份双轨：数据可由源重算时以源为准（源备份 + 管道可重放）；不可重算数据域启用 snapshot 定期备份并演练恢复。
3. 【强制】collection 结构变更（维度/距离/主索引）= 新 collection 迁移（双写→切读→下线），禁在线改。
4. 【推荐】以 docker-compose 纳管共享基础设施，与既有中间件同等对待；版本升级走影子验证。

---

## 附：选型与来源

- **向量三档（已落决策，可推翻）**：pgvector（默认起步）→ **Qdrant（专用库升级档默认**：单二进制部署轻、无 etcd/消息依赖、Rust 生态契合 yarch、payload 过滤强）→ Milvus（亿级/GPU/超大规模保留档）。理由更新原"pgvector→Milvus"两档决策（2026-09-01）。
- HNSW/量化/named vectors/payload 索引/snapshot：Qdrant 官方文档（qdrant.tech/documentation）通行最佳实践。
- 模型隔离、幂等写入、召回回归、可重算以源为准：与 [pgvector.md](pgvector.md)、[milvus.md](milvus.md) 条文同源对齐（协作参考级，见 [../README.md](../README.md) 组合模型）。
