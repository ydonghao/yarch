// Package middleware 实现 Hertz 中间件链（契约术语：middleware.Trace()）。
// 顺序即语义：Trace 最先（入口保证 traceId），Recovery 紧随，然后 AccessLog → CORS → 幂等 → 限流。
package middleware

import (
	"context"
	"crypto/rand"
	"encoding/hex"
	"fmt"
	"time"

	"github.com/cloudwego/hertz/pkg/app"

	"github.com/yuandonghao/yarch-go/logx"
)

const (
	// TraceIDKey RequestContext 存储键（handler 内 c.GetString(middleware.TraceIDKey) 可取）。
	TraceIDKey = "yarch.traceId"
)

// Trace 入口中间件（logging-trace.md traceId 贯穿-1）：
// 优先解析请求头 traceparent（W3C，取 trace-id 段）；无则看 X-Trace-Id；再无则生成 32 位小写 hex。
// 响应头 X-Trace-Id 恒回显；全链路不改变、不重新生成。
func Trace() app.HandlerFunc {
	return func(ctx context.Context, c *app.RequestContext) {
		tid := extractTraceID(c)
		ctx = logx.WithTraceID(ctx, tid)
		c.Set(TraceIDKey, tid)
		c.Header("X-Trace-Id", tid)
		c.Next(ctx)
	}
}

func extractTraceID(c *app.RequestContext) string {
	// W3C traceparent: 00-{32hex trace-id}-{16hex span-id}-{2hex flag}
	if tp := c.GetHeader("traceparent"); len(tp) > 0 {
		parts := splitASCII(string(tp), '-')
		if len(parts) == 4 && isLowerHex32(parts[1]) {
			return parts[1]
		}
	}
	if x := c.GetHeader("X-Trace-Id"); len(x) > 0 {
		return string(x)
	}
	return newTraceID()
}

// newTraceID 生成 32 位小写 hex（128-bit 随机数，契约「生成」规则）。
func newTraceID() string {
	var b [16]byte
	if _, err := rand.Read(b[:]); err != nil {
		// crypto/rand 失败不可恢复；以时间熵兜底，绝不因 traceId 生成失败阻断请求
		return fmt.Sprintf("%032x", time.Now().UnixNano())
	}
	return hex.EncodeToString(b[:])
}

func isLowerHex32(s string) bool {
	if len(s) != 32 {
		return false
	}
	for i := 0; i < len(s); i++ {
		c := s[i]
		if (c < '0' || c > '9') && (c < 'a' || c > 'f') {
			return false
		}
	}
	return true
}

func splitASCII(s string, sep byte) []string {
	var out []string
	start := 0
	for i := 0; i < len(s); i++ {
		if s[i] == sep {
			out = append(out, s[start:i])
			start = i + 1
		}
	}
	return append(out, s[start:])
}
