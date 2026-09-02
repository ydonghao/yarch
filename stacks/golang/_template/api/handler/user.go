// Package handler HTTP 处理器：只做协议转换（绑定 → 调用应用/领域 → 信封回包），零业务逻辑。
package handler

import (
	"context"

	"github.com/cloudwego/hertz/pkg/app"
	"github.com/google/uuid"

	"github.com/yuandonghao/yarch-go/response"
	"github.com/yuandonghao/yarch-go/web"

	"{{.Module}}/api/model"
	"{{.Module}}/domain/entity"
	"{{.Module}}/domain/repository"
)

// UserHandler 用户资源处理器。
type UserHandler struct{ repo repository.UserRepo }

func NewUserHandler(repo repository.UserRepo) *UserHandler { return &UserHandler{repo: repo} }

// Create POST /api/v1/users → 201 + 新资源。
func (h *UserHandler) Create(ctx context.Context, c *app.RequestContext) {
	var req model.CreateUserRequest
	if be := web.Bind(c, &req); be != nil {
		web.Err(c, be)
		return
	}
	u, err := entity.New(uuid.NewString(), req.Name)
	if err != nil {
		web.Err(c, err)
		return
	}
	if err := h.repo.Create(ctx, u); err != nil {
		web.Err(c, err)
		return
	}
	web.OK(c, 201, toResp(u))
}

// Get GET /api/v1/users/:id → 200；不存在 → 404 + 1004。
func (h *UserHandler) Get(ctx context.Context, c *app.RequestContext) {
	u, err := h.repo.FindByID(ctx, c.Param("id"))
	if err != nil {
		web.Err(c, err)
		return
	}
	web.OK(c, 200, toResp(u))
}

// List GET /api/v1/users?page=&pageSize= → 200 + PageData（D6 越界空页）。
func (h *UserHandler) List(ctx context.Context, c *app.RequestContext) {
	q, be := web.BindPageQuery(c)
	if be != nil {
		web.Err(c, be)
		return
	}
	p, err := h.repo.Page(ctx, q)
	if err != nil {
		web.Err(c, err)
		return
	}
	web.OKPage(c, toPage(p))
}

func toResp(u *entity.User) model.UserResponse {
	return model.UserResponse{ID: u.ID, Name: u.Name, CreatedAt: u.CreatedAt.UTC()}
}

func toPage(p *response.PageData[entity.User]) *response.PageData[model.UserResponse] {
	list := make([]model.UserResponse, 0, len(p.List))
	for i := range p.List {
		list = append(list, toResp(&p.List[i]))
	}
	out := response.NewPageData(list, p.Total, p.Page, p.PageSize)
	out.NextCursor = p.NextCursor
	return out
}
