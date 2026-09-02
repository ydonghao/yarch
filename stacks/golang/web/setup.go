package web

import (
	"log/slog"
	"time"

	"github.com/cloudwego/hertz/pkg/app/server"
	"github.com/hertz-contrib/cors"

	"github.com/yuandonghao/yarch-go/middleware"
)

// Options Setup 装配选项（零值即默认：CORS 宽松、无限流、无幂等）。
type Options struct {
	// CORS 配置；nil = 开发档宽松（AllowAllOrigins）。生产必须显式收紧（nginx/higress 入口终结）。
	CORS *cors.Config
	// IdemStore 非 nil 时挂幂等中间件（建议注入 redix.NewIdempotency）。
	IdemStore middleware.IdempotencyStore
	// IdemTTL 幂等记录保留时长（契约下限 24h，默认 24h）。
	IdemTTL time.Duration
	// RatePerSecond > 0 时挂进程内限流（分布式档触发式）。
	RatePerSecond float64
	RateBurst     int
}

// Setup 一行挂全中间件链（顺序即语义，G5 显式装配）：
//
//	Trace（最先：入口保证 traceId）→ Recovery → AccessLog → CORS → 幂等 → 限流
//
// 业务工程 main.go 里 yarch.Setup(h, opts) 后注册路由即可。
func Setup(h *server.Hertz, log *slog.Logger, opts Options) {
	h.Use(middleware.Trace())
	h.Use(middleware.Recovery(log))
	h.Use(middleware.AccessLog(log))

	if opts.CORS != nil {
		h.Use(cors.New(*opts.CORS))
	} else {
		h.Use(cors.Default())
	}

	if opts.IdemStore != nil {
		ttl := opts.IdemTTL
		if ttl <= 0 {
			ttl = 24 * time.Hour
		}
		h.Use(middleware.Idempotency(opts.IdemStore, ttl, log))
	}
	if opts.RatePerSecond > 0 {
		h.Use(middleware.RateLimit(opts.RatePerSecond, opts.RateBurst))
	}
}
