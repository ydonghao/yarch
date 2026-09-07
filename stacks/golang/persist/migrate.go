package persist

import (
	"embed"
	"errors"
	"fmt"

	"github.com/golang-migrate/migrate/v4"
	_ "github.com/golang-migrate/migrate/v4/database/pgx/v5"
	"github.com/golang-migrate/migrate/v4/source/iofs"
	"gorm.io/driver/postgres"
	"gorm.io/gorm"
)

func postgresOpen(dsn string) gorm.Dialector { return postgres.Open(dsn) }

// MigrateUp 执行版本化迁移（db/migration 目录 embed 进二进制；契约 PG 规约六-1：
// 迁移版本化进仓，禁 GORM AutoMigrate）。
func MigrateUp(dsn string, fs embed.FS, dir string) error {
	src, err := iofs.New(fs, dir)
	if err != nil {
		return fmt.Errorf("persist: migrations fs: %w", err)
	}
	m, err := migrate.NewWithSourceInstance("iofs", src, "pgx5://"+trimScheme(dsn))
	if err != nil {
		return fmt.Errorf("persist: migrate init: %w", err)
	}
	defer func() { _, _ = m.Close() }()

	if err := m.Up(); err != nil && !errors.Is(err, migrate.ErrNoChange) {
		return fmt.Errorf("persist: migrate up: %w", err)
	}
	return nil
}

func trimScheme(dsn string) string {
	for _, p := range []string{"postgres://", "postgresql://", "pgx5://", "pgx://"} {
		if len(dsn) >= len(p) && dsn[:len(p)] == p {
			return dsn[len(p):]
		}
	}
	return dsn
}
