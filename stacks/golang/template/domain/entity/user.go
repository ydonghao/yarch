// Package entity 领域实体：业务规则的家（依赖倒置核心——零技术框架依赖，depguard 机检）。
// 允许依赖：标准库 + yarch 契约内核（errcode/xerror）+ 本仓 types。
package entity

import (
	"strings"
	"time"

	"github.com/yuandonghao/yarch-go/xerror"

	"github.com/yuandonghao/yarch-go/template/types/errno"
)

// User 用户实体（充血但克制：业务行为进领域，api 层零业务逻辑）。
type User struct {
	ID        string
	Name      string
	CreatedAt time.Time
}

// New 创建用户（不变式：名称非空且 ≤128；违例 → 业务码 3001）。
func New(id, name string) (*User, error) {
	if strings.TrimSpace(name) == "" || len(name) > 128 {
		return nil, xerror.New(errno.UserNameRequired)
	}
	return &User{ID: id, Name: name}, nil
}

// Rename 改名（同一不变式的演化点）。
func (u *User) Rename(name string) error {
	if strings.TrimSpace(name) == "" || len(name) > 128 {
		return xerror.New(errno.UserNameRequired)
	}
	u.Name = name
	return nil
}
