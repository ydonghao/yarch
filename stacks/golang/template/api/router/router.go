// Package router 路由注册：资源名词复数 kebab-case，路径带主版本 /api/v1（rest-conventions.md）。
package router

import (
	"github.com/cloudwego/hertz/pkg/app/server"

	"github.com/yuandonghao/yarch-go/template/api/handler"
	"github.com/yuandonghao/yarch-go/template/application"
)

// Register 注册全部路由（main.go 装配的最后一环）。
func Register(h *server.Hertz, app *application.App) {
	users := handler.NewUserHandler(app.Users)
	v1 := h.Group("/api/v1")
	v1.POST("/users", users.Create)
	v1.GET("/users", users.List)
	v1.GET("/users/:id", users.Get)
}
