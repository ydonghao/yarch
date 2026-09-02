package captcha

import (
	"context"
	"time"

	"github.com/cloudwego/hertz/pkg/app"

	"github.com/yuandonghao/yarch/stacks/golang/web"
)

// keyFn 业务侧提供 key 生成器（redix.Keys.K("captcha", id)——首段=服务名纪律）。
type keyFn func(id string) string

// Handler 验证码端点件。
type Handler struct {
	store Store
	key   keyFn
	ttl   time.Duration
}

// NewHandler 构造（ttl ≤ 0 用默认 5 分钟）。
func NewHandler(store Store, key keyFn, ttl time.Duration) *Handler {
	if ttl <= 0 {
		ttl = DefaultTTL
	}
	return &Handler{store: store, key: key, ttl: ttl}
}

// Issue GET /api/v1/captcha → 200 + {id, image(dataURL)}。
// image 为 data:image/png;base64（前端 <img src> 直用）；code 不出网。
func (h *Handler) Issue(ctx context.Context, c *app.RequestContext) {
	id := NewID()
	code := NewCode(DefaultLen)
	if err := h.store.Issue(ctx, h.key(id), code, h.ttl); err != nil {
		web.ErrCode(c, 1009, "")
		return
	}
	web.OK(c, 200, map[string]string{
		"id":    id,
		"image": ImageDataURL(RenderPNG(code)),
	})
}

// Verify 校验并一次性消费（挂在登录等业务 handler 内调用；失败返回 false 由业务给 1001 细节）。
func (h *Handler) Verify(ctx context.Context, id, input string) bool {
	return Verify(ctx, h.store, h.key(id), input)
}
