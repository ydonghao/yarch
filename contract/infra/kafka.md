# Kafka 开发规约（v1.0 已定稿）

> **状态：已定稿**（2026-09-01 评审通过）。供人阅读、供 AI 作为规范上下文使用。
> 等级：**【强制】**违反即缺陷；**【推荐】**默认遵守、评审后可豁免；**【参考】**正向引导。适用 Kafka 3.x（KRaft 模式）。消息队列选型记录见文末附录。来源见附录。

## 一、集群与容量

1. 【强制】自托管生产集群至少 3 个 Broker（KRaft 模式，无 ZooKeeper）；单机实例仅限本地开发。
2. 【强制】可靠性基线三件套：`replication.factor = 3`、`min.insync.replicas = 2`、生产者 `acks = all`——三者必须同时成立，禁任何一项降级到生产。
3. 【强制】`auto.create.topics.enable = false`：topic 一律显式创建并登记（见二-6），禁自动创建。
4. 【强制】多项目共享集群时，以 **topic 前缀 = 服务名** 隔离，ACL 按前缀授权（与 Redis key 前缀、Nacos Group 同构）；跨项目订阅必须显式授权，禁通配读写。
5. 【强制】`retention` 显式设置：日志/事件流类按时间（默认 7 天）；事件源/状态回放类用 compacted topic；不得无脑默认值上线。
6. 【推荐】容量规划：按峰值吞吐 ×1.5 与保留时长推算磁盘；单分区目标吞吐与消费并行度匹配（分区是并行度与顺序的双重边界）。
7. 【推荐】跨机房/高可用要求出现前不做多集群；出现后按主备或镜像（MM2）评审。

## 二、Topic 与命名

1. 【强制】topic 命名：`{服务名}.{业务域}.{事件名}`，全小写、点分隔（`order-service.trade.order-created`）——前缀即共享集群下的租户边界。
2. 【强制】事件名用过去式名词短语（`order-created`、`payment-succeeded`），禁动词祈使句与驼峰。
3. 【强制】分区数创建时定妥：按目标吞吐与消费并行度评估；分区只增不减，且增量须评估 key 顺序性影响。
4. 【强制】消息 key = 顺序边界：同一业务实体（订单 ID、用户 ID）的消息必须同 key，保证同分区有序；无顺序诉求显式传 null key（轮询打散）。
5. 【强制】消息体跨栈序列化统一 JSON 或 Protobuf（与 Redis value 规约同构，见 [redis.md](redis.md) 三-1），禁语言原生序列化。
6. 【强制】topic 清单登记：业务仓 docs 登记 topic 名、属主、schema、保留策略——机制与错误码 3xxx+ 段位登记一致。
7. 【推荐】内部通道与对外事件分区：`{服务名}.internal.*` 仅限本服务消费组；对外广播事件单独 topic 并承诺 schema 稳定。

## 三、生产者

1. 【强制】开启幂等生产者（`enable.idempotence = true`，与 `acks = all` 配套）；重试依赖 `delivery.timeout.ms` 兜底，禁自行无限重试循环。
2. 【强制】消息必须携带消息头：`traceId`（贯穿 [../api/logging-trace.md](../api/logging-trace.md) 的同一条链）与 `x-schema-version`。
3. 【强制】发送必须处理结果（回调/同步确认）：失败按业务语义决定重试或落盘补偿，禁"发后不管"。
4. 【强制】单条消息默认上限 1MB（`max.message.bytes`）视为硬边界：超限改引用传递（消息带对象存储 key），禁调大上限塞大对象。
5. 【推荐】批量与压缩：`linger.ms` 5~20ms + `compression.type` lz4/zstd，吞吐与延迟按业务拍板并记录。
6. 【参考】事务生产者（transactional.id）仅在"跨 topic 原子写"或"读-处理-写" Exactly-Once 场景使用，禁当默认。

## 四、消费者

1. 【强制】消费组命名：`{服务名}.{用途}`（`order-service.notification`）；一个消费组一个用途，禁多用途共用组名导致 rebalance 互相牵连。
2. 【强制】幂等消费是默认假设：Kafka 交付语义为至少一次，重复必然发生——按业务键或 `(topic, partition, offset)` 去重（与错误码 1007、Redis 幂等写呼应）。
3. 【强制】关闭自动提交（`enable.auto.commit = false`）：处理成功后再提交位移；处理失败进重试通道，禁"先提交后处理"。
4. 【强制】失败处理必须有终点：重试 N 次（建议 ≤5，指数退避）后进死信 topic `{原topic}.dlq` 并触发告警；DLQ 消息保留原始头（traceId、错误原因、原始 topic/partition/offset）。
5. 【强制】单条处理必须有时限（低于 `max.poll.interval.ms`），禁单条无限阻塞拖垮消费组触发反复 rebalance。
6. 【推荐】分区分配用 `cooperative-sticky`，减少再平衡影响面；监听 rebalance 事件记录日志。
7. 【推荐】消费 lag 常态监控告警（按业务 SLA 定阈值，如积压 > 10 分钟量）。
8. 【推荐】批消费场景用 poll 批次内逐条处理，禁跳过失败消息（跳过必须留痕进 DLQ）。

## 五、Schema 与演进

1. 【强制】消息 schema 带版本头（`x-schema-version`）；演进只做向后兼容变更（新增可选字段、禁改语义与类型、禁删必填字段）。
2. 【强制】破坏性变更必须新 topic（或新事件名），旧 topic 按保留策略自然退役。
3. 【推荐】引入 Schema Registry（Avro/Protobuf）治理 schema；未引入前版本头 + 业务仓登记为最低要求。
4. 【参考】事件契约文档随业务仓维护：事件名、字段、语义、生产者、消费者清单。

## 六、运维基线

1. 【强制】生产集群开启 ACL（SASL + TLS）；匿名与超管账号禁用于应用。
2. 【强制】监控告警至少覆盖：消费 lag、ISR 收缩（`min.insync.replicas` 不满足即告警）、Broker 磁盘水位（70% 治理）、生产/消费错误率。
3. 【强制】topic 变更（分区扩容、retention 调整）走变更评审并同步登记表。
4. 【推荐】Broker 磁盘按吞吐容量 2 倍冗余；日志与 metrics 保留策略与集群 retention 分开治理。
5. 【参考】自建实例加入 yarch 共享基础设施时，以 docker-compose 纳管，与既有中间件同等备份策略；Kafka 数据可重建（源头重放）时备份策略以源头为准。

---

## 附：选型与来源记录

**消息队列双轨分工（2026-09-01 落，可推翻）**：Kafka 管高吞吐事件流、日志管道、数据集成与 Rust 侧生产消费（四栈客户端全一线：rdkafka / franz-go；KRaft 单组件自托管）；**RocketMQ 管在线业务消息**（事务消息、延时消息、内建重试/DLQ，见 [rocketmq.md](rocketmq.md)）。同一业务事件默认只落一个 MQ，双写需评审。
- 可靠性基线（rf=3 / min.isr=2 / acks=all / 幂等生产者 / 关自动建 topic / 1MB 边界 / max.poll.interval 纪律）：Kafka 官方文档与 Confluent 工程指南通行口径。
- 命名与幂等消费、DLQ、lag 治理：业界通行实践，与 yarch 错误码 1007（幂等冲突）及 Redis 规约（幂等写用唯一索引）同源呼应。
