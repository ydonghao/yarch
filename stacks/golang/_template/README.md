# template · DDD 七包业务工程模板

coze-studio / yagent 同构七包（`api / application / domain / crossdomain / infra / pkg / types` + 根 `main.go` + `conf`）。
示例域 users：创建 / 分页 / 单查，演示契约全链路（信封、201、traceId 贯穿、D6 越界空页、1001/1004、业务码 3001）。

## 目录

```
├── main.go               # 单入口：env → logx → application.Init → web.Setup → router → Run
├── api/                  # handler / router / model（协议转换层，零业务逻辑）
├── application/          # 组装根（手写显式 Init 分阶段）
├── domain/               # entity（业务规则）+ repository（port 接口）
├── crossdomain/          # 域间防腐层（域与域禁止直接依赖）
├── infra/                # adapter：database（GORM 实现 + migration/）等
├── pkg/                  # 工程内无业务工具（跨项目件上沉 yarch-go 平台 module）
├── types/                # errno（业务码 3xxx+ 注册）/ consts
└── conf/                 # 运行时文件配置（凭证走环境变量）
```

## 生成新工程（安装）

```bash
# 在 yarch 仓 stacks/golang/ 下（发版前）
go run ./cmd/yarch-init -module github.com/you/your-svc -out ~/code/your-svc

# yarch-go 发 tag 后（Go 官方工具等价路径）
go run golang.org/x/tools/cmd/gonew@latest \
  {{.Module}} github.com/you/your-svc ~/code/your-svc
```

生成后：服务名（= module 末段，过 registry.md 一-1 校验）去 yarch 仓登记；`types/errno` 业务码段在本仓 docs 登记；yarch-go 发版后删 go.mod 的 replace 行改正式号。

## 起跑

```bash
cp .env.example .env   # 填 PG/Redis 地址（内网用户连共享实例：每服务独立 database，启动自动建库）
go mod tidy && go run .   # :8080，建库 + 迁移自动执行
go test ./...             # 冒烟（stub 仓储，不需要容器）
```

无共享 PG/Redis 环境时（本地档）：`docker compose up -d` 起本机 PG+Redis，.env 主机名改 localhost。

## 分层纪律（depguard 机检）

- `domain/` 零技术框架依赖（禁 hertz/gorm/redis import）；`api/` 不直依赖 `infra/`；
- 跨域调用必须走 `crossdomain/`；迁移版本化进仓（`infra/database/migration`，禁 AutoMigrate）。
