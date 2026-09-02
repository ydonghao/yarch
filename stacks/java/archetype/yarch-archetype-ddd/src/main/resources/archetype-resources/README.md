# ${artifactId}

yarch DDD 七包工程（`yarch-archetype-ddd` 生成）。契约唯一权威来源：yarch 仓 `contract/`。

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

1. **服务名登记**：`${artifactId}` 到 yarch 仓 `contract/registry.md`（key 前缀/资源命名的前提）。
2. **业务错误码登记**：`types/errno/` 的 3xxx+ 码在 `docs/errno.md` 登记（未登记即用 CI 视为违规）。

## 分层速查（ddd 档）

| 包 | 职责 | 禁止 |
|---|---|---|
| `api/` | controller/dto/assembler：接入与信封 | 业务逻辑 |
| `application/` | 编排、事务边界 | 业务规则 |
| `domain/` | 模型/规则/仓储接口（零框架依赖，ArchUnit 守护） | Spring/MyBatis 依赖 |
| `crossdomain/` | 外部系统防腐 | 外部模型渗入 domain |
| `infrastructure/` | 仓储实现/技术细节 | 业务规则 |
| `types/` | 常量/错误码 | — |

模板选型：业务规则主要在 DB/SQL 的 CRUD/管理面 → 换 `yarch-archetype-simple`；有状态机/不变式 → 本模板。

## 测试

`mvn verify`：ArchUnit 分层机检 + Testcontainers 全链路契约测试（信封/traceId/分页 D6/逻辑删除/幂等/3xxx 业务码）。
