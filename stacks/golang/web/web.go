// Package web 是 Hertz 工程的装配入口（契约实现件）：
// Setup 一行挂全中间件链（顺序即语义）；Bind/PageQuery/Write 是 handler 三件套。
package web

import (
	"encoding/json"
	"errors"
	"strconv"

	"github.com/cloudwego/hertz/pkg/app"

	"github.com/yuandonghao/yarch/stacks/golang/errcode"
	"github.com/yuandonghao/yarch/stacks/golang/middleware"
	"github.com/yuandonghao/yarch/stacks/golang/response"
	"github.com/yuandonghao/yarch/stacks/golang/xerror"
)

// Bind 统一绑定与校验：请求体 JSON 语法/类型错误 → 1002；字段校验失败 → 1001。
// 返回 *xerror.BizError 时 handler 直接交给 Err(c, e) 写出。
// 说明：Hertz 的 BindAndValidate 将 JSON 解码错误与 vd 校验错误合并为同一错误类型，
// 故先对 body 做一次语法/类型预检以区分 1001/1002（sonic 解码，两次解析成本可忽略）。
func Bind(c *app.RequestContext, obj any) *xerror.BizError {
	if body := c.Request.Body(); len(body) > 0 {
		var probe any
		if err := json.Unmarshal(body, &probe); err != nil {
			return xerror.New(errcode.MalformedBody, err.Error())
		}
		if err := json.Unmarshal(body, obj); err != nil {
			var typ *json.UnmarshalTypeError
			if errors.As(err, &typ) {
				return xerror.New(errcode.MalformedBody, err.Error())
			}
		}
	}
	if err := c.BindAndValidate(obj); err != nil {
		return xerror.New(errcode.InvalidArgument, err.Error())
	}
	return nil
}

// BindPageQuery 从 query 绑定分页参数并校验（rest-conventions.md：1-based；
// pageSize 默认 20 上限 100；page 非正整数或 pageSize 超上限 → 1001）。
func BindPageQuery(c *app.RequestContext) (response.PageQuery, *xerror.BizError) {
	q := response.PageQuery{Page: response.DefaultPage, PageSize: response.DefaultPageSize}

	if raw := string(c.Query("page")); raw != "" {
		v, err := strconv.ParseInt(raw, 10, 32)
		if err != nil || v < 1 {
			return q, xerror.Newf(errcode.InvalidArgument, "page 非正整数：%s", raw)
		}
		q.Page = int32(v)
	}
	if raw := string(c.Query("pageSize")); raw != "" {
		v, err := strconv.ParseInt(raw, 10, 32)
		if err != nil || v < 1 {
			return q, xerror.Newf(errcode.InvalidArgument, "pageSize 非正整数：%s", raw)
		}
		if v > 100 {
			return q, xerror.Newf(errcode.InvalidArgument, "pageSize 必须 ≤ 100")
		}
		q.PageSize = int32(v)
	}
	return q, nil
}

func traceID(c *app.RequestContext) string { return c.GetString(middleware.TraceIDKey) }

// Write 以信封写出（status 由调用方按方法语义给：GET 200 / POST 201 …）。
func Write[T any](c *app.RequestContext, status int, r *response.Response[T]) {
	c.Data(status, "application/json; charset=utf-8", response.JSON(r))
}

// OK 成功信封（有负载）。
func OK[T any](c *app.RequestContext, status int, data T) {
	Write(c, status, response.Ok(data, traceID(c)))
}

// OKNil 成功信封（无负载，data=null）。
func OKNil(c *app.RequestContext, status int) {
	Write(c, status, response.OkNil[struct{}](traceID(c)))
}

// OKPage 成功信封（分页负载）。
func OKPage[T any](c *app.RequestContext, p *response.PageData[T]) {
	OK(c, 200, p)
}

// Err 业务失败信封（HTTP 映射由码表固定；非 BizError 归一为 1000）。
func Err(c *app.RequestContext, err error) {
	be := xerror.FromError(err)
	Write(c, be.Code.HTTP(), response.Fail[struct{}](be.Code, be.Message, traceID(c)))
}

// ErrCode 指定码失败信封。
func ErrCode(c *app.RequestContext, code errcode.Code, detail string) {
	middleware.WriteError(c, code, detail)
}
