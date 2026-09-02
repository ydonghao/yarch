-- 用户表示例表：按 contract/infra/postgresql.md v1.0（必备四列 + 部分唯一索引 + 注释 + 显式列）
create table users (
  id         bigint generated always as identity primary key,
  email      varchar(255) not null,
  name       varchar(100) not null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  is_deleted boolean not null default false
);

comment on table users is '用户';
comment on column users.email is '登录邮箱，逻辑删除后可复用（部分唯一索引仅约束未删行）';
comment on column users.name is '显示名';

create unique index uk_users_email_live on users (email) where is_deleted = false;
