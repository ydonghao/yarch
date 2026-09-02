package middleware

import (
	"context"
	"log/slog"
	"time"

	"github.com/cloudwego/hertz/pkg/app"

	"github.com/yuandonghao/yarch/stacks/golang/logx"
)

// AccessLog 请求完成日志（logging-trace.md 日志行协议示例同构：method/path/status/costMs）。
func AccessLog(log *slog.Logger) app.HandlerFunc {
	l := logx.Sub(log, "middleware.AccessLog")
	return func(ctx context.Context, c *app.RequestContext) {
		start := time.Now()
		c.Next(ctx)
		cost := time.Since(start).Milliseconds()
		l.InfoContext(ctx, "request completed",
			slog.String("method", string(c.Method())),
			slog.String("path", string(c.Path())),
			slog.Int("status", c.Response.StatusCode()),
			slog.Int64("costMs", cost),
		)
	}
}
