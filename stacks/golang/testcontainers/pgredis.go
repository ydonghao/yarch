package testcontainers

import (
	"context"
	"testing"
	"time"

	"github.com/testcontainers/testcontainers-go"
	"github.com/testcontainers/testcontainers-go/modules/postgres"
	"github.com/testcontainers/testcontainers-go/modules/redis"
	"github.com/testcontainers/testcontainers-go/wait"
)

// StartPG 启动一次性 PG 容器并执行迁移就绪，返回 DSN（容器随 t 清理）。
func StartPG(t *testing.T) string {
	t.Helper()
	ctx := context.Background()
	pgc, err := postgres.Run(ctx, "postgres:17-alpine",
		postgres.WithDatabase("yarchtest"),
		postgres.WithUsername("yarch"),
		postgres.WithPassword("yarch"),
		testcontainers.WithWaitStrategy(
			wait.ForLog("database system is ready to accept connections").
				WithOccurrence(2).WithStartupTimeout(60*time.Second)),
	)
	if err != nil {
		t.Skipf("testx: PG 容器不可用（需要 Docker）: %v", err)
	}
	t.Cleanup(func() { _ = pgc.Terminate(ctx) })
	dsn, err := pgc.ConnectionString(ctx, "sslmode=disable")
	if err != nil {
		t.Fatalf("testx: PG DSN: %v", err)
	}
	return dsn
}

// StartRedis 启动一次性 Redis 容器，返回 addr（host:port）。
func StartRedis(t *testing.T) string {
	t.Helper()
	ctx := context.Background()
	rc, err := redis.Run(ctx, "redis:7-alpine")
	if err != nil {
		t.Skipf("testx: Redis 容器不可用（需要 Docker）: %v", err)
	}
	t.Cleanup(func() { _ = rc.Terminate(ctx) })
	addr, err := rc.ConnectionString(ctx)
	if err != nil {
		t.Fatalf("testx: Redis addr: %v", err)
	}
	// ConnectionString 返回 redis://host:port 形态，剥去 scheme
	if len(addr) > 8 && addr[:8] == "redis://" {
		addr = addr[8:]
	}
	return addr
}
