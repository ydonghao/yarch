package persist

import (
	"database/sql"
	"fmt"
	"net/url"
	"strings"

	_ "github.com/jackc/pgx/v5/stdlib"
)

// EnsureDatabase 确保目标库存在（不存在则创建），随后即可安全 Open/MigrateUp。
// 用于共享 PG 实例的租户隔离：新服务 = 新 database（redis.md 的 key 前缀纪律在 redix.Keys）。
// dsn 形如 postgres://user:pass@host:port/dbname?sslmode=disable。
func EnsureDatabase(dsn string) error {
	dbName, err := dbNameOf(dsn)
	if err != nil {
		return err
	}
	admin, err := sql.Open("pgx", withDB(dsn, "postgres"))
	if err != nil {
		return err
	}
	defer admin.Close()

	var exists bool
	if err := admin.QueryRow(
		`SELECT EXISTS (SELECT 1 FROM pg_database WHERE datname = $1)`, dbName,
	).Scan(&exists); err != nil {
		return fmt.Errorf("persist: check database: %w", err)
	}
	if exists {
		return nil
	}
	// CREATE DATABASE 不支持参数绑定；dbName 已限制为标识符安全字符
	if !isSafeIdent(dbName) {
		return fmt.Errorf("persist: database name %q not identifier-safe", dbName)
	}
	if _, err := admin.Exec(`CREATE DATABASE "` + dbName + `"`); err != nil {
		return fmt.Errorf("persist: create database: %w", err)
	}
	return nil
}

func dbNameOf(dsn string) (string, error) {
	u, err := url.Parse(dsn)
	if err != nil {
		return "", fmt.Errorf("persist: parse dsn: %w", err)
	}
	name := strings.TrimPrefix(u.Path, "/")
	if name == "" {
		return "", fmt.Errorf("persist: dsn 缺少库名")
	}
	return name, nil
}

func withDB(dsn, db string) string {
	u, err := url.Parse(dsn)
	if err != nil {
		return dsn
	}
	u.Path = "/" + db
	return u.String()
}

func isSafeIdent(s string) bool {
	if s == "" {
		return false
	}
	for i := 0; i < len(s); i++ {
		c := s[i]
		if c == '_' || c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z' || c >= '0' && c <= '9' {
			continue
		}
		return false
	}
	return true
}
