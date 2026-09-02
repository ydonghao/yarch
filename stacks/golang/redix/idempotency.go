package redix

import (
	"context"
	"errors"
	"time"

	"github.com/redis/go-redis/v9"

	"github.com/yuandonghao/yarch-go/middleware"
)

// Idempotency 幂等存储的 Redis 实现（rest-conventions.md 幂等总则-1：
// Redis SET NX PX，TTL ≥ 24h）。key 必须经 Keys.K 生成。
type Idempotency struct {
	rdb redis.UniversalClient
}

// NewIdempotency 构造幂等存储。
func NewIdempotency(rdb redis.UniversalClient) *Idempotency { return &Idempotency{rdb: rdb} }

// Reserve SET NX 占位：value=请求摘要。
func (i *Idempotency) Reserve(ctx context.Context, key, digest string, ttl time.Duration) (bool, error) {
	return i.rdb.SetNX(ctx, key, middleware.MarshalIdemRecord(digest, 0, nil), ttl).Result()
}

// Lookup 取已存响应；摘要不匹配 / 占位未完成 → 契约哨兵错误。
func (i *Idempotency) Lookup(ctx context.Context, key, digest string) (int, []byte, error) {
	b, err := i.rdb.Get(ctx, key).Bytes()
	if err != nil {
		if errors.Is(err, redis.Nil) {
			return 0, nil, middleware.ErrIdempotencyPending
		}
		return 0, nil, err
	}
	rec, err := middleware.UnmarshalIdemRecord(b)
	if err != nil {
		return 0, nil, err
	}
	if rec.Digest != digest {
		return 0, nil, middleware.ErrIdempotencyMismatch
	}
	if rec.Status == 0 {
		return 0, nil, middleware.ErrIdempotencyPending
	}
	return rec.Status, rec.Body, nil
}

// Complete 写入响应（覆盖占位，同 TTL 刷新）。
func (i *Idempotency) Complete(ctx context.Context, key, digest string, status int, body []byte, ttl time.Duration) error {
	return i.rdb.Set(ctx, key, middleware.MarshalIdemRecord(digest, status, body), ttl).Err()
}
