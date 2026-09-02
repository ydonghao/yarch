package middleware

import (
	"github.com/cloudwego/hertz/pkg/app"

	"github.com/yuandonghao/yarch/stacks/golang/errcode"
	"github.com/yuandonghao/yarch/stacks/golang/response"
)

// WriteError 以信封写出错误（HTTP 映射由码表固定）。detail 非空时按「默认文案：细节」追加。
func WriteError(c *app.RequestContext, code errcode.Code, detail string) {
	msg := code.Message()
	if detail != "" {
		msg = msg + "：" + detail
	}
	r := response.Fail[struct{}](code, msg, c.GetString(TraceIDKey))
	c.Data(code.HTTP(), "application/json; charset=utf-8", response.JSON(r))
}
