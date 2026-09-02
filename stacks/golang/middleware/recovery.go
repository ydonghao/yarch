package middleware

import (
	"context"
	"fmt"
	"log/slog"
	"runtime/debug"

	"github.com/cloudwego/hertz/pkg/app"

	"github.com/yuandonghao/yarch/stacks/golang/errcode"
	"github.com/yuandonghao/yarch/stacks/golang/logx"
)

// Recovery panic 兜底：未预期失败 → 500 + code=1000（message 固定「内部错误」，细节只进日志）。
// 堆栈折叠为 stack 单字段（契约日志禁止多行堆栈直接打印）。
func Recovery(log *slog.Logger) app.HandlerFunc {
	l := logx.Sub(log, "middleware.Recovery")
	return func(ctx context.Context, c *app.RequestContext) {
		defer func() {
			if r := recover(); r != nil {
				l.ErrorContext(ctx, "panic recovered",
					slog.String("panic", toString(r)),
					slog.String("stack", string(debug.Stack())),
					slog.String("method", string(c.Method())),
					slog.String("path", string(c.Path())),
				)
				WriteError(c, errcode.InternalError, "")
			}
		}()
		c.Next(ctx)
	}
}

func toString(v any) string {
	switch x := v.(type) {
	case string:
		return x
	case error:
		return x.Error()
	default:
		return fmt.Sprintf("%v", x)
	}
}
