package auth_test

import (
	"context"
	"encoding/json"
	"log/slog"
	"testing"
	"time"

	"github.com/cloudwego/hertz/pkg/app"
	"github.com/cloudwego/hertz/pkg/app/server"
	"github.com/cloudwego/hertz/pkg/common/ut"

	"github.com/ydonghao/yarch/stacks/golang/auth"
	"github.com/ydonghao/yarch/stacks/golang/middleware"
)

func signer(t *testing.T) *auth.Signer {
	t.Helper()
	s, err := auth.NewHS256([]byte("0123456789abcdef0123456789abcdef"))
	if err != nil {
		t.Fatal(err)
	}
	return s
}

// 契约断言：Bearer JWT 签发/解析闭环 + 载荷（sub 不透明 string + roles）。
func TestSignParse(t *testing.T) {
	s := signer(t)
	tok, err := s.Sign("u-42", []string{"admin"}, time.Hour)
	if err != nil {
		t.Fatal(err)
	}
	claims, err := s.Parse(tok)
	if err != nil {
		t.Fatal(err)
	}
	if claims.UserID != "u-42" || len(claims.Roles) != 1 || claims.Roles[0] != "admin" {
		t.Fatalf("claims = %+v", claims)
	}
}

func TestShortSecretRejected(t *testing.T) {
	if _, err := auth.NewHS256([]byte("short")); err == nil {
		t.Fatal("短密钥必须拒绝")
	}
}

// 契约断言（2xxx 映射）：过期 → 2002/401；篡改/坏格式 → 2001/401。
func TestParseErrors(t *testing.T) {
	s := signer(t)

	tok, _ := s.Sign("u", nil, -time.Minute) // 已过期
	if _, err := s.Parse(tok); err != auth.ErrExpired {
		t.Fatalf("expired = %v", err)
	}

	tok2, _ := s.Sign("u", nil, time.Hour)
	if _, err := s.Parse(tok2 + "x"); err != auth.ErrInvalid {
		t.Fatalf("tampered = %v", err)
	}
	if _, err := s.Parse("garbage"); err != auth.ErrInvalid {
		t.Fatalf("garbage = %v", err)
	}

	// 非本 Signer 密钥签的 token → 2001
	other, _ := auth.NewHS256([]byte("ffffffffffffffffffffffffffffffff"))
	tok3, _ := other.Sign("u", nil, time.Hour)
	if _, err := s.Parse(tok3); err != auth.ErrInvalid {
		t.Fatalf("wrong key = %v", err)
	}
}

// 契约断言：认证/鉴权中间件矩阵（2001 无凭证 / 2002 过期 / 2003 权限不足 / 通过）。
func TestMiddleware(t *testing.T) {
	s := signer(t)
	h := server.Default(server.WithHostPorts(":0"))
	h.Use(middleware.Trace(), middleware.Recovery(slog.New(slog.DiscardHandler)))
	admin := h.Group("/api/v1/admin", auth.RequireAuth(s), auth.RequireRoles("admin"))
	admin.GET("/ping", func(ctx context.Context, c *app.RequestContext) {
		v, _ := c.Get(auth.ClaimsKey)
		cl := v.(*auth.Claims)
		c.String(200, cl.UserID)
	})

	code := func(t *testing.T, w *ut.ResponseRecorder) int {
		t.Helper()
		var e struct {
			Code int `json:"code"`
		}
		_ = json.Unmarshal(w.Body.Bytes(), &e)
		return e.Code
	}

	// 无凭证 → 2001/401
	w := ut.PerformRequest(h.Engine, "GET", "/api/v1/admin/ping", nil)
	if w.Code != 401 || code(t, w) != 2001 {
		t.Fatalf("no cred = %d/%d", w.Code, code(t, w))
	}

	// 过期 → 2002/401
	expired, _ := s.Sign("u", []string{"admin"}, -time.Minute)
	w = ut.PerformRequest(h.Engine, "GET", "/api/v1/admin/ping", nil, ut.Header{Key: "Authorization", Value: "Bearer " + expired})
	if w.Code != 401 || code(t, w) != 2002 {
		t.Fatalf("expired = %d/%d", w.Code, code(t, w))
	}

	// 有效凭证但角色不足 → 2003/403
	tok, _ := s.Sign("u", []string{"viewer"}, time.Hour)
	w = ut.PerformRequest(h.Engine, "GET", "/api/v1/admin/ping", nil, ut.Header{Key: "Authorization", Value: "Bearer " + tok})
	if w.Code != 403 || code(t, w) != 2003 {
		t.Fatalf("forbidden = %d/%d", w.Code, code(t, w))
	}

	// admin 角色 → 通过，claims 注入
	tok2, _ := s.Sign("u-9", []string{"admin"}, time.Hour)
	w = ut.PerformRequest(h.Engine, "GET", "/api/v1/admin/ping", nil, ut.Header{Key: "Authorization", Value: "Bearer " + tok2})
	if w.Code != 200 || w.Body.String() != "u-9" {
		t.Fatalf("ok = %d %s", w.Code, w.Body)
	}
}
