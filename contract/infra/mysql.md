# MySQL 开发规约（v1.0 已定稿）

> **状态：已定稿**（2026-09-01 评审通过，纳入 yarch 规约层）。供人阅读、供 AI 作为规范上下文使用。
> 等级：**【强制】**违反即缺陷；**【推荐】**默认遵守、评审后可豁免；**【参考】**正向引导。基于 MySQL 8.0（InnoDB）。
> 主体源自阿里《Java 开发手册（黄山版）》数据库章，ORM 节为跨栈泛化条文；原文解析与各栈方言详情见 [../docs/alibaba-mysql-digest.md](../../docs/references/alibaba-mysql-digest.md)。

## 一、命名规约

1. 【强制】表名、字段名仅小写字母或数字；禁大写字母、禁数字开头、禁两个下划线中间只出现数字（`level_3_name` 为反例）。理由：Windows/Linux 下大小写敏感性不同。
2. 【强制】表名用单数名词，表达实体而非数量；对应 DO 类名同为单数。
3. 【强制】表达是否概念的字段命名 `is_xxx`，类型 `tinyint unsigned`（1 是 / 0 否）；逻辑删除字段名 `is_deleted`。业务代码属性名不带 `is` 前缀（映射链见五-2）。
4. 【强制】禁用保留字：`desc`、`range`、`match`、`delayed` 等，参照 MySQL 官方保留字表。
5. 【强制】索引命名：主键 `pk_字段名`、唯一索引 `uk_字段名`、普通索引 `idx_字段名`。
6. 【强制】库名与应用（服务）名一致；一个应用一个库；跨应用取数必须走 API，禁止跨库直连。
7. 【强制】表名遵循 `业务名称_表的作用`（`trade_config`、`force_project`）。
8. 【推荐】字段含义或状态取值变更时，同步更新字段注释；建表时表和字段注释必写。

## 二、建表规约

1. 【强制】每表必备三列：

```sql
id          bigint unsigned not null auto_increment comment '主键',
create_time datetime     not null default current_timestamp,
update_time datetime     not null default current_timestamp on update current_timestamp,
-- 单表自增主键：primary key (id)；分库分表场景改用分布式 ID（雪花等），类型仍 bigint unsigned
```

2. 【强制】逻辑删除，禁物理 `delete`（审计与追溯）；唯一约束与逻辑删除共存时，唯一索引纳入 `is_deleted` 的衍生列或删除时间列参与唯一性。
3. 【强制】小数一律 `decimal(p, s)`；金额等精确值禁 `float`/`double`；超出 decimal 范围拆整数与小数分开存储。
4. 【强制】字符串长度几乎相等时用 `char(n)`；`varchar` 长度不超过 5000，更长用 `text` 且独立成表、以主键关联，避免拖累同表字段索引。
5. 【强制】任何非负数字段必须 `unsigned`。
6. 【强制】字符集与排序规则：库级 `utf8mb4` + `utf8mb4_0900_ai_ci`（表情存储与多语言前提）。
7. 【强制】状态/类型列禁魔法数字，用小写字符串枚举或带注释的 tinyint 枚举 + CHECK 约束；取值必须写入字段注释。
8. 【推荐】适度冗余提升查询性能，冗余字段必须同时满足：非频繁修改、非唯一索引列、非超长 varchar/text。
9. 【推荐】单表预估超过 500 万行或容量超过 2GB 才考虑分库分表；三年内到不了该量级不预先拆。
10. 【参考】选择合适的存储长度：省空间、省索引、提检索速度（如年龄 `tinyint unsigned`）。

## 三、索引规约

1. 【强制】业务上具有唯一特性的字段（含组合字段）必须建唯一索引——应用层校验再完善，没有唯一索引必有脏数据（墨菲定律）；insert 性能损耗可忽略。
2. 【强制】超过三个表禁止 join；join 字段数据类型必须完全一致；被关联字段必须有索引。
3. 【强制】`varchar` 字段建索引必须指定前缀长度，不对全字段建索引；区分度公式 `count(distinct left(列, 长度)) / count(*)`，长度 20 通常区分度 >90%。
4. 【强制】页面搜索严禁左模糊与全模糊（`'abc%'` 可走索引，`'%abc'` 不可）；需要则走搜索引擎。
5. 【推荐】`order by` 利用索引有序性：`where a = ? and b = ? order by c` 建 `(a, b, c)`；范围条件破坏有序性（`where a > 10 order by b` 无法用 `(a, b)` 排序）。
6. 【推荐】利用覆盖索引避免回表：`explain` 的 extra 出现 `using index`。
7. 【推荐】超多分页用延迟关联或子查询优化：先定位 id 段再回表——`select t1.* from t1, (select id from t1 where ... limit 100000, 20) t2 where t1.id = t2.id`；或控制总页数。
8. 【推荐】组合索引区分度最高的列在最左；等值条件列前置于范围条件列（`where c > ? and d = ?` 建 `(d, c)`）。
9. 【推荐】防止字段类型隐式转换导致索引失效（如字符串列传数字）。
10. 【参考】`explain` 性能目标：至少 `range`，要求 `ref`，最好 `const`；`type = index` 为全索引扫描，比 `range` 还低，视为不达标。

## 四、SQL 语句

1. 【强制】行数统计用 `count(*)`（SQL92 标准，与 NULL 无关）；`count(列名)` 不统计该列 NULL 行，语义不同不得混用；`count(distinct col1, col2)` 中任一列全 NULL 则整体为 0。
2. 【强制】`sum()` 在空集上返回 NULL 而非 0，应用层必须处理：`select ifnull(sum(col), 0)`。
3. 【强制】判空用 `is null` / `is not null`；禁 NULL 与任何值直接比较（三值逻辑：`NULL = NULL`、`NULL <> 1` 结果均为 NULL）。
4. 【强制】分页前先 count，count 为 0 直接返回，不再执行分页语句。
5. 【强制】禁外键与级联：一切外键概念在应用层解决——外键与级联适合单机低并发，分布式高并发下级联是强阻塞、有更新风暴风险、拖慢插入。
6. 【强制】禁存储过程：难调试、难扩展、无移植性。
7. 【强制】数据订正（特别是删除或修改）先 `select` 确认影响范围，确认无误才执行。
8. 【强制】多表查询/变更的列名必须加表别名（或表名）限定——同名字段在后续加列时会直接抛 1052 歧义异常。
9. 【强制】`in` 集合控制在一千个以内，超出改 join 或临时表。
10. 【推荐】表别名加 `as` 并按 `t1 / t2 / t3` 顺序命名。
11. 【推荐】批量写入用多值 `insert into ... values (...), (...)`；大批量导入关 binlog 评估或分批。
12. 【参考】`truncate` 快于 `delete` 但无事务、不触发 trigger——开发代码中禁用。

## 五、ORM 映射（跨栈泛化条文）

1. 【强制】查询显式列清单，禁 `select *`：省解析成本、防映射漂移、省网络（尤其 text 列）。
2. 【强制】布尔三层映射链：DB `is_deleted` ↔ 语言属性 `deleted`（无前缀）↔ JSON `deleted`（camelCase）；三层转换必须显式声明（tag / resultMap / 映射配置），不依赖隐式同名推断。
3. 【强制】映射显式化 + 漂移校验：列↔字段映射显式配置或编译期/启动期校验，禁"同名即等价"的隐式约定当唯一防线（Java resultMap；Go 首选 sqlc；Python `mapped_column`；Rust 首选 sqlx `query_as!` / Diesel schema）。
4. 【强制】参数一律绑定（`#{}` / `?` / `$1`），禁任何形式的字符串拼接 SQL；`${}` 仅限白名单排序字段且必须校验。
5. 【强制】分页必须下推数据库（`limit` 进 SQL）；禁全量取回内存分页（OOM 根因）。
6. 【强制】禁无类型容器承载结果集（`HashMap` / `map[string]interface{}` / `dict`）：驱动版本差异会让类型漂移（bigint → Long/BigInteger）且丢失编译期检查。
7. 【强制】更新必带 `update_time`（DB 侧 `on update current_timestamp` 或统一注入），禁依赖客户端时钟。
8. 【强制】更新只写变更字段：禁大而全更新接口——易错、低效、多占 binlog。
9. 【推荐】事务克制：短小作用域；跨资源（缓存/消息/搜索）一致性用显式补偿设计；`@Transactional` 不滥用（影响 QPS）。
10. 【参考】动态条件构造显式、可读、逐条独立（等值/非空判断各自成立），禁深层嵌套字符串拼装。

## 六、与 PostgreSQL 规约的口径分野

两库规约并立时，以下差异是**既定决策**（各随生态，跨库映射在应用层做），不得互相"纠正"：

| 项 | MySQL（本规约） | PostgreSQL（[postgresql.md](postgresql.md)） |
|---|---|---|
| 时间字段 | `create_time` / `update_time`，`datetime` | `created_at` / `updated_at`，`timestamptz` |
| 是否字段 | `is_xxx` + `tinyint unsigned` | `is_xxx` + 真 `boolean` |
| 主键自增 | `bigint unsigned auto_increment` | `bigint generated always as identity` |
| 判空函数 | `is null`（标准语法；阿里原文推荐的 `isnull()` 不采用，理由：可移植与索引一致） | `is null` |
| 字符串 | `varchar` ≤5000，更长 text 拆表 | 默认 `text` |
| 字符集 | `utf8mb4` 显式声明 | 库级 UTF-8 默认 |
| 新项目默认 | 存量与既有生态 | **新项目默认 PG**（架构决策，2026-09） |

---

## 附：来源记录

- 主体：阿里《Java 开发手册（黄山版）》第五章（建表 16 / 索引 11 / SQL 13 / ORM 10），逐条原文与正反例见 [../docs/alibaba-mysql-digest.md](../../docs/references/alibaba-mysql-digest.md)。
- 与原文的三处有意偏离（均已在条文内注明理由）：判空用 `is null` 而非 `isnull()`；ORM 节泛化为跨栈条文（G1-G10）；补"新项目默认 PG"的选型决策行。
- 时间字段保留 `create_time`（阿里口径），与 PG 的 `created_at` 分生态并立——见六。
