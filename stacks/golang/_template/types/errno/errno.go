// Package errno 业务码注册处（3xxx-8xxx：在本仓 docs 登记后方可使用，error-codes.md 段位表）。
package errno

import "github.com/yuandonghao/yarch/stacks/golang/errcode"

// 业务码登记（init 注册模式：进程启动即校验段位/白名单/冲突）。
func init() {
	errcode.Register(3001, "UserNameRequired", "用户名不能为空", 400)
	errcode.Register(3002, "UserNameDuplicated", "用户名已存在", 409)
}

// 业务码常量（domain/application 引用）。
const (
	UserNameRequired   = errcode.Code(3001)
	UserNameDuplicated = errcode.Code(3002)
)
