package testcontainers

import (
	"context"
	"embed"
	"testing"
	"time"

	"github.com/redis/go-redis/v9"
	"gorm.io/gorm"

	"github.com/yuandonghao/yarch/stacks/golang/middleware"
	"github.com/yuandonghao/yarch/stacks/golang/persist"
	"github.com/yuandonghao/yarch/stacks/golang/redix"
	"github.com/yuandonghao/yarch/stacks/golang/response"
)

//go:embed testdata/db/migration/*.sql
var migrations embed.FS

// userDO 集成测试领域对象（显式列清单模型）。
type userDO struct {
	persist.Model
	Name string `gorm:"column:name;size:128"`
}

func (userDO) TableName() string { return "users" }

func openPG(t *testing.T) *gorm.DB {
	t.Helper()
	dsn := StartPG(t)
	if err := persist.MigrateUp(dsn, migrations, "testdata/db/migration"); err != nil {
		t.Fatalf("migrate: %v", err)
	}
	db, err := persist.Open(dsn)
	if err != nil {
		t.Fatalf("open: %v", err)
	}
	return db
}

// 契约断言（PG 行为级）：迁移版本化生效、分页下推、D6 越界空页（200 语义由 web 层保证，
// 此处断言数据层边界事实：空 list + 真实 total）、逻辑删除 is_deleted、审计时间戳填充。
func TestPersistPG(t *testing.T) {
	db := openPG(t)
	ctx := context.Background()

	seed := []userDO{{Model: persist.Model{ID: "u1"}, Name: "a"}, {Model: persist.Model{ID: "u2"}, Name: "b"}, {Model: persist.Model{ID: "u3"}, Name: "c"}}
	if err := db.Create(&seed).Error; err != nil {
		t.Fatalf("seed: %v", err)
	}

	// 审计时间戳自动填充
	var got userDO
	if err := db.First(&got, "id = ?", "u1").Error; err != nil {
		t.Fatalf("first: %v", err)
	}
	if got.CreatedAt.IsZero() || got.UpdatedAt.IsZero() {
		t.Fatal("created_at/updated_at 应自动填充")
	}

	// 分页第 1 页
	p1, err := persist.PageOf[userDO](db.Model(&userDO{}), response.PageQuery{Page: 1, PageSize: 2})
	if err != nil {
		t.Fatalf("page1: %v", err)
	}
	if p1.Total != 3 || len(p1.List) != 2 {
		t.Fatalf("page1 = total %d len %d", p1.Total, len(p1.List))
	}

	// D6：越界页返回边界事实（空 list + 真实 total），不纠错不报错
	p9, err := persist.PageOf[userDO](db.Model(&userDO{}), response.PageQuery{Page: 5, PageSize: 2})
	if err != nil {
		t.Fatalf("page9: %v", err)
	}
	if p9.Total != 3 || len(p9.List) != 0 {
		t.Fatalf("D6 越界: total %d len %d", p9.Total, len(p9.List))
	}

	// 逻辑删除：Delete 写 is_deleted，默认查询过滤
	if err := db.Delete(&userDO{}, "id = ?", "u1").Error; err != nil {
		t.Fatalf("delete: %v", err)
	}
	p2, err := persist.PageOf[userDO](db.Model(&userDO{}), response.PageQuery{Page: 1, PageSize: 10})
	if err != nil {
		t.Fatalf("page after delete: %v", err)
	}
	if p2.Total != 2 {
		t.Fatalf("软删后 total = %d", p2.Total)
	}
	var raw int64
	db.Unscoped().Model(&userDO{}).Where("id = ?", "u1").Count(&raw)
	if raw != 1 {
		t.Fatal("逻辑删除不得物理删除")
	}
	_ = ctx
}

// 契约断言（Redis 行为级）：key 首段=服务名、JSON 值、锁 token 语义、幂等三段流转。
func TestRedixRedis(t *testing.T) {
	addr := StartRedis(t)
	rdb := redis.NewClient(&redis.Options{Addr: addr})
	defer rdb.Close()
	ctx := context.Background()

	keys, err := redix.NewKeys("mysvc")
	if err != nil {
		t.Fatal(err)
	}

	// JSON 缓存
	cache := redix.NewCache(rdb)
	type payload struct{ N int }
	if err := cache.SetJSON(ctx, keys.K("user", "42"), payload{N: 7}, time.Minute); err != nil {
		t.Fatalf("setjson: %v", err)
	}
	var out payload
	if err := cache.GetJSON(ctx, keys.K("user", "42"), &out); err != nil || out.N != 7 {
		t.Fatalf("getjson = %v %+v", err, out)
	}
	s, _ := rdb.Get(ctx, keys.K("user", "42")).Result()
	if s[0] != '{' {
		t.Fatalf("value 必须是 JSON 文本: %s", s)
	}

	// 锁：互斥 + token 释放
	lock := redix.NewLock(rdb)
	lockKey := keys.K("lock", "demo")
	tok1, err := lock.Acquire(ctx, lockKey, time.Minute)
	if err != nil || tok1 == "" {
		t.Fatalf("acquire1 = %q %v", tok1, err)
	}
	if tok2, _ := lock.Acquire(ctx, lockKey, time.Minute); tok2 != "" {
		t.Fatal("互斥失败：第二个持有者不应获取成功")
	}
	if err := lock.Release(ctx, lockKey, tok1); err != nil {
		t.Fatalf("release: %v", err)
	}
	if tok3, _ := lock.Acquire(ctx, lockKey, time.Minute); tok3 == "" {
		t.Fatal("释放后应可重新获取")
	}

	// 幂等：占位 → 完成 → 回放；异参 1007
	idem := redix.NewIdempotency(rdb)
	ikey := keys.K("idem", "k1")
	first, err := idem.Reserve(ctx, ikey, "digest-a", 24*time.Hour)
	if err != nil || !first {
		t.Fatalf("reserve = %v %v", first, err)
	}
	if _, _, err := idem.Lookup(ctx, ikey, "digest-a"); err != middleware.ErrIdempotencyPending {
		t.Fatalf("占位后应 pending, got %v", err)
	}
	if err := idem.Complete(ctx, ikey, "digest-a", 201, []byte(`{"code":0}`), 24*time.Hour); err != nil {
		t.Fatalf("complete: %v", err)
	}
	status, body, err := idem.Lookup(ctx, ikey, "digest-a")
	if err != nil || status != 201 || string(body) != `{"code":0}` {
		t.Fatalf("replay = %d %s %v", status, body, err)
	}
	if _, _, err := idem.Lookup(ctx, ikey, "digest-b"); err != middleware.ErrIdempotencyMismatch {
		t.Fatalf("异参应 mismatch(1007), got %v", err)
	}
}
