package middleware_test

import (
	"context"
	"fmt"
	"strings"
	"testing"
	"time"

	"github.com/cloudwego/hertz/pkg/app"
	"github.com/cloudwego/hertz/pkg/common/ut"

	"github.com/ydonghao/yarch/stacks/golang/middleware"
)

// 契约断言（对偶 java SignatureInterceptor）：HMAC 匹配 / 时间窗 ±300s /
// nonce 一次性消费 / 失败一律 401+2001。
func TestSignedApi(t *testing.T) {
	secrets := map[string]string{"web-client": "s3cret-key"}
	store := middleware.NewInMemoryNonceStore()
	h := newEngine(middleware.SignedApi(secrets, "demo-svc", store))
	h.POST("/api/v1/orders", func(ctx context.Context, c *app.RequestContext) {
		c.String(200, "ok")
	})
	body := `{"sku":"a"}`

	hdrs := func(ts, nonce, sign string) []ut.Header {
		return []ut.Header{
			{Key: "X-App-Key", Value: "web-client"},
			{Key: "X-Timestamp", Value: ts},
			{Key: "X-Nonce", Value: nonce},
			{Key: "X-Sign", Value: sign},
		}
	}
	sign := func(ts, nonce string) string {
		material := "POST" + "\n" + "/api/v1/orders" + "\n" + ts + "\n" + nonce + "\n" + body
		return middleware.HMACSHA256Hex("s3cret-key", material)
	}
	post := func(hs ...ut.Header) *ut.ResponseRecorder {
		return ut.PerformRequest(h.Engine, "POST", "/api/v1/orders",
			&ut.Body{Body: strings.NewReader(body), Len: len(body)}, hs...)
	}

	// ① 缺头 → 2001
	if e := decode(t, post().Body.String()); e.Code != 2001 {
		t.Fatalf("missing headers: want 2001, got %d", e.Code)
	}

	// ② 未知 appKey → 2001
	ts := fmt.Sprintf("%d", time.Now().UnixMilli())
	w := ut.PerformRequest(h.Engine, "POST", "/api/v1/orders",
		&ut.Body{Body: strings.NewReader(body), Len: len(body)},
		[]ut.Header{
			{Key: "X-App-Key", Value: "other"},
			{Key: "X-Timestamp", Value: ts},
			{Key: "X-Nonce", Value: "n-1"},
			{Key: "X-Sign", Value: sign(ts, "n-1")},
		}...)
	if e := decode(t, w.Body.String()); e.Code != 2001 {
		t.Fatalf("unknown app key: want 2001, got %d", e.Code)
	}

	// ③ 合法签名 → 200
	ts = fmt.Sprintf("%d", time.Now().UnixMilli())
	if w := post(hdrs(ts, "n-2", sign(ts, "n-2"))...); w.Code != 200 || w.Body.String() != "ok" {
		t.Fatalf("valid signature: want 200 ok, got %d %s", w.Code, w.Body.String())
	}

	// ④ 原样重放（同 nonce）→ 2001
	if e := decode(t, post(hdrs(ts, "n-2", sign(ts, "n-2"))...).Body.String()); e.Code != 2001 {
		t.Fatalf("replay: want 2001, got %d", e.Code)
	}

	// ⑤ 时间窗过期（>±300s）→ 2001
	stale := fmt.Sprintf("%d", time.Now().Add(-10*time.Minute).UnixMilli())
	if e := decode(t, post(hdrs(stale, "n-3", sign(stale, "n-3"))...).Body.String()); e.Code != 2001 {
		t.Fatalf("stale timestamp: want 2001, got %d", e.Code)
	}

	// ⑥ 签名不匹配（篡改 sign）→ 2001
	ts = fmt.Sprintf("%d", time.Now().UnixMilli())
	if e := decode(t, post(hdrs(ts, "n-4", sign(ts, "n-4")+"00")...).Body.String()); e.Code != 2001 {
		t.Fatalf("tampered sign: want 2001, got %d", e.Code)
	}
}
