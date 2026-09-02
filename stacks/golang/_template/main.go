// yarch-go-template 服务入口：env → logx → application.Init → web.Setup → router → Run（G5 显式装配）。
package main

import (
	"context"
	"log/slog"
	"os"

	"github.com/cloudwego/hertz/pkg/app/server"
	"github.com/joho/godotenv"

	"github.com/yuandonghao/yarch/stacks/golang/logx"
	"github.com/yuandonghao/yarch/stacks/golang/web"

	"{{.Module}}/api/router"
	"{{.Module}}/application"
)

// 服务名须过 registry.md 一-1 校验（^[a-z][a-z0-9-]{1,31}$），生成工程后去 yarch 仓登记。
const service = "{{.Service}}"

func main() {
	ctx := context.Background()

	// env → 日志（.env 三环境：local/debug/release）
	_ = godotenv.Load(envFiles()...)
	env := getenv("APP_ENV", "local")
	log := logx.New(service, env, logx.ParseLevel(getenv("LOG_LEVEL", "INFO")), os.Stdout)

	// 组装根：迁移/PG/Redis → 仓储
	app, err := application.Init(ctx, os.Getenv("DATABASE_URL"), os.Getenv("REDIS_ADDR"), service)
	if err != nil {
		log.Error("application init failed", slog.String("err", err.Error()))
		os.Exit(1)
	}

	// Hertz + 中间件链 + 路由
	h := server.Default(server.WithHostPorts(getenv("LISTEN_ADDR", ":8080")))
	web.Setup(h, log, web.Options{IdemStore: app.IdemStore})
	router.Register(h, app)

	h.Spin()
}

func envFiles() []string {
	files := []string{}
	for _, f := range []string{".env." + os.Getenv("APP_ENV"), ".env"} {
		if f != ".env." {
			files = append(files, f)
		}
	}
	return files
}

func getenv(k, def string) string {
	if v := os.Getenv(k); v != "" {
		return v
	}
	return def
}
