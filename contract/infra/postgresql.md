# PostgreSQL 开发规约（v1.0 已定稿）

> **状态：已定稿**（2026-09-01 评审通过，纳入 yarch 规约层）。供人阅读、供 AI 作为规范上下文使用。
> 等级：**【强制】**违反即缺陷；**【推荐】**默认遵守、评审后可豁免；**【参考】**正向引导。基于 PostgreSQL 14+。来源与取舍讨论稿：[../docs/alibaba-java-manual-digest.md](../../docs/references/alibaba-java-manual-digest.md) 系列。

## 一、命名规约

1. 【强制】标识符（库、模式、表、列、索引、约束、序列、函数）一律 snake_case：仅小写字母、数字、下划线；以字母开头，不得以下划线结尾；禁 CamelCase、禁拼音混拼、禁保留字（`order`、`user`、`desc`、`check`、`limit` 等，参照 PG 官方保留字表）。
2. 【强制】表名用单数名词，表达实体而非数量；默认 `public` 模式，跨 schema 查询显式写 schema 前缀。
3. 【强制】布尔列以 `is_` / `has_` / `can_` 开头，类型为 `boolean`；业务代码属性名不带前缀（映射链见五-2）。
4. 【强制】时间列命名 `created_at` / `updated_at` / `deleted_at`，类型 `timestamptz`。
5. 【强制】外键列 = 引用表名单数 + `_id`：`orders.user_id` 引用 `users.id`。
6. 【强制】索引命名：唯一索引 `uk_表名_列[列…]`；普通索引 `idx_表名_列[列…]`；部分索引在词尾追加语义缩写（`idx_orders_user_id_live`）；主键使用 PG 默认 `表名_pkey`。
7. 【强制】约束命名：CHECK `ck_表名_语义`；外键约束（如使用）`fk_表名_列`；默认值以外的一切约束都必须显式命名。
8. 【强制】库名与应用（服务）名一致；一个应用一个库；跨应用取数必须走 API，禁止跨库直连。
9. 【推荐】视图 `v_` 前缀、物化视图 `mv_` 前缀、触发器 `trg_表名_用途`；UDF `fn_` 前缀且不承载业务逻辑（见四-8）。

## 二、建表规约

1. 【强制】每表必备四列：

```sql
id         bigint generated always as identity primary key,
created_at timestamptz not null default now(),
updated_at timestamptz not null default now(),
is_deleted boolean     not null default false
```

2. 【强制】逻辑删除，禁物理 `delete`（审计与追溯）；唯一约束与逻辑删除共存时，必须用部分唯一索引：

```sql
create unique index concurrently uk_users_email_live on users (email) where is_deleted = false;
```

3. 【强制】每张表必须有 `comment on table`；状态/枚举列必须有 `comment on column` 说明全部取值含义。
4. 【强制】小数一律 `numeric(p, s)`；金额等精确值禁 `float`/`double`；纯计数整数按范围选 `int`/`bigint`，默认 `bigint`。
5. 【强制】字符串默认 `text`；仅固定长度语义用 `char(n)`（如国家码 `char(2)`）；长度是业务校验规则时用 `varchar(n)` 或 CHECK 约束，不以性能为由选型。
6. 【强制】状态/类型列用小写字符串 + CHECK 枚举（`'pending'`、`'paid'`…），禁魔法数字；PG 原生 `enum` 类型仅在取值极稳定时使用（加值易、减值难），默认不用。
7. 【强制】无符号 ID 用 `uuid` 类型，分布式主键场景用 UUIDv7（时间有序，索引友好）。
8. 【强制】非负语义用 CHECK 约束表达（`check (amount >= 0)`）——PG 无 unsigned。
9. 【强制】默认排序规则不指定（库级 UTF-8 + 默认 collation）；按前缀加速的等值查询需要时用 `text_pattern_ops` 索引，不改列类型。
10. 【推荐】列顺序固定并冻结：`id` → 外键列 → 业务语义列 → 状态/枚举列 → 布尔列 → 可空列 → 时间列 → `is_deleted`。
11. 【推荐】单表预估三年内超千万行或超 10GB 才考虑分区；分区键必须在建表期确定，优先 `created_at` 范围分区；已有大表改造分区走新表迁移。
12. 【推荐】适度冗余提升查询性能，冗余字段必须同时满足：非频繁修改、非唯一索引列、非超长文本。
13. 【参考】`jsonb` 仅用于真正无 schema 的扩展属性；可预见结构建实体列。`jsonb` 查询条件按需建 GIN 索引。

## 三、索引规约

1. 【强制】业务上具有唯一特性的字段（含组合字段）必须建唯一索引——应用层校验再完善，没有唯一索引必有脏数据；与逻辑删除共存见二-2。
2. 【强制】超过三个表禁止 join；join 字段数据类型必须完全一致；被关联字段必须有索引。
3. 【强制】模糊匹配禁左模糊与全模糊（`'abc%'` 可走索引，`'%abc'` 不可）；需要全文检索用 `pg_trgm` GIN 索引、`tsvector` 或专用搜索引擎。
4. 【强制】生产库建索引必须 `create index concurrently`；失败后必须清理残留的 `INVALID` 索引再重试。
5. 【推荐】组合索引区分度最高的列在最左；等值条件列前置于范围条件列：`where c > ? and d = ?` 建 `(d, c)`。
6. 【推荐】`order by` 列对齐组合索引尾部列，避免 sort；优先覆盖索引达成 Index Only Scan。
7. 【推荐】深分页优先 keyset（游标）分页：`where id > $last_id order by id limit n`；延迟关联（先取 id 再回表）次之；禁无上限 offset 扫描。
8. 【推荐】逻辑删除表的常规查询，索引一律建为部分索引 `where is_deleted = false`——更小、更快、写入更省。
9. 【推荐】逻辑删除表的高频过滤条件（租户、状态等）与部分索引组合使用，替代宽泛全表索引。
10. 【推荐】外键关联（应用层维护）的关联列必须有索引，否则关联查询与父表清理全表扫。
11. 【推荐】优先表达式索引替代冗余列：唯一约束大小写不敏感时建 `unique index on users (lower(email))`。
12. 【参考】`explain (analyze, buffers)` 是性能判断唯一准绳：大表出现 Seq Scan 必须评审；Index Scan/Index Only Scan 为达标线，Bitmap Scan 视场景。

## 四、SQL 语句

1. 【强制】SQL 关键字小写；函数视为标识符书写（`date_trunc()` 不是 `DATE_TRUNC()`）。
2. 【强制】join 显式写明类型（`inner join` / `left join`）；表别名带 `as` 并按 `t1 / t2 / t3` 或有语义名命名；多表 SQL 的列名必须带别名限定。
3. 【强制】参数一律绑定（`$1` / 驱动占位符）；禁任何形式的字符串拼接 SQL。排序/表名等标识符白名单动态拼接必须经显式校验。
4. 【强制】查询显式列清单，禁 `select *`：省解析成本、防映射漂移、省网络（尤其 text 大列）。
5. 【强制】行数统计用 `count(*)`（SQL92 标准，含 NULL 行）；`count(col)` 不统计该列 NULL 行，语义不同不得混用。
6. 【强制】判空用 `is null` / `is not null`：NULL 与任何值的比较结果均为 NULL（三值逻辑）。
7. 【强制】`sum()` 在空集上返回 NULL 而非 0，应用层必须处理（或 `coalesce(sum(col), 0)`）。
8. 【强制】分页前先 count，count 为 0 直接返回，不再执行分页语句。
9. 【强制】禁存储过程承载业务逻辑（难调试、难扩展、无移植性）；触发器仅限 `updated_at` 维护等基础设施用途。
10. 【强制】数据订正（update/delete）先 `select` 确认影响范围、经评审执行；`update` / `delete` 必须带 `where`。
11. 【强制】`in` 集合控制在一千个以内，超出改临时表 join 或 `= any(array)`。
12. 【强制】批量写入用多值 `insert ... values (...), (...)` 或 `copy`；禁循环单行 insert 处理大批量。
13. 【推荐】`update` 只 set 变更列——MVCC 下全列更新放大表膨胀与 WAL。
14. 【推荐】CTE（`with`）代替深层嵌套子查询；`group by` 写显式列名。
15. 【推荐】条件计数用 `count(*) filter (where …)`，优于多重 `count(case when …)`。
16. 【参考】日期字面量一律 ISO 8601（`2026-09-01T12:00:00Z`）。

## 五、ORM 与数据访问

1. 【强制】查询显式列清单并映射到显式类型（G1/G6）：禁 `select *`，禁 `map` / `dict` / `HashMap` 等无类型容器承载结果集。
2. 【强制】布尔三层映射链：DB `is_deleted` ↔ 语言属性 `deleted`（无前缀）↔ JSON `deleted`（camelCase）；三层转换必须显式声明（tag/映射配置），不依赖隐式同名推断。
3. 【强制】映射漂移必须有编译期或启动期校验（G3）：Rust 首选 `sqlx::query_as!` / sqlc / Diesel schema；Go 首选 sqlc；Java 用显式 resultMap/`@Column`；Python 用 `mapped_column`。
4. 【强制】参数绑定（G4），同四-3。
5. 【强制】分页必须下推数据库（`limit` / `offset` / keyset），禁全量取回内存分页（G5）。
6. 【强制】`updated_at` 由数据库默认值/触发器/ORM 自动填充（G7），禁依赖客户端时钟；应用代码不显式赋值。
7. 【强制】更新只写变更字段（G8）：部分 `update` 语句或 ORM 的变更集机制；禁"全字段 updateById"作为默认路径。
8. 【强制】连接池必选：PG 每连接一进程，应用直连总数控制在同量级核心数内；应用侧池 + 部署侧 PgBouncer（transaction 模式注意 prepared statement 兼容配置）。
9. 【推荐】事务短作用域（G9）：禁事务内发起 HTTP/RPC 等外部 IO；跨缓存/消息的一致性用显式补偿设计，不指望数据库事务包打天下。
10. 【参考】动态查询条件显式构造、逐条独立（等值/非空判断各自成立），禁深层嵌套字符串拼装（G10）。

## 六、迁移规约

1. 【强制】一切 DDL 默认按零停机设计；迁移版本化进仓（golang-migrate / Flyway / Alembic / diesel migration 等），禁手工改库。
2. 【强制】加列直接加（可空或带 default，PG 11+ 不重写表）；回填数据分批进行；最后再设 `not null`——三步走，禁一步加 not null 带默认值锁大表。
3. 【强制】加唯一/CHECK 约束用两步：`... not valid` → `validate constraint`，避免全表锁定。
4. 【强制】删列两阶段：先发布停止读写该列的代码，下个版本再 `drop column`。
5. 【强制】大表改类型/改语义走新列：新建列 → 双写 → 分批回填 → 切读 → 删旧列；禁直接 `alter column type` 改大表。
6. 【强制】迁移评审内容：`explain` 影响、锁范围、执行时长、失败修复预案（forward-only 也必须有）。
7. 【推荐】部署后迁移（post-deployment）与常规迁移分离：依赖新代码的 DDL 放部署后执行。

## 七、事务与并发

1. 【强制】隔离级别默认 `read committed`；需要语句级一致快照用 `repeatable read`（PG 下无幻读异常）；禁默认 `serializable`（重试复杂度高，除非框架已处理）。
2. 【强制】悲观锁 `select ... for update` 必须命中索引且设置 `lock_timeout`；禁无索引条件的 for update。
3. 【推荐】幂等写入用唯一索引 + `on conflict do nothing`，以索引冲突表达幂等，不靠先查后插。
4. 【推荐】配置 `idle_in_transaction_session_timeout` 与 `statement_timeout`，监控长事务与 `idle in transaction`。
5. 【参考】热点行更新（计数器类）考虑 `insert ... on conflict do update` 聚合，或异步批量回写。

## 八、运维基线

1. 【强制】备份必须是 WAL 归档 + 基础备份（PITR 能力）；`pg_dump` 不构成备份体系。
2. 【强制】`pg_stat_statements` 常开，慢查询按周期采样评审。
3. 【推荐】autovacuum 按写放大调优（大表提高 `autovacuum_vacuum_scale_factor` 敏感度）；监控 dead tuples 与表膨胀。
4. 【推荐】分区表监控分区数量与单分区体积，提前归档冷数据。

---

## 附：来源与取舍记录

- 风格基调（命名/书写/表名单数/identity 主键）：[PostgresAI SQL Style](https://postgres.ai/rules/sql-style)。
- 工程骨架（零停机迁移、keyset 分页、批量处理、并发索引）：[GitLab Database Development Guidelines](https://docs.gitlab.com/development/database/)。
- 通用原则（唯一索引、join 上限、模糊匹配、count/NULL 语义、逻辑删除、禁存储过程等）与 ORM G1-G10：源自阿里黄山版数据库章，见 [alibaba-mysql-digest.md](../../docs/references/alibaba-mysql-digest.md)。
- 已定决策：时间字段 `created_at`（随 PG 生态）；外键默认不加、完整性在应用层（一-5 仅约束命名）；关键字小写；表列排序规范吸收（二-10）。
- MySQL 专有条目的 PG 转写对照与差异分析，审阅需要时见本仓 git 历史与本文件讨论稿版本。
