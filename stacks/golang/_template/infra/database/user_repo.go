// Package database 基础设施适配器（adapter）：实现 domain/repository 接口（port）。
// GORM 细节不外泄——出入参只有领域实体与契约类型。
package database

import (
	"context"
	"errors"

	"gorm.io/gorm"

	"github.com/ydonghao/yarch/stacks/golang/errcode"
	"github.com/ydonghao/yarch/stacks/golang/persist"
	"github.com/ydonghao/yarch/stacks/golang/response"
	"github.com/ydonghao/yarch/stacks/golang/xerror"

	"{{.Module}}/domain/entity"
)

// userDO 显式列清单模型（PG 规约：is_deleted 逻辑删除 + 审计时间戳）。
type userDO struct {
	persist.Model
	Name string `gorm:"column:name;size:128"`
}

func (userDO) TableName() string { return "users" }

// UserRepo GORM 实现。
type UserRepo struct{ db *gorm.DB }

func NewUserRepo(db *gorm.DB) *UserRepo { return &UserRepo{db: db} }

func (r *UserRepo) Create(ctx context.Context, u *entity.User) error {
	err := r.db.WithContext(ctx).Create(&userDO{Model: persist.Model{ID: u.ID}, Name: u.Name}).Error
	if err != nil {
		return xerror.FromError(err)
	}
	return nil
}

func (r *UserRepo) FindByID(ctx context.Context, id string) (*entity.User, error) {
	var do userDO
	err := r.db.WithContext(ctx).First(&do, "id = ?", id).Error
	if err != nil {
		if errors.Is(err, gorm.ErrRecordNotFound) {
			return nil, xerror.New(errcode.NotFound, "user "+id)
		}
		return nil, xerror.FromError(err)
	}
	return toEntity(do), nil
}

func (r *UserRepo) Page(ctx context.Context, q response.PageQuery) (*response.PageData[entity.User], error) {
	p, err := persist.PageOf[userDO](r.db.WithContext(ctx).Model(&userDO{}), q)
	if err != nil {
		return nil, xerror.FromError(err)
	}
	list := make([]entity.User, 0, len(p.List))
	for _, do := range p.List {
		list = append(list, *toEntity(do))
	}
	return response.NewPageData(list, p.Total, p.Page, p.PageSize), nil
}

func toEntity(do userDO) *entity.User {
	return &entity.User{ID: do.ID, Name: do.Name, CreatedAt: do.CreatedAt.UTC()}
}
