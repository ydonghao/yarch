package middleware

import (
	"context"
	"time"

	"github.com/cloudwego/hertz/pkg/app"
)

// RateStore 分布式限流存储（实现见 redix.RateLimiter）。
type RateStore interface {
	Allow(ctx context.Context, key string, limit int, window time.Duration) bool
}

// RateLimitRedis 分布式限流（跨实例口径，按路径维度；进程内档见 RateLimit）。
// 超 limit → 429+1006。key = 服务前缀由实现方 Keys 生成，此处以 path 传参。
func RateLimitRedis(store RateStore, perMinute, burst int, keyPrefix string) app.HandlerFunc {
	window := time.Minute
	return func(ctx context.Context, c *app.RequestContext) {
		key := keyPrefix + ":ratelimit:" + string(c.Path())
		limit := perMinute + burst
		if !store.Allow(ctx, key, limit, window) {
			WriteError(c, 1006, "")
			c.Abort()
			return
		}
		c.Next(ctx)
	}
}
