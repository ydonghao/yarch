package web_test

import (
	"context"
	"strings"
	"encoding/json"
	"log/slog"
	"testing"

	"github.com/cloudwego/hertz/pkg/app"
	"github.com/cloudwego/hertz/pkg/app/server"
	"github.com/cloudwego/hertz/pkg/common/ut"

	"github.com/yuandonghao/yarch-go/errcode"
	"github.com/yuandonghao/yarch-go/middleware"
	"github.com/yuandonghao/yarch-go/web"
)

func newEngine(t *testing.T) *server.Hertz {
	t.Helper()
	h := server.Default(server.WithHostPorts(":0"))
	web.Setup(h, slog.New(slog.DiscardHandler), web.Options{})
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
		t.Fatalf("not envelope: %v (%s)", err, body)
	}
	return e
}

// 契约断言：POST 创建 201 + code=0；响应体 traceId 恒等于响应头 X-Trace-Id。
func TestCreate201(t *testing.T) {
	h := newEngine(t)
	h.POST("/api/v1/users", func(ctx context.Context, c *app.RequestContext) {
		var req struct {
			Name string `json:"name"`
		}
		if be := web.Bind(c, &req); be != nil {
			web.Err(c, be)
			return
		}
		web.OK(c, 201, map[string]string{"name": req.Name})
	})
	w := ut.PerformRequest(h.Engine, "POST", "/api/v1/users", &ut.Body{Body: strings.NewReader(`{"name":"a"}`), Len: 12})
	if w.Code != 201 {
		t.Fatalf("http = %d body=%s", w.Code, w.Body)
	}
	e := decode(t, w.Body.String())
	if e.Code != 0 {
		t.Fatalf("code = %d", e.Code)
	}
	if e.TraceID == "" || e.TraceID != w.Header().Get("X-Trace-Id") {
		t.Fatalf("traceId(%q) != X-Trace-Id(%q)", e.TraceID, w.Header().Get("X-Trace-Id"))
	}
}

// 契约断言：请求体 JSON 语法错误 → 400 + 1002；字段校验失败 → 400 + 1001。
func TestBindErrors(t *testing.T) {
	h := newEngine(t)
	h.POST("/api/v1/users", func(ctx context.Context, c *app.RequestContext) {
		var req struct {
			Name string `json:"name" vd:"len($)>0"`
		}
		if be := web.Bind(c, &req); be != nil {
			web.Err(c, be)
			return
		}
		web.OKNil(c, 201)
	})

	// 语法错误 → 1002
	w := ut.PerformRequest(h.Engine, "POST", "/api/v1/users", &ut.Body{Body: strings.NewReader(`{bad json`), Len: 9})
	if w.Code != 400 {
		t.Fatalf("malformed http = %d", w.Code)
	}
	if e := decode(t, w.Body.String()); e.Code != 1002 {
		t.Fatalf("malformed code = %d (%s)", e.Code, w.Body)
	}

	// 校验失败（vd 表达式不过）→ 1001
	w = ut.PerformRequest(h.Engine, "POST", "/api/v1/users", &ut.Body{Body: strings.NewReader(`{"name":""}`), Len: 11})
	if e := decode(t, w.Body.String()); e.Code != 1001 {
		t.Fatalf("invalid code = %d (%s)", e.Code, w.Body)
	}
}

// 契约断言（D6 与分页参数）：page 非正整数 / pageSize 超上限 → 1001；
// 默认 page=1、pageSize=20。
func TestPageQuery(t *testing.T) {
	h := newEngine(t)
	h.GET("/api/v1/users", func(ctx context.Context, c *app.RequestContext) {
		q, be := web.BindPageQuery(c)
		if be != nil {
			web.Err(c, be)
			return
		}
		web.OK(c, 200, map[string]any{"page": q.Page, "pageSize": q.PageSize})
	})

	check := func(query string, wantCode int) {
		w := ut.PerformRequest(h.Engine, "GET", "/api/v1/users"+query, nil)
		e := decode(t, w.Body.String())
		if e.Code != wantCode {
			t.Fatalf("query %q: code = %d want %d (%s)", query, e.Code, wantCode, w.Body)
		}
	}
	check("", 0)          // 默认 1/20
	check("?page=2", 0)   // 合法
	check("?page=0", 1001)
	check("?page=-1", 1001)
	check("?page=abc", 1001)
	check("?pageSize=101", 1001)
	check("?pageSize=100", 0)

	w := ut.PerformRequest(h.Engine, "GET", "/api/v1/users", nil)
	e := decode(t, w.Body.String())
	var d struct {
		Page     int `json:"page"`
		PageSize int `json:"pageSize"`
	}
	_ = json.Unmarshal(e.Data, &d)
	if d.Page != 1 || d.PageSize != 20 {
		t.Fatalf("defaults = %d/%d", d.Page, d.PageSize)
	}
}

// Err 对非 BizError 归一 1000/500；ErrCode 走码表 HTTP 映射。
func TestErrHelpers(t *testing.T) {
	h := newEngine(t)
	h.GET("/api/v1/a", func(ctx context.Context, c *app.RequestContext) {
		web.Err(c, errDecodeFail{})
	})
	h.GET("/api/v1/b", func(ctx context.Context, c *app.RequestContext) {
		web.ErrCode(c, errcode.Unauthorized, "")
	})
	w := ut.PerformRequest(h.Engine, "GET", "/api/v1/a", nil)
	if w.Code != 500 || decode(t, w.Body.String()).Code != 1000 {
		t.Fatalf("unexpected error must be 1000/500, got %d", w.Code)
	}
	w = ut.PerformRequest(h.Engine, "GET", "/api/v1/b", nil)
	if w.Code != 401 || decode(t, w.Body.String()).Code != 2001 {
		t.Fatalf("unauthorized must be 2001/401, got %d", w.Code)
	}
}

type errDecodeFail struct{}

func (errDecodeFail) Error() string { return "boom" }

var _ = middleware.TraceIDKey
