-- 测试表：按 postgresql.md v1.0 建表规约（必备四列 + 部分唯一索引 + 注释）
create table test_users (
  id         bigint generated always as identity primary key,
  name       varchar(100) not null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  is_deleted boolean not null default false
);

comment on table test_users is '契约测试用户表';
comment on column test_users.name is '显示名';

create unique index uk_test_users_name_live on test_users (name) where is_deleted = false;
