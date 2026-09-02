-- 案例：商品 + 订单（postgresql.md v1.0 建表口径）
create table products (
  id          bigint generated always as identity primary key,
  name        varchar(100) not null,
  price_cents bigint not null check (price_cents >= 0),
  stock       int not null default 0 check (stock >= 0),
  created_at  timestamptz not null default now(),
  updated_at  timestamptz not null default now(),
  is_deleted  boolean not null default false
);
comment on table products is '商品';
comment on column products.price_cents is '价格（整型分值，禁浮点）';
comment on column products.stock is '库存';

create table orders (
  id          bigint generated always as identity primary key,
  product_id  bigint not null,
  buyer_email varchar(255) not null,
  quantity    int not null check (quantity > 0),
  status      varchar(20) not null,
  created_at  timestamptz not null default now(),
  updated_at  timestamptz not null default now(),
  is_deleted  boolean not null default false
);
comment on table orders is '订单';
comment on column orders.status is '状态：pending / paid / cancelled';

create index idx_orders_product_id on orders (product_id) where is_deleted = false;
create index idx_orders_id_live on orders (id) where is_deleted = false;
