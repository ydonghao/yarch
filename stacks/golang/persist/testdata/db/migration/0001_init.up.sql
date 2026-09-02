CREATE TABLE users (
    id          varchar(64) PRIMARY KEY,
    name        varchar(128) NOT NULL,
    created_at  timestamptz NOT NULL DEFAULT now(),
    updated_at  timestamptz NOT NULL DEFAULT now(),
    is_deleted  timestamptz
);
