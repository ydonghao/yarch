package redix

import (
	"context"
	"time"

	"github.com/redis/go-redis/v9"
)

// 固定窗口计数（原子）：首次置位即带过期。
var rateLua = redis.NewScript(`
local n = redis.call("INCR", KEYS[1])
if n == 1 then
	redis.call("PEXPIRE", KEYS[1], ARGV[1])
end
if n > tonumber(ARGV[2]) then
	return 0
end
return 1`)

// RateLimiter Redis 分布式限流（固定窗口档；key 经 Keys.K 生成，首段=服务名）。
type RateLimiter struct{ rdb redis.UniversalClient }

func NewRateLimiter(rdb redis.UniversalClient) *RateLimiter { return &RateLimiter{rdb: rdb} }

// Allow 窗口内第 limit 次以内放行；存储故障 fail-open 放行（限流是保护增强，不阻断业务）。
func (r *RateLimiter) Allow(ctx context.Context, key string, limit int, window time.Duration) bool {
	v, err := rateLua.Run(ctx, r.rdb, []string{key}, window.Milliseconds(), limit).Int()
	if err != nil {
		return true
	}
	return v == 1
}
