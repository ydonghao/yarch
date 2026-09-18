package testcontainers

import (
	"context"
	"strings"
	"sync"
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

// V6/V7 行为级：Service 全链路（GETDEL 原子消费并发恰一过 + TTL 生效 + 存储 key 形状）。
func TestCaptchaServiceWithRedis(t *testing.T) {
	addr := StartRedis(t)
	rdb := redis.NewClient(&redis.Options{Addr: addr})
	defer rdb.Close()
	ctx := context.Background()

	keys, _ := redix.NewKeys("mysvc")
	svc, err := captcha.NewService(captcha.NewRedisStore(rdb), keys, captcha.Options{})
	if err != nil {
		t.Fatalf("NewService: %v", err)
	}
	issued, err := svc.Issue(ctx, "", "")
	if err != nil {
		t.Fatalf("issue: %v", err)
	}
	if issued.Provider != "image" || issued.Key == "" {
		t.Fatalf("issued = %+v", issued)
	}

	// 三-5：存储 key 形状 {服务名}:captcha:{provider}:{key}
	storeKey := "mysvc:captcha:image:" + issued.Key
	ttl, err := rdb.TTL(ctx, storeKey).Result()
	if err != nil || ttl <= 0 || ttl > 2*time.Minute {
		t.Fatalf("V7：TTL 异常 %v err=%v", ttl, err)
	}

	answer, err := rdb.Get(ctx, storeKey).Result()
	if err != nil {
		t.Fatalf("peek: %v", err)
	}

	// V6：8 并发校验恰一过（GETDEL 原子）
	const threads = 8
	var wg sync.WaitGroup
	wins := 0
	var mu sync.Mutex
	start := make(chan struct{})
	for i := 0; i < threads; i++ {
		wg.Add(1)
		go func() {
			defer wg.Done()
			<-start
			if svc.Verify(ctx, "", issued.Key, strings.ToLower(answer)) {
				mu.Lock()
				wins++
				mu.Unlock()
			}
		}()
	}
	close(start)
	wg.Wait()
	if wins != 1 {
		t.Fatalf("V6：并发校验应恰一过，实际 %d", wins)
	}
	if _, err := rdb.Get(ctx, storeKey).Result(); err == nil {
		t.Fatal("V2：校验后 key 已消费")
	}
}
