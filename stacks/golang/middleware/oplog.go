package middleware

import (
	"context"
	"log/slog"
	"time"

	"github.com/cloudwego/hertz/pkg/app"

	"github.com/yuandonghao/yarch-go/logx"
)

// OperationLogRecord 操作日志记录（ndjson 行协议口径 + 存储 SPI）。
type OperationLogRecord struct {
	TraceID     string    `json:"traceId"`
	UserID      string    `json:"userId,omitempty"`
	Method      string    `json:"method"`
	Path        string    `json:"path"`
	Status      int       `json:"status"`
	CostMs      int64     `json:"costMs"`
	OccurredAt  time.Time `json:"occurredAt"`
}

// OperationLogStore 存储 SPI（落 DB/MQ 归业务工程实现）。
type OperationLogStore interface {
	Save(ctx context.Context, rec OperationLogRecord) error
}

// OperationLog 操作日志中间件：unsafe 方法完成后产出记录——
// ndjson 打一行（审计轨迹），并异步投递存储 SPI（存储失败不阻断响应，ERROR 记日志）。
// userId 取自 auth.ClaimsKey（若挂了认证中间件），无则留空。
func OperationLog(store OperationLogStore, log *slog.Logger, userIDKey string) app.HandlerFunc {
	l := logx.Sub(log, "middleware.OperationLog")
	return func(ctx context.Context, c *app.RequestContext) {
		if !isUnsafeMethod(string(c.Method())) {
			c.Next(ctx)
			return
		}
		start := time.Now()
		c.Next(ctx)

		rec := OperationLogRecord{
			TraceID:    c.GetString(TraceIDKey),
			UserID:     c.GetString(userIDKey),
			Method:     string(c.Method()),
			Path:       string(c.Path()),
			Status:     c.Response.StatusCode(),
			CostMs:     time.Since(start).Milliseconds(),
			OccurredAt: start.UTC(),
		}
		l.InfoContext(ctx, "operation",
			slog.String("method", rec.Method),
			slog.String("path", rec.Path),
			slog.Int("status", rec.Status),
			slog.Int64("costMs", rec.CostMs),
		)
		if store != nil {
			go func() {
				if err := store.Save(context.Background(), rec); err != nil {
					l.Error("operation log save failed", slog.String("err", err.Error()))
				}
			}()
		}
	}
}
