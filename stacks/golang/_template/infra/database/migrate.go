package database

import (
	"embed"

	"github.com/ydonghao/yarch/stacks/golang/persist"
)

//go:embed migration/*.sql
var migrations embed.FS

// MigrateUp 执行版本化迁移（禁 AutoMigrate，PG 规约六-1）。
func MigrateUp(dsn string) error {
	return persist.MigrateUp(dsn, migrations, "migration")
}
