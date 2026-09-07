package testcontainers

import (
	"context"
	"testing"
	"time"

	"github.com/redis/go-redis/v9"

	"github.com/ydonghao/yarch/stacks/golang/captcha"
	"github.com/ydonghao/yarch/stacks/golang/redix"
)

// 行为级：Redis 分布式限流（固定窗口 Lua：窗口内超限拒绝、窗口滚动放行）。
func TestRateLimiterRedis(t *testing.T) {
	addr := StartRedis(t)
	rdb := redis.NewClient(&redis.Options{Addr: addr})
	defer rdb.Close()
	ctx := context.Background()

	keys, _ := redix.NewKeys("mysvc")
	rl := redix.NewRateLimiter(rdb)
	key := keys.K("ratelimit", "demo")

	allow := 0
	for i := 0; i < 5; i++ {
		if rl.Allow(ctx, key, 3, time.Minute) {
			allow++
		}
	}
	if allow != 3 {
		t.Fatalf("limit 3 got %d allowed", allow)
	}
	// 独立 key 互不影响
	if !rl.Allow(ctx, keys.K("ratelimit", "other"), 3, time.Minute) {
		t.Fatal("独立 key 应放行")
	}
}

// 行为级：验证码 RedisStore——GETDEL 原子一次性（校验后即消费）+ TTL 生效。
func TestCaptchaRedisStore(t *testing.T) {
	addr := StartRedis(t)
	rdb := redis.NewClient(&redis.Options{Addr: addr})
	defer rdb.Close()
	ctx := context.Background()

	store := captcha.NewRedisStore(rdb)
	key := "mysvc:captcha:c1"

	if err := store.Issue(ctx, key, "AB3CD", time.Minute); err != nil {
		t.Fatalf("issue: %v", err)
	}
	if got, ok, _ := store.Consume(ctx, key); !ok || got != "AB3CD" {
		t.Fatalf("consume = %q %v", got, ok)
	}
	if _, ok, _ := store.Consume(ctx, key); ok {
		t.Fatal("一次性：二次消费必须失败")
	}
}
