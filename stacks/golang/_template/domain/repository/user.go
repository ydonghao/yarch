// Package repository 领域接口（port）：由 infrastructure 实现（adapter）。
// 领域层定义接口、基础设施层实现——依赖倒置，替换实现不改业务代码。
package repository

import (
	"context"

	"github.com/yuandonghao/yarch-go/response"

	"{{.Module}}/domain/entity"
)

// UserRepo 用户仓储接口。
type UserRepo interface {
	Create(ctx context.Context, u *entity.User) error
	FindByID(ctx context.Context, id string) (*entity.User, error)
	Page(ctx context.Context, q response.PageQuery) (*response.PageData[entity.User], error)
}
