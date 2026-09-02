package middleware_test

import (
	"context"
	"sync"
	"testing"
	"time"

	"github.com/cloudwego/hertz/pkg/app"
	"github.com/cloudwego/hertz/pkg/app/server"
	"github.com/cloudwego/hertz/pkg/common/ut"

	"github.com/yuandonghao/yarch/stacks/golang/middleware"
)

// memRateStore 分布式限流内存桩（真实 Redis 行为级见 testcontainers 子 module）。
type memRateStore struct {
	mu   sync.Mutex
	n    map[string]int
}

func (m *memRateStore) Allow(ctx context.Context, key string, limit int, window time.Duration) bool {
	m.mu.Lock()
	defer m.mu.Unlock()
	if m.n == nil {
		m.n = map[string]int{}
	}
	m.n[key]++
	return m.n[key] <= limit
}

func TestRateLimitRedis(t *testing.T) {
	store := &memRateStore{}
	h := server.Default(server.WithHostPorts(":0"))
	h.Use(middleware.Trace(), middleware.RateLimitRedis(store, 2, 0, "mysvc"))
	h.POST("/api/v1/x", func(ctx context.Context, c *app.RequestContext) { c.String(200, "ok") })

	for i := 0; i < 3; i++ {
		w := ut.PerformRequest(h.Engine, "POST", "/api/v1/x", nil)
		want := 200
		if i >= 2 {
			want = 429
		}
		if w.Code != want {
			t.Fatalf("req %d = %d, want %d", i, w.Code, want)
		}
	}
}

type memOpStore struct {
	mu    sync.Mutex
	recs  []middleware.OperationLogRecord
	fail  bool
}

func (m *memOpStore) Save(ctx context.Context, rec middleware.OperationLogRecord) error {
	if m.fail {
		return errBoom{}
	}
	m.mu.Lock()
	defer m.mu.Unlock()
	m.recs = append(m.recs, rec)
	return nil
}

type errBoom struct{}

func (errBoom) Error() string { return "boom" }

// 操作日志：unsafe 方法产出记录（traceId/userId 贯穿），GET 不产出；存储失败不阻断。
func TestOperationLog(t *testing.T) {
	store := &memOpStore{}
	h := server.Default(server.WithHostPorts(":0"))
	h.Use(middleware.Trace(), middleware.OperationLog(store, testLogger(), "yarch.claims.userid"))
	h.POST("/api/v1/orders", func(ctx context.Context, c *app.RequestContext) { c.String(201, "ok") })
	h.GET("/api/v1/orders", func(ctx context.Context, c *app.RequestContext) { c.String(200, "ok") })

	if w := ut.PerformRequest(h.Engine, "GET", "/api/v1/orders", nil); w.Code != 200 {
		t.Fatal("GET 不受影响")
	}
	w := ut.PerformRequest(h.Engine, "POST", "/api/v1/orders", nil,
		ut.Header{Key: "traceparent", Value: "00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01"})
	if w.Code != 201 {
		t.Fatalf("oplog 不得阻断响应: %d", w.Code)
	}

	deadline := time.Now().Add(time.Second)
	for time.Now().Before(deadline) {
		store.mu.Lock()
		n := len(store.recs)
		store.mu.Unlock()
		if n == 1 {
			r := store.recs[0]
			if r.Method != "POST" || r.Path != "/api/v1/orders" || r.Status != 201 ||
				r.TraceID != "0af7651916cd43dd8448eb211c80319c" || r.CostMs < 0 {
				t.Fatalf("record = %+v", r)
			}
			return
		}
		time.Sleep(20 * time.Millisecond)
	}
	t.Fatal("异步存储未在 1s 内完成")
}
