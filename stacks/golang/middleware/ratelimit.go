package middleware

import (
	"context"

	"github.com/cloudwego/hertz/pkg/app"
	"golang.org/x/time/rate"

	"github.com/ydonghao/yarch/stacks/golang/errcode"
)

// RateLimit 进程内限流（令牌桶，单实例档）：超限 → 429 + 1006。
// 分布式限流（Redis 档）按触发式增长（对偶 java @RateLimited SPI）。
func RateLimit(perSecond float64, burst int) app.HandlerFunc {
	lim := rate.NewLimiter(rate.Limit(perSecond), burst)
	return func(ctx context.Context, c *app.RequestContext) {
		if !lim.Allow() {
			WriteError(c, errcode.RateLimited, "")
			c.Abort()
			return
		}
		c.Next(ctx)
	}
}
