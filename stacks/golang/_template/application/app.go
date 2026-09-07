// Package application 组装根（G5：手写显式装配，coze-studio Init 分阶段同构）。
// 领域只见接口，实现在此注入——替换实现不改业务代码（无包级全局单例）。
package application

import (
	"context"

	"github.com/redis/go-redis/v9"
	"gorm.io/gorm"

	"github.com/ydonghao/yarch/stacks/golang/middleware"
	"github.com/ydonghao/yarch/stacks/golang/persist"
	"github.com/ydonghao/yarch/stacks/golang/redix"

	"{{.Module}}/domain/repository"
	"{{.Module}}/infra/database"
)

// App 组装产物：handler 依赖的唯一入口（显式注入）。
type App struct {
	DB        *gorm.DB
	RDB       *redis.Client
	Keys      *redix.Keys
	IdemStore middleware.IdempotencyStore
	Users     repository.UserRepo
}

// Init 分阶段组装：基础件（迁移/PG/Redis）→ 仓储（依赖注入）。
// dsn/redisAddr 为空时进入降级模式（本地冒烟；生产必配，见 main.go 校验）。
func Init(ctx context.Context, dsn, redisAddr, service string) (*App, error) {
	app := &App{}

	// 阶段一：基础件（共享 PG 实例隔离：服务独立 database，启动自动建库）
	if dsn != "" {
		if err := persist.EnsureDatabase(dsn); err != nil {
			return nil, err
		}
		if err := database.MigrateUp(dsn); err != nil {
			return nil, err
		}
		db, err := persist.Open(dsn)
		if err != nil {
			return nil, err
		}
		app.DB = db
	}
	keys, err := redix.NewKeys(service)
	if err != nil {
		return nil, err
	}
	app.Keys = keys
	if redisAddr != "" {
		app.RDB = redis.NewClient(&redis.Options{Addr: redisAddr})
		app.IdemStore = redix.NewIdempotency(app.RDB)
	}

	// 阶段二：仓储（领域接口 ← 基础设施实现）
	if app.DB != nil {
		app.Users = database.NewUserRepo(app.DB)
	}

	return app, nil
}
