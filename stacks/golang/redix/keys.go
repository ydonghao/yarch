// Package redix 实现契约 Redis 规约要点（contract/infra/redis.md）：
// key 前缀即租户边界（首段=服务名强制）、JSON 序列化值（禁非文本序列化）、
// 分布式锁、幂等存储（SET NX PX，TTL ≥ 24h）。
package redix

import (
	"fmt"
	"strings"
)

// Keys 服务级 key 前缀管理：首段恒为服务名（registry.md 登记的租户边界标识）。
type Keys struct{ service string }

// NewKeys 构造。service 必须符合 registry.md 一-1（^[a-z][a-z0-9-]{1,31}$，禁裸通用词）。
func NewKeys(service string) (*Keys, error) {
	if service == "" {
		return nil, fmt.Errorf("redix: service name required")
	}
	if len(service) < 2 || len(service) > 32 {
		return nil, fmt.Errorf("redix: service %q invalid", service)
	}
	c := service[0]
	if c < 'a' || c > 'z' {
		return nil, fmt.Errorf("redix: service %q must start with lowercase letter", service)
	}
	for i := 0; i < len(service); i++ {
		b := service[i]
		if (b >= 'a' && b <= 'z') || (b >= '0' && b <= '9') || b == '-' {
			continue
		}
		return nil, fmt.Errorf("redix: service %q contains invalid byte %q", service, b)
	}
	return &Keys{service: service}, nil
}

// K 拼 key：首段=服务名，后续段以 : 分隔（如 mysvc:user:42）。
func (k *Keys) K(parts ...string) string {
	return k.service + ":" + strings.Join(parts, ":")
}

// Service 返回服务名（首段）。
func (k *Keys) Service() string { return k.service }
