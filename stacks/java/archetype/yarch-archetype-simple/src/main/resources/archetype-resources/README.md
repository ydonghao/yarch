# ${artifactId}

yarch 阿里五层工程（`yarch-archetype-simple` 生成，简单贫血档）。契约唯一权威来源：yarch 仓 `contract/`。

## 快速开始

```bash
docker compose up -d        # 本地 PG17 + Redis7
mvn spring-boot:run         # Flyway 自动迁移
curl -X POST localhost:8080/api/v1/users \
  -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: demo-001' \
  -d '{"email":"a@b.c","name":"alice"}'
curl 'localhost:8080/api/v1/users?page=1&pageSize=20'
```

## 必办登记（合规）

1. **服务名登记**：`${artifactId}` 到 yarch 仓 `contract/registry.md`。
2. **业务错误码登记**：`types/errno/` 的 3xxx+ 码在 `docs/errno.md` 登记。

## 分层速查（simple 档）

| 层 | 职责 |
|---|---|
| `controller/` | 转发 + 基本参数校验 + 信封（禁直接调 dao，ArchUnit 守护） |
| `service/` | 具体业务逻辑 |
| `manager/` | 按需：通用业务下沉 / 三方封装（简单场景留空） |
| `dao/` | 数据访问（MyBatis-Plus mapper） |
| `model/` | DO（表一一对应）/ dto（DO/DTO/Query 对象族） |

选型对照：出现状态机/不变式/可演化复杂规则 → 换 `yarch-archetype-ddd`（七包依赖倒置档）。

## 测试

`mvn verify`：ArchUnit 分层机检 + Testcontainers 全链路契约测试（与 ddd 档同一套验收语义）。
