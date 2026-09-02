package persist

import (
	"gorm.io/gorm"
	"gorm.io/gorm/logger"

	"github.com/yuandonghao/yarch-go/response"
)

// Open 打开 PG 连接（G3：GORM + pgx 驱动；新项目默认 PG）。
func Open(dsn string) (*gorm.DB, error) {
	db, err := gorm.Open(postgresOpen(dsn), &gorm.Config{
		Logger: logger.Default.LogMode(logger.Silent),
	})
	if err != nil {
		return nil, err
	}
	return db, nil
}

// Paginate 分页 Scope（下推 OFFSET/LIMIT 到 PG）。
func Paginate(q response.PageQuery) func(*gorm.DB) *gorm.DB {
	return func(db *gorm.DB) *gorm.DB {
		return db.Offset((int(q.Page) - 1) * int(q.PageSize)).Limit(int(q.PageSize))
	}
}

// PageOf 分页查询并装配 PageData：count（过滤后总数）+ 当前页 list。
// D6 天然成立：page 超出总页数时 OFFSET 越界返回空 list、total 仍为真实总数。
func PageOf[T any](db *gorm.DB, q response.PageQuery, where ...func(*gorm.DB) *gorm.DB) (*response.PageData[T], error) {
	for _, w := range where {
		db = w(db)
	}
	var total int64
	if err := db.Session(&gorm.Session{}).Count(&total).Error; err != nil {
		return nil, err
	}
	var list []T
	if err := db.Scopes(Paginate(q)).Find(&list).Error; err != nil {
		return nil, err
	}
	return response.NewPageData(list, total, q.Page, q.PageSize), nil
}
