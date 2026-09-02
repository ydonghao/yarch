# Celery 开发规约（v1.0 已定稿）

> **状态：已定稿**（2026-09-01 评审通过）。供人阅读、供 AI 作为规范上下文使用。
> 等级：**【强制】**违反即缺陷；【推荐】默认遵守、评审后可豁免；**【参考】**正向引导。适用 Celery 5.x（Python 分布式任务队列）。分工与来源见文末附录。

## 一、定位与分工

1. 【强制】Celery 定位：**Python 生态的分布式任务队列**——异步任务（耗时操作后台化）与周期任务；与服务间事件流（Kafka/RocketMQ）、Java 系时间驱动调度（XXL-Job）三分：跨服务事件归 MQ，纯时间触发且 Java 栈归 XXL-Job，Python 进程内/跨 worker 任务归 Celery。同项目混用两件以上必须登记边界（谁承担什么触发语义）。
2. 【强制】任务三问上线前回答：触发方（API 请求内 / 周期 / 事件）、失败兜底（重试耗尽后去哪）、幂等键是什么——答不出不上线。
3. 【推荐】轻量"发个任务"场景先评估 `asyncio`/线程池；跨进程、需重试与水平扩展才引入 Celery。

## 二、Broker 与命名（共享实例的租户纪律）

1. 【强制】broker 二选一并登记：Redis（简单部署）或 RabbitMQ（任务语义完备：路由/优先级/ACK 稳健）。共享 Redis 实例时**必须配置全局 key 前缀**：`broker_transport_options = {"global_keyprefix": "{服务名}:"}`——Celery 默认 key（`celery-task-meta-*`、`unacked` 等）无前缀，共享实例下必撞（对齐 [redis.md](redis.md) 二-1 租户边界）。
2. 【强制】队列命名 `{服务名}.{用途}`（`yagent.embed-scan`），`task_routes` 显式路由——**禁默认单队列**（一个慢任务堵死全部，对齐 Kafka 消费组隔离哲学）。
3. 【强制】task 命名 `{服务名}.{模块}.{动作}`（`yagent.embedding.rebuild`），显式 `name=`，禁依赖自动生成名（重构即断链）。
4. 【推荐】result backend 按需启用（仅需要取回结果的任务）；启用时同样带服务前缀，TTL 必设（`result_expires`，默认 1 天内）。

## 三、任务纪律

1. 【强制】**幂等是默认假设**：Celery 交付为至少一次（`acks_late` + `task_reject_on_worker_lost` 时更是必重）——幂等键按业务键或唯一约束兜底（上位口径见 [../api/rest-conventions.md](../api/rest-conventions.md) 幂等总则）。
2. 【强制】超时显式：`soft_time_limit`（可捕获清理）+ `hard_time_limit`（硬杀）都必配，禁默认无限执行；单批数据量与超时匹配。
3. 【强制】重试显式：`max_retries` 有上限（≤5）、指数退避 + 抖动（`retry_backoff` + `retry_jitter`）；禁用重试掩盖代码 bug——确定性异常（参数错、权限缺）直接失败进告警，不重试。
4. 【强制】失败有终点：重试耗尽的任务走 `errback`/信号落库告警（失败任务表），闭环人工处理——Celery 无内建死信队列，"失败任务表 + 告警 + 重投工具"即 DLQ 等价物（对齐 [kafka.md](kafka.md) 四-4 死信闭环思想）。
5. 【强制】序列化只允许 `json`：`task_serializer = json`、`accept_content = ["json"]`，**pickle 全局禁用**（远程代码执行风险；与各存储"禁语言原生序列化"条文同源）。
6. 【推荐】大任务拆批（chunk/map）+ 游标续跑，单任务可中断可续（对齐 [xxl-job.md](xxl-job.md) 三-4）。

## 四、Worker 与调度

1. 【强制】worker 按队列专享部署（`-Q {服务名}.{用途}`），并发模型与超时配套：prefork 配硬杀，async 池配上下文超时；资源画像（内存/CPU）登记。
2. 【强制】优雅发布：`warm_shutdown` 等待在跑任务，超时上限与部署窗口匹配；禁直接 SIGKILL 丢任务（`acks_late` 下会重投，`acks_on_failure` 场景会丢）。
3. 【强制】beat 调度器**单实例**（或用分布式锁方案如 redbeat）：多实例 beat = 周期任务全量双跑。周期任务清单登记（cron、任务、幂等策略、属主——对齐 XXL-Job 任务登记）。
4. 【强制】任务链路 traceId：来自 HTTP 请求的任务**继承调用方 traceId**（作为任务头/参数传入），周期任务每轮新建——口径对齐 [../api/logging-trace.md](../api/logging-trace.md)「跨进程传播矩阵」的消息/定时档；任务日志携带 traceId。
5. 【推荐】`acks_late = True` 提高交付可靠（配合幂等）；`worker_prefetch_multiplier = 1` 长任务防饿死后续。

## 五、运维基线

1. 【强制】监控：各队列积压（depth）、任务失败率与 P95 耗时、worker 存活、broker 资源（共享 Redis 时并入其水位治理 [redis.md](redis.md) 八-2）。
2. 【强制】broker 与 result backend 的凭证走环境变量/配置中心（[nacos.md](nacos.md) 三-4 加密纪律），禁硬编码入库。
3. 【推荐】事件流（`events`）+ 监控面板（Flower 或自采）常态化；周期任务执行历史可查。
4. 【推荐】以 docker-compose 纳管共享基础设施；worker 与 web 进程分离部署，独立扩缩。

---

## 附：分工与来源

- **分工决策（可推翻）**：Celery=Python 任务队列（异步+周期）；XXL-Job=Java 系时间驱动调度；Kafka/RocketMQ=跨服务事件。Python 项目里"调度语义"归 Celery beat，不为此引入 XXL-Job。
- broker 前缀/序列化/ack 语义/优雅关机/beat 单实例：Celery 官方文档（Configuration 与 Best Practices 章）通行口径。
- 幂等、DLQ 闭环、traceId 传播、登记机制：与 yarch 既有规约（幂等总则、kafka.md、xxl-job.md、logging-trace.md）同源对齐（协作参考级，见 [../README.md](../README.md) 组合模型）。
