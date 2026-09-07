package middleware_test

import (
	"context"
	"encoding/json"
	"log/slog"
	"strings"
	"sync"
	"testing"
	"time"

	"github.com/cloudwego/hertz/pkg/app"
	"github.com/cloudwego/hertz/pkg/app/server"
	"github.com/cloudwego/hertz/pkg/common/ut"
	"github.com/cloudwego/hertz/pkg/route/param"

	"github.com/ydonghao/yarch/stacks/golang/errcode"
	"github.com/ydonghao/yarch/stacks/golang/middleware"
	"github.com/ydonghao/yarch/stacks/golang/response"
)

func testLogger() *slog.Logger {
	return slog.New(slog.DiscardHandler)
}

func newEngine(mw ...app.HandlerFunc) *server.Hertz {
	h := server.Default(server.WithHostPorts(":0"))
	for _, m := range mw {
		h.Use(m)
	}
	return h
}

type envelope struct {
	Code    int             `json:"code"`
	Message string          `json:"message"`
	Data    json.RawMessage `json:"data"`
	TraceID string          `json:"traceId"`
}

func decode(t *testing.T, body string) envelope {
	t.Helper()
	var e envelope
	if err := json.Unmarshal([]byte(body), &e); err != nil {
		t.Fatalf("not envelope json: %v (%s)", err, body)
	}
	return e
}

// 契约断言：traceId 入口三级（traceparent 优先 → X-Trace-Id → 生成）+ 响应头恒回显。
func TestTrace(t *testing.T) {
	h := newEngine(middleware.Trace())
	h.GET("/api/v1/ping", func(ctx context.Context, c *app.RequestContext) {
		c.String(200, c.GetString(middleware.TraceIDKey))
	})

	// ① traceparent 优先（取 trace-id 段）
	w := ut.PerformRequest(h.Engine, "GET", "/api/v1/ping", nil, ut.Header{Key: "traceparent", Value: "00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01"})
	if string(w.Body.Bytes()) != "0af7651916cd43dd8448eb211c80319c" {
		t.Fatalf("traceparent 优先失败: %s", w.Body.Bytes())
	}
	if got := w.Header().Get("X-Trace-Id"); got != "0af7651916cd43dd8448eb211c80319c" {
		t.Fatalf("X-Trace-Id 回显 = %q", got)
	}

	// ② 无 traceparent 看 X-Trace-Id
	w = ut.PerformRequest(h.Engine, "GET", "/api/v1/ping", nil, ut.Header{Key: "X-Trace-Id", Value: "mytrace"})
	if string(w.Body.Bytes()) != "mytrace" {
		t.Fatalf("X-Trace-Id 降级失败: %s", w.Body.Bytes())
	}

	// ③ 再无则生成 32 位小写 hex
	w = ut.PerformRequest(h.Engine, "GET", "/api/v1/ping", nil)
	tid := string(w.Body.Bytes())
	if len(tid) != 32 || strings.ToLower(tid) != tid {
		t.Fatalf("生成 traceId 形状错误: %q", tid)
	}
	if w.Header().Get("X-Trace-Id") != tid {
		t.Fatal("生成路径未回显")
	}

	// ④ 非法 traceparent 忽略
	w = ut.PerformRequest(h.Engine, "GET", "/api/v1/ping", nil, ut.Header{Key: "traceparent", Value: "garbage"})
	if string(w.Body.Bytes()) == "garbage" {
		t.Fatal("非法 traceparent 必须忽略")
	}
}

// 契约断言：未预期失败 500 + code=1000 + message 固定「内部错误」，堆栈只进日志。
func TestRecovery(t *testing.T) {
	var logBuf strings.Builder
	log := slog.New(slog.NewJSONHandler(&logBuf, nil))
	h := newEngine(middleware.Trace(), middleware.Recovery(log))
	h.GET("/api/v1/boom", func(ctx context.Context, c *app.RequestContext) {
		panic("boom")
	})
	w := ut.PerformRequest(h.Engine, "GET", "/api/v1/boom", nil)
	if w.Code != 500 {
		t.Fatalf("http = %d", w.Code)
	}
	e := decode(t, w.Body.String())
	if e.Code != 1000 || e.Message != "内部错误" || string(e.Data) != "null" {
		t.Fatalf("envelope = %+v", e)
	}
	if !strings.Contains(logBuf.String(), `"panic"`) || !strings.Contains(logBuf.String(), `"stack"`) {
		t.Fatalf("堆栈未折叠进日志: %s", logBuf.String())
	}
}

func TestRateLimit(t *testing.T) {
	h := newEngine(middleware.Trace(), middleware.RateLimit(0.001, 1))
	h.POST("/api/v1/x", func(ctx context.Context, c *app.RequestContext) { c.String(200, "ok") })
	for i := 0; i < 3; i++ {
		w := ut.PerformRequest(h.Engine, "POST", "/api/v1/x", nil)
		if i == 0 && w.Code != 200 {
			t.Fatalf("first should pass, got %d", w.Code)
		}
		if i > 0 {
			if w.Code != 429 {
				t.Fatalf("third should be 429, got %d", w.Code)
			}
			e := decode(t, w.Body.String())
			if e.Code != 1006 || e.Message != "触发限流" {
				t.Fatalf("envelope = %+v", e)
			}
		}
	}
}

// 内存幂等存储（模拟 redix 语义）。
type memStore struct {
	mu   sync.Mutex
	data map[string][]byte // key -> record json
}

func newMemStore() *memStore { return &memStore{data: map[string][]byte{}} }

func (m *memStore) Reserve(ctx context.Context, key, digest string, ttl time.Duration) (bool, error) {
	m.mu.Lock()
	defer m.mu.Unlock()
	if _, ok := m.data[key]; ok {
		return false, nil
	}
	m.data[key] = middleware.MarshalIdemRecord(digest, 0, nil) // 占位：无响应 = pending
	return true, nil
}

func (m *memStore) Lookup(ctx context.Context, key, digest string) (int, []byte, error) {
	m.mu.Lock()
	defer m.mu.Unlock()
	rec, ok := m.data[key]
	if !ok {
		return 0, nil, middleware.ErrIdempotencyPending
	}
	r, _ := middleware.UnmarshalIdemRecord(rec)
	if r.Digest != digest {
		return 0, nil, middleware.ErrIdempotencyMismatch
	}
	if r.Status == 0 {
		return 0, nil, middleware.ErrIdempotencyPending
	}
	return r.Status, r.Body, nil
}

func (m *memStore) Complete(ctx context.Context, key, digest string, status int, body []byte, ttl time.Duration) error {
	m.mu.Lock()
	defer m.mu.Unlock()
	m.data[key] = middleware.MarshalIdemRecord(digest, status, body)
	return nil
}

// 契约断言：同键同参回放原响应；同键异参 1007；GET 不参与。
func TestIdempotency(t *testing.T) {
	store := newMemStore()
	h := newEngine(middleware.Trace(), middleware.Idempotency(store, 24*time.Hour, testLogger()))
	h.POST("/api/v1/orders", func(ctx context.Context, c *app.RequestContext) {
		var req struct {
			Sku string `json:"sku"`
		}
		_ = c.BindJSON(&req)
		c.JSON(201, response.Ok(map[string]string{"sku": req.Sku}, c.GetString(middleware.TraceIDKey)))
	})

	hdr := ut.Header{Key: "Idempotency-Key", Value: "idem-1"}
	w1 := ut.PerformRequest(h.Engine, "POST", "/api/v1/orders", &ut.Body{Body: strings.NewReader(`{"sku":"a"}`), Len: 12}, hdr)
	if w1.Code != 201 {
		t.Fatalf("first = %d", w1.Code)
	}
	w2 := ut.PerformRequest(h.Engine, "POST", "/api/v1/orders", &ut.Body{Body: strings.NewReader(`{"sku":"a"}`), Len: 12}, hdr)
	if w2.Code != 201 || w2.Body.String() != w1.Body.String() {
		t.Fatalf("回放必须逐字节一致:\n%s\n%s", w1.Body, w2.Body)
	}
	if w2.Header().Get("Idempotency-Replayed") != "true" {
		t.Fatal("回放应标记 Idempotency-Replayed")
	}
	w3 := ut.PerformRequest(h.Engine, "POST", "/api/v1/orders", &ut.Body{Body: strings.NewReader(`{"sku":"b"}`), Len: 12}, hdr)
	if w3.Code != 409 {
		t.Fatalf("异参 = %d", w3.Code)
	}
	e := decode(t, w3.Body.String())
	if e.Code != 1007 {
		t.Fatalf("异参 code = %d", e.Code)
	}

	// pending 竞争：占位但未 Complete → 1007
	store2 := newMemStore()
	_, _ = store2.Reserve(context.Background(), "k", "d", time.Hour)
	_, _, err := store2.Lookup(context.Background(), "k", "d")
	if err != middleware.ErrIdempotencyPending {
		t.Fatalf("pending 哨兵 = %v", err)
	}
}

// 信封形状：WriteError 输出四字段（traceId 与 X-Trace-Id 恒等）。
func TestWriteErrorEnvelope(t *testing.T) {
	h := newEngine(middleware.Trace(), middleware.Recovery(testLogger()))
	h.GET("/api/v1/e", func(ctx context.Context, c *app.RequestContext) {
		middleware.WriteError(c, errcode.NotFound, "order o-1")
	})
	w := ut.PerformRequest(h.Engine, "GET", "/api/v1/e", nil, ut.Header{Key: "traceparent", Value: "00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01"})
	if w.Code != 404 {
		t.Fatalf("http = %d", w.Code)
	}
	if got := w.Header().Get("X-Trace-Id"); got != "0af7651916cd43dd8448eb211c80319c" {
		t.Fatalf("X-Trace-Id = %q", got)
	}
	e := decode(t, w.Body.String())
	if e.Code != 1004 || e.Message != "资源不存在：order o-1" || e.TraceID != "0af7651916cd43dd8448eb211c80319c" {
		t.Fatalf("envelope = %+v", e)
	}
}

var _ = param.Params{}
