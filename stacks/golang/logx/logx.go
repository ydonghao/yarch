// Package logx 实现契约「日志与追踪」的日志行协议（contract/api/logging-trace.md v1.0）：
// ndjson 一行一条，标准输出，字段 ts/level/service/env/traceId/logger/msg + 自由键值（camelCase）。
// 输出器为 log/slog JSONHandler（契约「各栈实现锚点」点名）。
package logx

import (
	"context"
	"io"
	"log/slog"
	"strings"
	"time"
)

type ctxKey struct{}

// WithTraceID 将 traceId 注入 context（由 middleware.Trace 调用；业务代码不直接使用）。
func WithTraceID(ctx context.Context, traceID string) context.Context {
	if ctx == nil {
		ctx = context.Background()
	}
	return context.WithValue(ctx, ctxKey{}, traceID)
}

// TraceID 从 context 取 traceId；无上下文值时返回空串（后台任务/启动日志可为空，契约条件必填）。
func TraceID(ctx context.Context) string {
	if ctx == nil {
		return ""
	}
	if v, ok := ctx.Value(ctxKey{}).(string); ok {
		return v
	}
	return ""
}

// New 构造 ndjson logger。service/env 为进程级必填字段（来自配置）；
// logger 字段建议每包经 Sub 派生（记录点标识 = Go 包名）。
func New(service, env string, level slog.Level, w io.Writer) *slog.Logger {
	h := &traceHandler{inner: slog.NewJSONHandler(w, &slog.HandlerOptions{
		Level:       level,
		ReplaceAttr: replaceAttr,
	})}
	l := slog.New(h).With(slog.String("service", service), slog.String("env", env))
	return l
}

// Sub 派生子 logger，携带 logger 字段（记录点标识，Go 包名）。
func Sub(l *slog.Logger, logger string) *slog.Logger {
	return l.With(slog.String("logger", logger))
}

// traceHandler：请求上下文内必有 traceId（从 ctx 提取）；logger 必填（未派生时补 main）。
type traceHandler struct {
	inner     slog.Handler
	hasLogger bool
}

func (h *traceHandler) Enabled(ctx context.Context, l slog.Level) bool { return h.inner.Enabled(ctx, l) }

func (h *traceHandler) Handle(ctx context.Context, r slog.Record) error {
	if tid := TraceID(ctx); tid != "" {
		r.AddAttrs(slog.String("traceId", tid))
	}
	if !h.hasLogger && !recordHasLogger(&r) {
		r.AddAttrs(slog.String("logger", "main"))
	}
	return h.inner.Handle(ctx, r)
}

func recordHasLogger(r *slog.Record) bool {
	found := false
	r.Attrs(func(a slog.Attr) bool {
		if a.Key == "logger" {
			found = true
			return false
		}
		return true
	})
	return found
}

func (h *traceHandler) WithAttrs(attrs []slog.Attr) slog.Handler {
	has := h.hasLogger
	for _, a := range attrs {
		if a.Key == "logger" {
			has = true
		}
	}
	return &traceHandler{inner: h.inner.WithAttrs(attrs), hasLogger: has}
}

func (h *traceHandler) WithGroup(name string) slog.Handler {
	return &traceHandler{inner: h.inner.WithGroup(name), hasLogger: h.hasLogger}
}

// replaceAttr：time→ts（RFC3339 毫秒 UTC，恒以 Z 结尾）、level 大写（TRACE/DEBUG/INFO/WARN/ERROR）。
func replaceAttr(groups []string, a slog.Attr) slog.Attr {
	if len(groups) > 0 {
		return a
	}
	switch a.Key {
	case slog.TimeKey:
		if t, ok := a.Value.Any().(time.Time); ok {
			return slog.String("ts", t.UTC().Format("2006-01-02T15:04:05.000Z"))
		}
	case slog.LevelKey:
		if lv, ok := a.Value.Any().(slog.Level); ok {
			return slog.String("level", levelName(lv))
		}
	}
	return a
}

func levelName(l slog.Level) string {
	switch {
	case l < slog.LevelDebug:
		return "TRACE"
	case l < slog.LevelInfo:
		return "DEBUG"
	case l < slog.LevelWarn:
		return "INFO"
	case l < slog.LevelError:
		return "WARN"
	default:
		return "ERROR"
	}
}

// ParseLevel 解析日志级别字符串（大小写不敏感；TRACE 映射到 slog DEBUG 以下一级）。
func ParseLevel(s string) slog.Level {
	switch strings.ToUpper(strings.TrimSpace(s)) {
	case "TRACE":
		return slog.Level(-8)
	case "DEBUG":
		return slog.LevelDebug
	case "INFO", "":
		return slog.LevelInfo
	case "WARN", "WARNING":
		return slog.LevelWarn
	case "ERROR":
		return slog.LevelError
	default:
		return slog.LevelInfo
	}
}
