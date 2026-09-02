# yarch-go-template · DDD 七包业务工程模板

yarch-golang 默认档工程模板（coze-studio 同构七包：`cmd/api/application/domain/crossdomain/infrastructure/types`）。示例域 users：创建 / 分页 / 单查，演示契约全链路（信封、201、traceId 贯穿、D6 越界空页、1001/1004、业务码 3001、幂等键）。

## 生成新工程

```bash
# 方式一：gonew（自动替换 module path）
go run golang.org/x/tools/cmd/gonew@latest \
  github.com/yuandonghao/yarch-go/template github.com/yourname/your-svc ~/code/your-svc

# 方式二：手动复制 + 批量替换
cp -r template/ ~/code/your-svc && cd ~/code/your-svc
grep -rl 'github.com/yuandonghao/yarch-go/template' . | xargs sed -i '' 's|github.com/yuandonghao/yarch-go/template|github.com/yourname/your-svc|g'
rm go.sum && go mod tidy   # 删除 go.mod 中 replace ../ 行，改用正式版本号
```

生成后两件事：

1. `cmd/server/main.go` 的 `service` 常量改为你的服务名（须过 [registry.md 一-1](../../../contract/registry.md) 校验：`^[a-z][a-z0-9-]{1,31}$` + 禁裸通用词），并去 yarch 仓登记；
2. `types/errno` 的业务码段（3xxx 起）在**你的仓库 docs 登记**后方可使用。

## 本地起跑

```bash
docker compose up -d          # PG 17 + Redis 7
cp .env.example .env
make run                      # :8080，迁移自动执行
make test                     # 冒烟（stub 仓储，不需要容器）
make lint                     # depguard：domain 零框架依赖
```

## 分层纪律（depguard 机检）

- `domain/`：实体 + 仓储接口（port）。零技术框架依赖（禁 hertz/gorm/redis import）；
- `infrastructure/`：仓储实现（adapter）+ 版本化迁移（`infrastructure/database/migration`，禁 AutoMigrate）；
- `api/`：handler 只做协议转换，不直依赖 infrastructure；
- `application/`：组装根（手写显式 Init，替换实现不改业务代码）。
