package httpx_test

import (
	"context"
	"net/http"
	"net/http/httptest"
	"testing"
	"time"

	"github.com/ydonghao/yarch/stacks/golang/errcode"
	"github.com/ydonghao/yarch/stacks/golang/httpx"
	"github.com/ydonghao/yarch/stacks/golang/logx"
)

// 契约断言：出口传播——traceparent 优先注入（trace-id 继承当前 ctx），X-Trace-Id 兜底同置。
func TestTraceparentInjection(t *testing.T) {
	var gotTP, gotXT string
	down := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		gotTP = r.Header.Get("traceparent")
		gotXT = r.Header.Get("X-Trace-Id")
		_, _ = w.Write([]byte(`{"code":0,"message":"成功","data":{"n":1},"traceId":"x"}`))
	}))
	defer down.Close()

	ctx := logx.WithTraceID(context.Background(), "0af7651916cd43dd8448eb211c80319c")
	c := httpx.New(2 * time.Second)
	data, be := httpx.Get[map[string]int](c, ctx, down.URL, nil)
	if be != nil {
		t.Fatalf("biz error: %v", be)
	}
	if data["n"] != 1 {
		t.Fatalf("data = %v", data)
	}
	if len(gotTP) != len("00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01") {
		t.Fatalf("traceparent = %q", gotTP)
	}
	if gotTP[3:35] != "0af7651916cd43dd8448eb211c80319c" {
		t.Fatalf("trace-id 段未继承 ctx: %q", gotTP)
	}
	if gotXT != "0af7651916cd43dd8448eb211c80319c" {
		t.Fatalf("X-Trace-Id = %q", gotXT)
	}
}

// 契约断言：下游业务失败（code≠0）透传下游 code/message。
func TestDownstreamBizError(t *testing.T) {
	down := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(404)
		_, _ = w.Write([]byte(`{"code":1004,"message":"资源不存在：order o-1","data":null,"traceId":"t"}`))
	}))
	defer down.Close()

	_, be := httpx.Get[struct{}](httpx.New(time.Second), context.Background(), down.URL, nil)
	if be == nil || be.Code != 1004 || be.Message != "资源不存在：order o-1" {
		t.Fatalf("biz error = %+v", be)
	}
}

// 契约断言：超时 → 1008；连接失败/非信封 → 1009。
func TestTimeoutAndUnavailable(t *testing.T) {
	// 超时 → 1008
	down := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		time.Sleep(300 * time.Millisecond)
		_, _ = w.Write([]byte(`{}`))
	}))
	defer down.Close()
	_, be := httpx.Get[struct{}](httpx.New(50*time.Millisecond), context.Background(), down.URL, nil)
	if be == nil || be.Code != errcode.UpstreamTimeout {
		t.Fatalf("timeout = %+v", be)
	}

	// 连接拒绝 → 1009
	_, be = httpx.Get[struct{}](httpx.New(time.Second), context.Background(), "http://127.0.0.1:1/nope", nil)
	if be == nil || be.Code != errcode.Unavailable {
		t.Fatalf("conn refused = %+v", be)
	}

	// 非信封响应 → 1009
	down2 := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		_, _ = w.Write([]byte(`<html>bad gateway</html>`))
	}))
	defer down2.Close()
	_, be = httpx.Get[struct{}](httpx.New(time.Second), context.Background(), down2.URL, nil)
	if be == nil || be.Code != errcode.Unavailable {
		t.Fatalf("non-envelope = %+v", be)
	}
}

// 超时强制：New 截断 > MaxTimeout。
func TestTimeoutForced(t *testing.T) {
	if c := httpx.New(0); c == nil {
		t.Fatal("default timeout client")
	}
	// >30s 截断为 30s（不 panic、不透传长超时）
	down := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		time.Sleep(80 * time.Millisecond)
		_, _ = w.Write([]byte(`{"code":0,"message":"成功","data":null,"traceId":"t"}`))
	}))
	defer down.Close()
	_, be := httpx.Get[struct{}](httpx.New(31*time.Second), context.Background(), down.URL, nil)
	if be != nil {
		t.Fatalf("31s should clamp to 30s not fail: %v", be)
	}
}

// 成功无负载：data null → 零值返回。
func TestNullData(t *testing.T) {
	down := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		_, _ = w.Write([]byte(`{"code":0,"message":"成功","data":null,"traceId":"t"}`))
	}))
	defer down.Close()
	v, be := httpx.Get[map[string]int](httpx.New(time.Second), context.Background(), down.URL, nil)
	if be != nil || v != nil {
		t.Fatalf("null data = %v, %v", v, be)
	}
}
