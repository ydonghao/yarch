package middleware

import (
	"context"
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"errors"
	"log/slog"
	"time"

	"github.com/cloudwego/hertz/pkg/app"

	"github.com/yuandonghao/yarch-go/errcode"
	"github.com/yuandonghao/yarch-go/logx"
)

// 幂等存储错误哨兵。
var (
	// ErrIdempotencyMismatch 同键异参（→1007）。
	ErrIdempotencyMismatch = errors.New("idempotency: digest mismatch")
	// ErrIdempotencyPending 首次执行尚未完成（并发同键竞争，等待回放窗口后仍为 →1007）。
	ErrIdempotencyPending = errors.New("idempotency: pending")
)

// IdempotencyStore 幂等存储接口（rest-conventions.md 幂等总则-1；
// 实现见 redix（Redis SET NX PX），业务工程亦可自行替换）。
type IdempotencyStore interface {
	// Reserve 占位（SET NX 语义）：true=首次获得执行权；false=键已存在。
	Reserve(ctx context.Context, key, digest string, ttl time.Duration) (bool, error)
	// Lookup 取已存响应；digest 不匹配返回 ErrIdempotencyMismatch；
	// 键存在但响应未写入返回 ErrIdempotencyPending。
	Lookup(ctx context.Context, key, digest string) (status int, body []byte, err error)
	// Complete 首次执行完成后写入响应（与占位同 TTL 刷新）。
	Complete(ctx context.Context, key, digest string, status int, body []byte, ttl time.Duration) error
}

// Idempotency 幂等中间件：unsafe 方法可带 Idempotency-Key（客户端 UUID，作用域=单服务单资源类型）。
// 同键同参回放原响应；同键异参 1007；存储故障 fail-open 放行（幂等是增强，不阻断业务）。
func Idempotency(store IdempotencyStore, ttl time.Duration, log *slog.Logger) app.HandlerFunc {
	l := logx.Sub(log, "middleware.Idempotency")
	return func(ctx context.Context, c *app.RequestContext) {
		if !isUnsafeMethod(string(c.Method())) {
			c.Next(ctx)
			return
		}
		key := string(c.GetHeader("Idempotency-Key"))
		if key == "" {
			c.Next(ctx)
			return
		}
		digest := requestDigest(c)

		first, err := store.Reserve(ctx, key, digest, ttl)
		if err != nil {
			// 存储不可用：放行并告警（fail-open），副作用以存储层唯一约束为最终防线
			l.WarnContext(ctx, "idempotency store unavailable", slog.String("err", err.Error()))
			c.Next(ctx)
			return
		}

		if first {
			c.Next(ctx)
			if err := store.Complete(ctx, key, digest, c.Response.StatusCode(), responseBody(c), ttl); err != nil {
				l.WarnContext(ctx, "idempotency complete failed", slog.String("err", err.Error()))
			}
			return
		}

		// 并发同键：竞争失败方等待短暂后回放，仍取不到则 1007
		for attempt := 0; ; attempt++ {
			status, body, err := store.Lookup(ctx, key, digest)
			switch {
			case err == nil:
				c.Header("Idempotency-Replayed", "true")
				c.Data(status, "application/json; charset=utf-8", body)
				c.Abort()
				return
			case errors.Is(err, ErrIdempotencyMismatch):
				WriteError(c, errcode.IdempotencyConflict, "")
				c.Abort()
				return
			case errors.Is(err, ErrIdempotencyPending):
				if attempt >= 2 {
					WriteError(c, errcode.IdempotencyConflict, "")
					c.Abort()
					return
				}
				time.Sleep(50 * time.Millisecond)
			default:
				l.WarnContext(ctx, "idempotency lookup failed", slog.String("err", err.Error()))
				WriteError(c, errcode.IdempotencyConflict, "")
				c.Abort()
				return
			}
		}
	}
}

func isUnsafeMethod(m string) bool {
	switch m {
	case "POST", "PUT", "PATCH", "DELETE":
		return true
	}
	return false
}

// requestDigest 请求摘要：方法 + 路径 + 请求体（同键异参判定依据）。
func requestDigest(c *app.RequestContext) string {
	h := sha256.New()
	h.Write(c.Method())
	h.Write([]byte{'|'})
	h.Write(c.Path())
	h.Write([]byte{'|'})
	h.Write(c.Request.Body())
	return hex.EncodeToString(h.Sum(nil))
}

func responseBody(c *app.RequestContext) []byte {
	return c.Response.Body()
}

// idemRecord 存储记录形状（实现方以 JSON 落 Redis value）。
type idemRecord struct {
	Digest string `json:"digest"`
	Status int    `json:"status"`
	Body   []byte `json:"body"`
}

// MarshalIdemRecord / UnmarshalIdemRecord 供实现方复用的编解码。
func MarshalIdemRecord(digest string, status int, body []byte) []byte {
	b, _ := json.Marshal(idemRecord{Digest: digest, Status: status, Body: body})
	return b
}

func UnmarshalIdemRecord(b []byte) (idemRecord, error) {
	var r idemRecord
	err := json.Unmarshal(b, &r)
	return r, err
}
