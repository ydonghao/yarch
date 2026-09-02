# 《Java 开发手册（黄山版）》MySQL 数据库规约解析（独立成文）

> **本文件是解析与参考资料**：规范性内容已定稿为 [../contract/mysql.md](../../contract/infra/mysql.md)（v1.0，2026-09-01 评审通过），条文以彼为准；本篇保留原文逐条解析、正反例与各栈 ORM 方言详情。

> 从 [alibaba-java-manual-digest.md](alibaba-java-manual-digest.md) 拆出独立维护：数据库规约与语言栈无关，将来可直接作为 yarch 数据契约（`contract/`）或业务仓数据规范的基线。
> 来源：黄山版 1.7.1（2022-02-03）第五章「五、MySQL 数据库」，p35-39。四个小节共 50 条：建表规约 16 / 索引规约 11 / SQL 语句 13 / ORM 映射 10。
> 等级语义：【强制】违反即缺陷；【推荐】默认遵守、评审后可豁免；【参考】正向引导。

## 一、建表规约（16 条）

### 强制（10 条）

1. **是否字段**：`is_xxx` 命名，`unsigned tinyint`（1 是 / 0 否）；POJO 布尔属性**不加** `is` 前缀，靠 resultMap 映射。逻辑删除字段名 `is_deleted`。任何非负字段必须 `unsigned`。
2. **命名**：表名、字段名仅小写字母或数字；禁数字开头；禁两个下划线中间只有数字（`level_3_name` 反例）。理由：Windows/Linux 大小写敏感性不同。
3. **表名不用复数**：表名表示实体不表示数量，DO 类名对应单数。
4. **禁保留字**：`desc`、`range`、`match`、`delayed` 等（参照 MySQL 官方保留字表）。
5. **索引命名**：主键 `pk_字段名` / 唯一 `uk_字段名` / 普通 `idx_字段名`。
6. **小数必 `decimal`**：禁 `float`/`double`（精度损失）；超出 decimal 范围拆整数+小数分开存。
7. **定长用 `char`**：字符串长度几乎相等时。
8. **`varchar ≤ 5000`**：更长用 `text` 且独立成表、主键关联，避免拖累其它字段索引。
9. **必备三字段**：`id`（主键、`bigint unsigned`、单表自增步长 1）+ `create_time` + `update_time`（`datetime`；需时区则 `timestamp`）。
10. **禁物理删除**：一律逻辑删除（可追溯；注意逻辑删除与唯一索引的冲突需酌情处理）。

### 推荐（5 条）

11. 表名 = `业务名称_表的作用`（`trade_config`、`force_project`）。
12. 库名与应用名一致。
13. 字段含义/状态变更时同步更新字段注释。
14. **适度冗余**提性能，但冗余字段须：非频繁修改、非唯一索引、非超长 varchar/text。
15. **分库分表门槛**：单表超 500 万行或 2GB 才做；三年内到不了这个量级不要预先拆。

### 参考（1 条）

16. 合适的存储长度省空间、提速检索（unsigned 扩表示范围：人 tinyint / 龟 smallint / 恐龙化石 int / 太阳 bigint 的例子）。

## 二、索引规约（11 条）

### 强制（4 条）

1. **唯一特性必建唯一索引**（含组合字段）：应用层校验再完善，没唯一索引必有脏数据（墨菲定律）；insert 损耗可忽略。
2. **超 3 表禁 join**；join 字段类型**绝对一致**；被关联字段必须有索引。
3. **varchar 索引必须指定长度**：不必全字段索引；区分度公式 `count(distinct left(列, 长度))/count(*)`，长度 20 通常区分度 >90%。
4. **禁左模糊/全模糊搜索**：B-Tree 最左前缀特性决定无法用索引；需要就走搜索引擎。

### 推荐（6 条）

5. **order by 利用索引有序性**：`where a=? and b=? order by c` → 索引 `a_b_c`；范围查询会破坏有序性（`where a>10 order by b` 索引失效 filesort）。
6. **覆盖索引避免回表**：explain extra 出现 `using index`。
7. **超多分页用延迟关联/子查询**：MySQL 是取 offset+N 再丢前 offset 行，深翻页极慢——先定位 id 段再关联回表；或控制总页数。
8. **性能目标分级**：explain type 至少 range、要求 ref、最好 const；`type=index` 是全索引扫描，比 range 还低。
9. **组合索引区分度最高的在最左**；等号列前置于非等号列（`where c>? and d=?` 建 `idx_d_c`）。
10. 防字段类型隐式转换导致索引失效。

### 参考（1 条）

11. 避免三个极端误解：索引宁滥勿缺 / 吝啬建索引 / 抵制唯一索引（先查后插不可靠）。

## 三、SQL 语句（13 条）

### 强制（9 条）

1. **`count(*)` 是标准**：不要用 `count(列名)`/`count(常量)` 替代；`count(*)` 与 NULL 无关，`count(col)` 不统计 NULL 行。
2. `count(distinct col1, col2)`：任一列全 NULL 则整体返 0。
3. **`sum()` 的 NPE**：列全 NULL 时 `count`=0 但 `sum`=NULL——用 `IFNULL(SUM(col), 0)`。
4. **用 `ISNULL()` 判空**：NULL 与任何值比较均为 NULL（三值逻辑）；ISNULL 整体性好、性能更优。
5. **分页先 count**：count=0 直接返回，不再执行分页语句。
6. **禁外键与级联**：一切外键概念在应用层解决——外键/级联适合单机低并发，分布式高并发下级联是强阻塞、有更新风暴风险、拖慢插入。
7. **禁存储过程**：难调试、难扩展、无移植性。
8. **数据订正先 select 后 update/delete**：确认无误再执行。
9. **多表操作列名必须加别名限定**：防同名字段歧义（1052 Column ambiguous——正常运行两年后新表加同名字段即翻车的真实案例）。

### 推荐（2 条）

10. 表别名加 `as` 并按 `t1/t2/t3` 顺序命名。
11. `in` 能避则避；避不了控制集合 ≤1000 个。

### 参考（2 条）

12. 字符集统一 **utf8mb4**（表情存储）；注意 `LENGTH`（字节）与 `CHARACTER_LENGTH`（字符）的差别。
13. `TRUNCATE` 快但无事务、不触发 trigger——开发代码中不要使用。

## 四、ORM 映射（10 条，跨栈泛化）

原手册此节以 Java/iBATIS 措辞写成。按 yarch「一种规范，多种方言」的原则，拆为：**通用原则（语言无关）**+ 各栈方言对照。各栈规则以通用原则编号引用。

### 4.1 通用原则（从 10 条提炼，适用任何语言的 ORM/DB 访问层）

| # | 原条目 | 通用原则 | 等级 |
|---|---|---|---|
| G1 | ORM-1 | **禁 `select *` / 隐式全列**：显式列清单——省解析成本、防映射漂移、省网络（尤其 text 列） | 强制 |
| G2 | ORM-2 | **布尔三层映射链**：DB `is_xxx` ↔ 语言属性不带 `is`（`deleted`）↔ JSON lowerCamelCase；三层转换必须显式声明，不靠隐式推断 | 强制 |
| G3 | ORM-3 | **映射显式化 + 漂移校验**：列↔字段映射须显式配置或编译期/启动期校验（一表一映射），禁"同名即等价"的隐式约定当唯一防线 | 强制 |
| G4 | ORM-4 | **参数一律绑定**，禁任何形式的字符串拼接 SQL（注入） | 强制 |
| G5 | ORM-5 | **分页必须下推数据库**：limit/offset 进 SQL；禁全量取回内存分页（OOM 根因） | 强制 |
| G6 | ORM-6 | **禁无类型容器承载结果集**（Map/dict/HashMap 直接当 DO 用）：驱动版本差异会让类型漂移（bigint→i64/BigInteger）且丢失编译期检查 | 强制 |
| G7 | ORM-7 | **更新必带 `update_time`**（now 由 SQL/DB 侧生成或统一注入，禁依赖客户端时钟） | 强制 |
| G8 | ORM-8 | **只更新变更字段**：禁大而全 update（易错/低效/多占 binlog） | 推荐 |
| G9 | ORM-9 | **事务克制**：短小作用域；跨资源（缓存/消息/搜索）一致性要显式设计补偿，别指望一个事务包打天下 | 推荐 |
| G10 | ORM-10 | 动态条件构造要**显式、可读、逐条独立**（等值/非空判断各自成立），禁深层嵌套字符串拼装 | 参考 |

> ORM-5 的 iBATIS `queryForList` 是 G5 的历史案例，ORM-3 的 resultClass 是 G3 的 Java 措辞——原则本身跨栈。

### 4.2 Java（MyBatis / MyBatis-Plus / JPA）

| 原则 | 方言规则 |
|---|---|
| G1/G3 | `<resultMap>` 一表一映射；禁 resultClass 直返；MyBatis-Plus 也禁 `select all + wrapper` 惰性写法，查询列显式 |
| G2 | POJO `boolean deleted` + resultMap/`@TableField("is_deleted")` |
| G4 | `#{}`；`${}` 仅白名单排序字段且经校验 |
| G5 | `LIMIT #{offset}, #{size}` 传入 SQL；先 count=0 早退（SQL-5） |
| G6 | 结果集用 DO；禁 `HashMap`/`Hashtable` 接结果 |
| G7/G8 | 更新语句必含 `update_time = NOW()`；MyBatis-Plus 用 `UpdateWrapper.set` 显式列；禁全字段 updateById 当默认 |
| G9 | `@Transactional` 窄作用域；RocketMQ/缓存补偿显式设计 |

### 4.3 Golang（GORM / sqlx / sqlc / ent）

| 原则 | 方言规则 |
|---|---|
| G4 | 禁 `fmt.Sprintf` 拼 SQL；GORM 用 `?` 与参数绑定，`Raw` 慎用且必须绑定参数 |
| G6 | Scan/Find 目标必须是显式 struct；禁 `map[string]interface{}` 承结果 |
| G2 | struct 字段 `Deleted bool` + tag `column:is_deleted`（gorm）或 db tag（sqlx） |
| G1/G3 | 首选 **sqlc**（SQL 先行、编译期生成类型化代码，天然 G3）；GORM 关闭惰性加载，查询 `Select` 显式列 |
| G5 | `Limit/Offset` 下推；count=0 早退；深翻页用延迟关联（索引-7） |
| G7/G8 | `Updates(map/struct)` 只写变更列 + `update_time`；禁 `Save(全量)`；NOW() 由 DB 生成 |
| G9 | 事务用 `db.Transaction(func(tx) {...})` 短闭包，禁跨 RPC/HTTP 边界持有事务 |

### 4.4 Python（SQLAlchemy / asyncpg / Tortoise）

| 原则 | 方言规则 |
|---|---|
| G4 | Core 绑定参数 `:param` / `%s`；禁 f-string/format 拼接（类型除注入外还破坏驱动缓存） |
| G6 | ORM 模型 / Pydantic 显式字段承接；禁裸 `dict(row)` 直接当 API 模型 |
| G2 | 模型属性 `deleted: Mapped[bool]` + `mapped_column("is_deleted")` |
| G1/G3 | `select(Model.col1, Model.col2)` 显式列；2.0 风格禁隐式全列查询当默认 |
| G5 | `.limit().offset()` 下推 + count 先行 |
| G7/G8 | `update().values(update_time=func.now(), **changed)`；DB 侧 now()；禁全字段提交 |
| G9 | `async with session.begin()` 窄作用域；禁事务内 await 外部 IO |

### 4.5 Rust（sqlx / SeaORM / Diesel）

| 原则 | 方言规则 |
|---|---|
| G3/G6 | 首选 **sqlx `query!`/`query_as!` 宏**：列↔类型编译期校验（G3 的最强形态，schema 漂移直接编译失败）；Diesel 的 schema.rs 同理 |
| G4 | 绑定参数天然强制；禁 `format!` 拼 SQL |
| G2 | `#[sqlx(rename = "is_deleted")] pub deleted: bool` |
| G1 | `query_as!` 显式列清单即类型来源 |
| G5 | `LIMIT $1 OFFSET $2` 绑定；count=0 早退 |
| G7/G8 | `UPDATE ... SET update_time = NOW()` 只写变更列；NOW() DB 侧生成 |
| G9 | 事务 `tx` 显式作用域，禁跨 await 点长期持有；补偿逻辑显式建模 |

## 五、与 yarch 的关系（待审阅）

| 议题 | 判断 |
|---|---|
| 定位 | 本篇与语言栈解耦，规范性内容已定稿为 [../contract/mysql.md](../../contract/infra/mysql.md)（v1.0，2026-09-01 评审通过）：建表/索引/SQL 三节近乎语言无关；ORM 节以 §4.1 的 G1-G10 通用原则为规范主体，java/golang/python/rust 各栈方言表作为实现锚点（对应栈缺失时按 G 原则自推导）。PostgreSQL 侧对应物见 [../contract/postgresql.md](../../contract/infra/postgresql.md)（PostgresAI 风格 + GitLab 工程实践 + 本篇通用原则的三合一） |
| 与 RestResponse 契约的衔接点 | 建表强制 9（id/create_time/update_time）与逻辑删除铁律（强制 10）决定 API 资源形状；G2 三层映射链（`is_xxx` ↔ 语言属性无 `is` ↔ JSON camelCase）是 contract 与 ORM 规范的交接条款 |
| 分页 | 索引推荐 7（深翻页延迟关联）与 contract 的 `page/pageSize` 约定呼应；「count=0 直接返回」(SQL 强制 5) + G5（分页下推）可吸收为各栈分页实现规范 |
| 空值 | SQL 强制 3/4（sum NPE、ISNULL）与 RestResponse「空集合返回 []」精神一致 |
| 字符集 | utf8mb4 与表情存储，业务仓建库默认值 |
| 时代性提示 | 部分条目以 MySQL 5.x/单机视角写成（如禁外键的分布式论证、iBATIS 措辞）；核心结论仍成立，吸收时按 §4.1 的方式现代化表述 |
