package redix

import (
	"context"
	"encoding/json"
	"time"

	"github.com/redis/go-redis/v9"
)

// Cache JSON 值缓存（契约：value JSON 序列化；big/hot key 口径由业务遵守，数值口径见 redis.md）。
type Cache struct {
	rdb redis.UniversalClient
}

// NewCache 构造 JSON 缓存。
func NewCache(rdb redis.UniversalClient) *Cache { return &Cache{rdb: rdb} }

// SetJSON 写入 JSON 值。
func (c *Cache) SetJSON(ctx context.Context, key string, v any, ttl time.Duration) error {
	b, err := json.Marshal(v)
	if err != nil {
		return err
	}
	return c.rdb.Set(ctx, key, b, ttl).Err()
}

// GetJSON 读取并反序列化；key 不存在返回 redis.Nil。
func (c *Cache) GetJSON(ctx context.Context, key string, dst any) error {
	b, err := c.rdb.Get(ctx, key).Bytes()
	if err != nil {
		return err
	}
	return json.Unmarshal(b, dst)
}

// Del 删除（缓存失效常用）。
func (c *Cache) Del(ctx context.Context, keys ...string) error {
	return c.rdb.Del(ctx, keys...).Err()
}
