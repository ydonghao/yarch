package captcha

import (
	"context"

	"github.com/cloudwego/hertz/pkg/app"

	"github.com/ydonghao/yarch/stacks/golang/web"
)

// Handler 验证码端点件（contract/api/captcha.md 六）。
// 业务注册时挂限流中间件（五-1：middleware.RateLimit 进程内档 / RateLimitRedis 分布式档）。
type Handler struct{ svc *Service }

// NewHandler 端点件构造。
func NewHandler(svc *Service) *Handler { return &Handler{svc: svc} }

// Issue GET /api/v1/captcha → RestResponse<Challenge{provider,key,imageBase64}>（六-1）。
// scene 可选路由参数（四）；imageBase64 为裸 base64（无 data: 前缀，二-2）；code 不出网。
// 非 image 档场景 = 1001（端点形态与档位不匹配）。
func (h *Handler) Issue(ctx context.Context, c *app.RequestContext) {
	ch, be := h.svc.Challenge(ctx, string(c.Query("scene")))
	if be != nil {
		web.ErrCode(c, be.Code, "")
		return
	}
	web.OK(c, 200, ch)
}

// Verify 内联校验（六-2）：挂在登录等业务 handler 内调用；false → 2005 CAPTCHA_INVALID
// （五-2 三态合一：web.ErrCode(c, errcode.CaptchaInvalid, "")），禁细分 key 状态。
func (h *Handler) Verify(ctx context.Context, scene, key, input string) bool {
	return h.svc.Verify(ctx, scene, key, input)
}
