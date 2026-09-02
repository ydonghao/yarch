package main_test

import (
	"strings"
	"context"
	"errors"
	"testing"

	"github.com/cloudwego/hertz/pkg/app/server"
	"github.com/cloudwego/hertz/pkg/common/ut"
	"log/slog"

	"github.com/yuandonghao/yarch/stacks/golang/response"
	"github.com/yuandonghao/yarch/stacks/golang/testx"
	"github.com/yuandonghao/yarch/stacks/golang/web"

	"{{.Module}}/api/router"
	"{{.Module}}/application"
	"{{.Module}}/domain/entity"
)

// stubUsers 内存仓储（依赖倒置演示：领域接口 ← 任意实现，测试不碰 DB）。
type stubUsers struct{ items []*entity.User }

func (s *stubUsers) Create(ctx context.Context, u *entity.User) error {
	s.items = append(s.items, u)
	return nil
}

func (s *stubUsers) FindByID(ctx context.Context, id string) (*entity.User, error) {
	for _, u := range s.items {
		if u.ID == id {
			return u, nil
		}
	}
	return nil, errors.New("not found")
}

func (s *stubUsers) Page(ctx context.Context, q response.PageQuery) (*response.PageData[entity.User], error) {
	return response.NewPageData[entity.User](nil, int64(len(s.items)), q.Page, q.PageSize), nil
}

// 模板冒烟：全链路（中间件链 + 信封 + 201 + traceId 恒等 + D6 空页 + 领域业务码 3001）。
func TestSmoke(t *testing.T) {
	app := &application.App{Users: &stubUsers{}}
	h := server.Default(server.WithHostPorts(":0"))
	web.Setup(h, slog.New(slog.DiscardHandler), web.Options{})
	router.Register(h, app)

	// ① 领域不变式：空名称 → 3001（业务码注册演示）
	w := ut.PerformRequest(h.Engine, "POST", "/api/v1/users", &ut.Body{Body: strings.NewReader(`{"name":""}`), Len: 11})
	m := testx.Envelope(t, w.Body.String())
	if m["code"].(float64) != 3001 || w.Code != 400 {
		t.Fatalf("3001 = code %v http %d", m["code"], w.Code)
	}

	// ② 创建成功：201 + code=0 + 信封 traceId == X-Trace-Id
	w = ut.PerformRequest(h.Engine, "POST", "/api/v1/users", &ut.Body{Body: strings.NewReader(`{"name":"alice"}`), Len: 16}, ut.Header{Key: "traceparent", Value: "00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01"})
	if w.Code != 201 {
		t.Fatalf("create http = %d", w.Code)
	}
	testx.EnvelopeTrace(t, w.Body.String(), "0af7651916cd43dd8448eb211c80319c")

	// ③ 分页：D6 越界 → 空 list + 真实 total（stub 回边界事实）
	w = ut.PerformRequest(h.Engine, "GET", "/api/v1/users?page=9&pageSize=20", nil)
	list, total := testx.PageData(t, w.Body.String())
	if len(list) != 0 || total != 1 {
		t.Fatalf("D6: list %d total %v", len(list), total)
	}

	// ④ 分页参数非法 → 1001
	w = ut.PerformRequest(h.Engine, "GET", "/api/v1/users?pageSize=101", nil)
	m = testx.Envelope(t, w.Body.String())
	if m["code"].(float64) != 1001 {
		t.Fatalf("1001 = %v", m["code"])
	}

}
