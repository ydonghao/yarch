// Package persist 实现契约 PostgreSQL 规约的 ORM 落地件（G3：GORM + pgx）：
// 显式列清单模型基类（逻辑删除 is_deleted / 审计时间戳）、分页下推（D6 越界空页天然成立）、
// 主键游标通道、golang-migrate 版本化迁移（禁 AutoMigrate，PG 规约六-1）。
package persist

import (
	"time"

	"gorm.io/gorm"
)

// Model 审计基类：ID 不透明 string（服务端定，契约数据表示）；
// is_deleted 逻辑删除（gorm.DeletedAt 语义，列名对齐 java 侧 is_deleted）；
// created_at / updated_at GORM 自动填充（timestamptz，ISO-8601 UTC 序列化）。
type Model struct {
	ID        string         `gorm:"primaryKey;size:64"`
	CreatedAt time.Time      `gorm:"autoCreateTime"`
	UpdatedAt time.Time      `gorm:"autoUpdateTime"`
	DeletedAt gorm.DeletedAt `gorm:"column:is_deleted;index"`
}

// ModelByName 带审计人字段的基类（created_by/updated_by 由业务从认证上下文填充）。
type ModelByName struct {
	Model
	CreatedBy string `gorm:"size:64"`
	UpdatedBy string `gorm:"size:64"`
}
