# registry/ · 资源命名登记处

> **定位**：共享基础设施（一台服务器多项目共用 PG/Redis/Kafka/Nacos/MinIO…）的**租户边界标识唯一登记处**。全部 infra 规约的隔离模型（Redis key 前缀、Kafka/RocketMQ topic 前缀、Nacos Group、ES 索引前缀、MinIO bucket、Milvus collection、CH/SR 库名）都压在"服务名唯一"这一前提上——**本表就是那个前提的守门人**（架构评审 P0-4，2026-09-01）。

## 一、服务名规则（租户边界标识）

1. 【强制】格式 `^[a-z][a-z0-9-]{1,31}$`：小写字母数字短横线，字母开头，2~32 字符；禁下划线、大写、中文。
2. 【强制】禁裸通用词：`user`、`api`、`admin`、`gateway`、`common` 等单词语不得单独成名，必须带项目/领域前缀或后缀消歧（如 `ysaas-user`）。
3. 【强制】服务名一经登记并产生基础设施资源（key/topic/bucket…）即冻结：改名 = 资源迁移，走变更评审；本表保留改名记录。
4. 【强制】一个服务名一个属主（业务仓），跨仓共用一个服务名视为边界击穿事故。

## 二、服务名登记表

| 服务名 | 属主（仓库/项目） | 登记日期 | 启用的基础设施前缀 | 备注 |
|---|---|---|---|---|
| `ysaas` | ysaas（首个客户） | 待登记 | — | 示例行：首个业务工程接入时正式登记 |
| `yagent` | yagent | 待登记 | — | 示例行 |

> 登记方式：PR 修改本表即登记；重名在 CI/评审阶段拒绝。

## 三、派生资源登记指引（前缀必须以本表服务名为首段）

> 各项目**按需组合**基础设施规约（无全家桶假设，组合关系见 [README.md](README.md)「规约组合与依赖模型」）；本表只登记该项目实际启用的资源。

| 资源 | 登记处 | 规约出处 |
|---|---|---|
| Redis key / Kafka·RocketMQ topic / ES 索引 / Milvus collection / CH·SR 库 | 各业务仓 `docs/`（属主维护） | [infra/redis.md](infra/redis.md) 二-1、[infra/kafka.md](infra/kafka.md) 二-6、[infra/rocketmq.md](infra/rocketmq.md) 二-5 |
| MinIO bucket、XXL-Job 任务、Nacos Group | 各业务仓 `docs/` | [infra/minio-s3.md](infra/minio-s3.md) 一-4、[infra/xxl-job.md](infra/xxl-job.md) 一-3 |
| 错误码 3xxx+ 段位 | 各业务仓 `docs/` | [api/error-codes.md](api/error-codes.md) 段位分配 |

> 派生资源的首段（服务名）必须能在本表查到属主；查不到 = 未登记资源，CI/巡检视为违规。
