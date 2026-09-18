-- users 表（postgresql.md 口径：snake_case；user 为 PG 保留字故表名取 users；
-- 时间列 timestamptz；逻辑删除 is_deleted 为 timestamptz——四栈同列名，NULL=存活）
CREATE TABLE IF NOT EXISTS users (
    id         BIGSERIAL PRIMARY KEY,
    name       TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    is_deleted TIMESTAMPTZ
);
CREATE INDEX IF NOT EXISTS idx_users_is_deleted ON users (is_deleted) WHERE is_deleted IS NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_users_name_alive ON users (name) WHERE is_deleted IS NULL;
