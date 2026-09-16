package middleware

import (
	"context"
	"crypto/hmac"
	"crypto/sha256"
	"crypto/subtle"
	"fmt"
	"sync"
	"time"

	"github.com/cloudwego/hertz/pkg/app"

	"github.com/ydonghao/yarch/stacks/golang/errcode"
)

// NonceStore 签名防重放的 nonce 一次性消费存储（实现见 redix.NonceStore；
// 单实例档用 NewInMemoryNonceStore，对偶 java NonceStore SPI）。
type NonceStore interface {
	// Consume 一次性消费（SET NX 语义）：true=首次；false=已消费（重放）。
	Consume(ctx context.Context, key string, ttl time.Duration) bool
}

// clockSkew 签名时间窗 ±300s；nonceTTL 覆盖整个时间窗（窗口外重放已被时间戳拦截）。
const (
	signedapiClockSkew = 300 * time.Second
	signedapiNonceTTL  = 2 * signedapiClockSkew
)

// SignedApi 接口签名中间件（E4 反扒基线，对偶 java @SignedApi + SignatureInterceptor）：
// HMAC-SHA256(secret, method+"\n"+path+"\n"+timestamp+"\n"+nonce+"\n"+body)，
// 头部 X-App-Key / X-Timestamp(毫秒) / X-Nonce / X-Sign（hex 小写）；
// 时间窗 ±300s + nonce 一次性消费双防线防重放。失败 → 401+2001。
// 应用到需保护的路由组（hertz 无注解路由，作用域=装配处显式决定）。
func SignedApi(appSecrets map[string]string, service string, store NonceStore) app.HandlerFunc {
	if err := validateService(service); err != nil {
		// 装配期错误：服务名不合法即拒绝启动，fail-fast
		panic(err)
	}
	return func(ctx context.Context, c *app.RequestContext) {
		appKey := string(c.GetHeader("X-App-Key"))
		timestamp := string(c.GetHeader("X-Timestamp"))
		nonce := string(c.GetHeader("X-Nonce"))
		sign := string(c.GetHeader("X-Sign"))
		if appKey == "" || timestamp == "" || nonce == "" || sign == "" {
			WriteError(c, errcode.Unauthorized, "missing signature headers")
			c.Abort()
			return
		}
		secret, ok := appSecrets[appKey]
		if !ok {
			WriteError(c, errcode.Unauthorized, "unknown app key")
			c.Abort()
			return
		}
		var ts int64
		if _, err := fmt.Sscanf(timestamp, "%d", &ts); err != nil {
			WriteError(c, errcode.Unauthorized, "bad timestamp")
			c.Abort()
			return
		}
		skew := time.Since(time.UnixMilli(ts))
		if skew > signedapiClockSkew || skew < -signedapiClockSkew {
			WriteError(c, errcode.Unauthorized, "timestamp expired")
			c.Abort()
			return
		}
		material := string(c.Method()) + "\n" + string(c.Path()) + "\n" + timestamp + "\n" + nonce + "\n" + string(c.Request.Body())
		expected := HMACSHA256Hex(secret, material)
		if subtle.ConstantTimeCompare([]byte(expected), []byte(sign)) != 1 {
			WriteError(c, errcode.Unauthorized, "signature mismatch")
			c.Abort()
			return
		}
		// 签名校验通过后消费 nonce（一次性）：原样重放 → 已消费 → 2001。
		// key 摘要化：appKey/nonce 均客户端可控，防注入任意字符进 key（redis.md 二-1 口径）
		nonceKey := service + ":signedapi:nonce:" + sha256Hex([]byte(appKey+":"+nonce))
		if !store.Consume(ctx, nonceKey, signedapiNonceTTL) {
			WriteError(c, errcode.Unauthorized, "nonce replayed")
			c.Abort()
			return
		}
		c.Next(ctx)
	}
}

// HMACSHA256Hex 供客户端与测试复用的签名原语（hex 小写，对偶 java SignatureInterceptor.hmacSha256）。
func HMACSHA256Hex(secret, material string) string {
	mac := hmac.New(sha256.New, []byte(secret))
	mac.Write([]byte(material))
	sum := mac.Sum(nil)
	buf := make([]byte, 0, len(sum)*2)
	const hexDigits = "0123456789abcdef"
	for _, b := range sum {
		buf = append(buf, hexDigits[b>>4], hexDigits[b&0x0f])
	}
	return string(buf)
}

// InMemoryNonceStore 进程内 nonce 存储（单实例档）：过期惰性清理；
// 跨实例部署换 redix.NonceStore（对偶限流双档）。key 摘要化已在上游完成，
// 条目数上界 ≈ 时间窗内的请求数（TTL 10min 自然回落）。
type InMemoryNonceStore struct {
	mu sync.Mutex
	m  map[string]time.Time
}

func NewInMemoryNonceStore() *InMemoryNonceStore {
	return &InMemoryNonceStore{m: make(map[string]time.Time)}
}

func (s *InMemoryNonceStore) Consume(_ context.Context, key string, ttl time.Duration) bool {
	s.mu.Lock()
	defer s.mu.Unlock()
	now := time.Now()
	deadline := now.Add(ttl)
	if seen, ok := s.m[key]; ok && seen.After(now) {
		return false
	}
	// 惰性清理：容量护栏，防长尾增长（非精确 LRU，够单实例档）
	if len(s.m) >= 65536 {
		for k, exp := range s.m {
			if !exp.After(now) {
				delete(s.m, k)
			}
		}
	}
	s.m[key] = deadline
	return true
}
