# yarch

> **Y**uan's **Arch**itecture —— 多栈工程架构体系：一种规范，多种方言。

个人开源项目（Apache-2.0）。yarch 是跨项目复用的工程架构平台：契约可执行化（而非文档自觉）、AI 施工的模具（模板管结构，AI 生成不漂移）、发版式升级（构件走 version 变更，业务工程自动跟进）。

## 结构

- `contract/` 跨栈统一契约与规约（**唯一权威来源，当前工作重心**）：`api/` 四件套（RestResponse 形状 · 错误码段位 · 日志/traceId · REST 约定，**v1.0 已定稿**，D1-D6 按"业界标准优先"拍板）；`infra/` 基础设施规约 20 份**全部 v1.0 已定稿**（Nacos · Kafka · RocketMQ · Elasticsearch · RediSearch · MinIO/S3 · MongoDB · MySQL · PostgreSQL · TimescaleDB · Redis · Nginx · Higress · XXL-Job · Celery · ClickHouse · StarRocks · pgvector · Qdrant · Milvus）
- `stacks/` 栈模板层预留位：`java` · `golang` · `rust` · `web-react` · `web-vue`（规约定稿后落地）
- `clients/` 客户端预留位：`mobile` · `miniprogram` · `desktop`（Tauri，按项目需要生长）
- `tools/` 模板定位器 `locate-scaffolds`（将来长成 `yarch init` CLI）
- `docs/` 工程规范与架构设计（[architecture.md](docs/architecture.md) · [架构图](docs/architecture-diagram.png)）

## 契约速览

```json
{ "code": 0, "message": "成功", "data": { }, "traceId": "0af7651916cd43dd8448eb211c80319c" }
```

- `code`：0 成功；1000-1009 通用段、2001-2004 认证段（yarch 所有）；3xxx+ 业务工程注册
- 日志：ndjson，`ts/level/service/env/traceId/logger/msg`，traceId 贯穿（W3C traceparent + X-Trace-Id 回显）
- 详见 [contract/](contract/README.md)

## 状态

规约层梳理中（2026-09 起）：API 契约四件套 v1 草案待评审（D1-D6 待拍板）；数据与中间件规约三份已定稿（MySQL / PostgreSQL / Redis，2026-09-01 评审通过）。定稿后按栈落地实现。
