package redix

import (
	"context"
	"crypto/rand"
	"encoding/hex"
	"time"

	"github.com/redis/go-redis/v9"
)

var releaseLua = redis.NewScript(`
if redis.call("GET", KEYS[1]) == ARGV[1] then
	return redis.call("DEL", KEYS[1])
end
return 0`)

// Lock 分布式锁（SET NX PX + 持有者 token 校验释放，防误解锁）。
// 锁的三防御口径与超时基线见 redis.md；锁 key 必须经 Keys.K 生成（首段=服务名）。
type Lock struct {
	rdb redis.UniversalClient
}

// NewLock 构造分布式锁。
func NewLock(rdb redis.UniversalClient) *Lock { return &Lock{rdb: rdb} }

// Acquire 尝试获取锁：成功返回持有者 token（释放时校验）；失败返回空串。
func (l *Lock) Acquire(ctx context.Context, key string, ttl time.Duration) (string, error) {
	var b [16]byte
	if _, err := rand.Read(b[:]); err != nil {
		return "", err
	}
	token := hex.EncodeToString(b[:])
	ok, err := l.rdb.SetNX(ctx, key, token, ttl).Result()
	if err != nil {
		return "", err
	}
	if !ok {
		return "", nil
	}
	return token, nil
}

// Release 释放锁（仅持有者 token 匹配时删除；锁已过期被他人持有时不误删）。
func (l *Lock) Release(ctx context.Context, key, token string) error {
	return releaseLua.Run(ctx, l.rdb, []string{key}, token).Err()
}
