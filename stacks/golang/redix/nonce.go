package redix

import (
	"context"
	"time"

	"github.com/redis/go-redis/v9"
)

// NonceStore Redis nonce 一次性消费存储（SET NX PX；实现 middleware.NonceStore，
// 跨实例签名防重放档，对偶 java RedisNonceStore）。key 形如
// {service}:signedapi:nonce:{sha256(appKey:nonce)}，由 middleware.SignedApi 生成。
type NonceStore struct{ rdb redis.UniversalClient }

func NewNonceStore(rdb redis.UniversalClient) *NonceStore { return &NonceStore{rdb: rdb} }

// Consume true=首次消费；false=已消费（重放）。存储故障 fail-closed：
// 签名是认证语义（非增强），存储不可用时拒绝放行（→2001），与限流 fail-open 口径相反。
func (s *NonceStore) Consume(ctx context.Context, key string, ttl time.Duration) bool {
	ok, err := s.rdb.SetNX(ctx, key, 1, ttl).Result()
	if err != nil {
		return false
	}
	return ok
}
