# 《Java 开发手册（黄山版）》解析 —— 供 yarch 规约层审阅

> 来源：阿里巴巴官方开源仓库 [alibaba/p3c](https://github.com/alibaba/p3c)，版本 **1.7.1 黄山版**（2022-02-03 发布，55 页，Apache-2.0）。
> 截至 2026-09 无更新正式版，黄山版即最新。PDF 版权归原作者，不入本仓；本文是解析与裁剪，供决定 yarch 吸收哪些。

## 一、全书骨架

七个章节 + 三个附录，规约按力度分三级：**【强制】**（违反即故障/缺陷）、**【推荐】**（必须评审后才能豁免）、**【参考】**（正向引导）。全书条目量级：强制约 193 条、推荐约 97 条、参考约 37 条。

| 章 | 内容 | 页 | 与 yarch 的关系 |
|---|---|---|---|
| 一、编程规约 | 命名/常量/格式/OOP/日期时间/集合/并发/控制语句/注释/前后端/其他，11 小节 | p4-26 | 前后端小节与 contract 直接相关，其余进 Java 方言规范 |
| 二、异常日志 | **错误码** / 异常处理 / 日志规约 | p27-31 | ⚠️ 与 contract 错误码体系有分歧，需拍板 |
| 三、单元测试 | AIR 原则等 16 条 | p32-33 | 吸收进 yarch 测试基座规范 |
| 四、安全规约 | 水平越权/脱敏/注入/CSRF/防重放等 11 条 | p34 | 吸收为 yarch 安全 checklist |
| 五、MySQL 数据库 | 建表/索引/SQL/ORM | p35-39 | **已独立成文**：[alibaba-mysql-digest.md](alibaba-mysql-digest.md) |
| 六、工程结构 | 应用分层/二方库依赖/服务器 | p40-42 | **分层模型与 GAV/版本规则直接用于 yarch-java** |
| 七、设计规约 | UML 使用时机/单一职责/依赖倒置等 20 条 | p43-45 | 方法论，选择性吸收 |
| 附1-3 | 版本历史 / 名词解释 / **错误码列表（144 条）** | p45-51 | 附 3 是错误码分歧的对照物 |

## 二、逐章要点

### 一、编程规约（Java 侧通用，摘要）

- **命名**：名字达意、禁拼音混拼（国际通用名除外）、类名 UpperCamelCase、方法/变量 lowerCamelCase、常量全大写下划线、抽象类 `Abstract/Base` 开头、异常类 `Exception` 结尾、测试类 `被测类名+Test`、POJO 布尔不加 `is` 前缀、包名全小写单数、禁魔法值。
- **常量**：长变量拆层级、封装变量间的依赖（一行一个赋值）；枚举优于常量类。
- **OOP**：禁 `Object.equals(str常量)` 反过来写法（NPE）、浮点比较用 `BigDecimal` 且指定 scale、禁浮点除法算钱、`BigDecimal.valueOf` 优于构造器、基本类型包装类比较用 `equals`、`toString` 默认含敏感字段需重写、NPE 六大场景与 `Optional`、禁直接抛 `RuntimeException/Exception/Throwable`。
- **日期时间**：日期格式化线程安全（`DateTimeFormatter`）、闰年 366 天陷阱。
- **集合**：`subList` 原视图语义、`toArray` 传定长构造、`entrySet` 遍历、`Collections.sort`（GAV 相关）、`ArrayList.subList` 修改父子集合互抛异常、`Collectors.toMap` 的 NPE 与重复 key、`Arrays.asList` 返回不可变视图等。
- **并发**：`SimpleDateFormat` 线程安全、`ThreadLocal` 必须 `remove`（尤其线程池）、`HashMap` 多线程死链（resize 成环）、`CountDownLatch` 的 count>0 时 await、`Lock` 必须 unlock 于 finally、双检锁须 volatile、`ScheduledThreadPool` 优于 `Timer`、并发修改同一 Collection 须加锁。
- **控制语句**：switch 必须有 default、三目运算自动拆箱 NPE、卫语句（guard clause）减少嵌套、复杂布尔表达式赋名、禁条件表达式内嵌赋值、循环体内性能考量。
- **注释**：类/属性/方法必须 Javadoc、抽象方法必须 Javadoc 并说明用途、所有类标注创建者与日期、枚举字段必须注释、TODO/FIXME 需标注人与时间。
- **前后端**（详见 §三 对照表）。
- **其他**：正则预编译、禁 Apache BeanUtils 拷贝、枚举属性私有不可变、数据结构构造指定大小。

### 二、异常日志（与 yarch 契约重叠最大的章）

**错误码 13 条核心**：

1. 制定原则：快速溯源、沟通标准化——"错误码回答：谁的错？错在哪？"
2. 错误码**不体现**版本号和错误等级，以追加方式兼容
3. 全部正常但必须填错误码时返回 `00000`
4. **错误码 = 字符串 5 位 = 错误来源（1 位字母）+ 四位数字编号**：
   - `A`：用户端错误（参数错误、版本过低、支付超时…）
   - `B`：当前系统错误（业务逻辑、健壮性）
   - `C`：第三方服务错误（CDN 出错、消息投递超时…）
   - 编号 0001-9999，大类间步长预留 100
5. 编号与组织架构无关，统一平台先到先得、审批固定
6. 优先复用已有错误码，不随意新增
7. **错误码不能直接输出给用户当提示**；`stack_trace / error_message / error_code / user_tip` 四者关联但各司其职
8. `error_message` 承载具体业务信息，错误码本身保持抽象
9. 三方错误码向上抛时转义：`C→B`，message 带原错误码
10. 分一级/二级/三级宏观错误码：兜底用 `A0001（用户端错误）/ B0001（系统执行出错）/ C0001（三方出错）`
11. 后三位编号与 HTTP 状态码**无关**

**异常处理 14 条核心**：可预检查的 RuntimeException 不用 catch 兜（NPE 等）；异常不做流程控制；分清稳定/非稳定代码再 catch；捕获必处理否则上抛、最外层转成用户可理解内容；事务回滚注意手动 rollback；finally 关资源（try-with-resources）；finally 禁 return；RPC/二方包/动态类调用用 `Throwable` 拦截（防 `NoSuchMethodError` 逃逸）。

**日志规约 14 条核心**：只依赖 SLF4J 门面；日志至少存 15 天（敏感操作日志依法存 ≥6 个月并多机备份）；扩展日志命名 `appName_logType_logName.log`；占位符 `{}` 拼接而非字符串 `+`；trace/debug/info 输出前做级别开关判断；`additivity=false` 防重复打印；生产禁 `System.out/printStackTrace`；异常日志含"案发现场 + 堆栈"两类信息；打印日志禁直接 JSON 工具序列化对象（get 方法可能抛异常影响业务）；生产禁 debug、慎 info；用户输入错误用 warn 不用 error；脱敏。

### 三、单元测试（AIR 原则）

**A**utomatic 自动化 / **I**ndependent 独立 / **R**epeatable 可重复；必须 assert 不许 System.out 人肉验证；用例间禁止互相调用与次序依赖；不依赖外部环境（依赖全注入 Mock）；粒度至多类级、一般方法级；增量代码必须覆盖；必须放 `src/test/java`；单测遵守 BCDE（Border 边界/Correct 正常/Design 设计/Expression 表达）；核心在提测前完成而非上线后补。

### 四、安全规约

水平越权校验（用户个人页面/功能）；敏感数据展示脱敏（`139****1219`）；SQL 参数绑定禁拼接；**一切入参必验**（防 OOM/慢查询/缓存击穿/SSRF/重定向/注入/ReDoS）；输出转义防 XSS；表单/AJAX CSRF 验证；外跳白名单；平台资源防重放（频控/疲劳度/验证码）；上传文件大小与类型校验；配置文件密码加密；UGC 防刷与违禁词。

### 五、MySQL 数据库

已**独立成文**：[alibaba-mysql-digest.md](alibaba-mysql-digest.md)（建表 16 / 索引 11 / SQL 13 / ORM 10，共 50 条全量解析）——数据库规约与语言栈无关，单独维护，将来可直接作为 yarch 数据契约基线。

### 六、工程结构（对 yarch-java 最直接）

**分层模型**：开放 API 层 / 终端显示层 → Web 层（转发、基本参数校验）→ Service 层（具体业务逻辑）→ Manager 层（通用业务：三方封装与异常转义、Service 通用能力下沉、多 DAO 组合）→ DAO 层；旁路：第三方服务、外部数据接口。**分层异常规约**：DAO 层 `catch(Exception) → throw DAOException` 不打日志；Service 层必须落盘日志（保护案发现场）；Manager 同 DAO；Web 层顶层兜底不继续上抛；开放接口层把异常处理成错误码+错误信息返回。

**领域模型**：DO（表一一对应）/ DTO（Service/Manager 向外）/ BO（业务对象）/ Query（查询对象，>2 参数必须封装、禁 Map）/ VO（显示层）。

**二方库（GAV）规则**：GroupId `com.{公司/BU}.业务线.[子业务线]` 最多 4 级；ArtifactId = 产品线-模块，发布前先查重；版本号 `主.次.修订` 且**起始必须 1.0.0**；线上禁 SNAPSHOT；升级须保持仲裁结果不变并 `dependency:tree` 比对；二方库**接口返回值禁用枚举**（或含枚举的 POJO）；同群库统一版本变量；子 pom 禁同 GAV 不同 Version；发布者原则——只含 API/领域模型/Utils/常量，依赖尽量 provided，无日志具体实现只依赖门面。

**服务器**：远程调用**必须有超时**（故障最常见根因）；高并发调小 time_wait、调大 fd；`-XX:+HeapDumpOnOutOfMemoryError`；`Xms=Xmx`；慢服务独立线程池隔离。

### 七、设计规约

触发式 UML：用户超 1 类且 UseCase 超 5 个→用例图；状态超 3 个→状态图；调用链对象超 3 个→时序图；模型类超 5 个→类图；跨对象流程→活动图；**弱依赖识别 + 降级预案**；单一职责；组合优于继承；依赖倒置；开闭原则；DRY；敏捷 ≠ 不设计；代码即文档是错误观点。

## 三、与 yarch 契约的交叉对照（重点审阅）

### 1. 错误码体系 ⚠️ 根本分歧，需拍板

| 维度 | 阿里黄山版 | yarch contract v1 草案 |
|---|---|---|
| 类型 | 字符串 5 位 `A0101` | int32 `1004` |
| 分段语义 | 首字母=错误来源（用户/系统/三方），后 4 位编号按大类步长 100 | 段位=归属（1xxx 通用 / 2xxx IAM / 3xxx+ 业务仓注册） |
| 成功 | `00000` | `0` |
| 分层 | 一/二/三级宏观错误码，可兜底 | 单层，靠默认文案前缀追加细节 |
| 与 HTTP | 明确无关 | 每码固定映射 |
| 登记治理 | 统一平台先到先得审批 | 业务仓自行登记 |

**关键差异在分段哲学**：阿里按"谁的错"（用户/系统/三方）分——面向**客服与排障**；yarch 草案按"谁拥有"（通用/IAM/业务）分——面向**多仓治理**。两者可组合：例如保留 yarch 数字段位，同时给 RestResponse 增加 `source`（A/B/C）派生字段；或改用阿里的 5 位字符串方案但把"统一审批平台"裁剪为"业务仓登记"。**此项决定牵动四件套全部栈实现，是审阅第一优先级。**

阿里值得直接吸收的错误码原则（无论选哪种分段）：不体现版本与等级、追加式兼容、优先复用、错误码不直接给用户当提示、三方错误 C→B 转义并保留原码、与 HTTP 状态码解耦（yarch 的固定映射可保留，但"语义不依赖 HTTP"这条精神一致）。

### 2. 前后端规约 vs contract/rest-conventions.md

| 主题 | 阿里 | yarch v1 草案 | 判断 |
|---|---|---|---|
| 资源命名 | 名词复数、**单词分隔用下划线** | 名词复数、**kebab-case** | ⚠️ 分歧（业界 REST 更主流 kebab-case，阿里手册偏内部惯例） |
| URL 后缀 | 禁 `.json/.xml` | 同 | ✅ 一致 |
| 空集合 | 返回 `[]`/`{}` 不返回 null | 同 | ✅ 一致 |
| key 风格 | lowerCamelCase | 同 | ✅ 一致 |
| 超大整数 | Long 一律转 String | ID 不透明 string、金额禁浮点 | ✅ 一致 |
| URL 长度 | ≤2048 字节 | 未规定 | ➕ 建议补进 contract |
| body 大小 | 有上限意识（nginx 1MB/tomcat 2MB） | 未规定 | ➕ 网关层规则，可补"注" |
| 分页边界 | page<1 归 1，超总数归最后一页 | 超限报 1001 | ⚠️ 小分歧，择一 |
| 时间格式 | `yyyy-MM-dd HH:mm:ss`，GMT | ISO-8601 UTC `2026-08-31T12:00:00Z` | ⚠️ 分歧（ISO-8601 无歧义，更推荐） |
| 版本位置 | 【参考】放 HTTP 头 | 路径 `/api/v1/` | ⚠️ 分歧（路径版本更常见、可网关路由） |
| 错误响应四件 | HTTP 状态码 + errorCode + errorMessage + 用户提示 | code + message（message 兼作提示） | ⚠️ 阿里把"排障信息"与"用户提示"分开（user_tip），yarch message 合二为一——与错误码章的 stack_trace/error_message/error_code/user_tip 四分法呼应，是否引入 `userTip` 字段需拍板 |
| 缓存 | 【推荐】标记可缓存（s-maxage） | 未规定 | ➕ 可选补充 |

### 3. 日志规约 vs contract/logging-trace.md

阿里未规定 JSON 行协议（只要求 SLF4J 门面、保留 15 天、扩展日志命名、脱敏），yarch 的 ndjson 契约**更严格且不冲突**，可直接叠加。建议吸收进 yarch 规约层：敏感操作日志 ≥6 个月多机备份（法律要求）；"打印日志禁直接 JSON 序列化对象"（防止 get 方法副作用）；"案发现场 + 堆栈"两要素；warn/error 使用纪律。traceId 概念阿里手册没有——这是 yarch 的增量价值。

### 4. 工程结构 vs yarch-java 规划

阿里分层（Web/Service/Manager/DAO + DO/DTO/BO/Query/VO）与异常分层处理规约，是 yarch-java archetype 的直接蓝本；但注意它是"三层半贫血 + 数据库中心"风格，与 architecture.md 里 golang/rust 栈声明的 DDD 布局存在路线选择，**Java 栈采用阿里分层还是 DDD 分层需要拍板**（可折中：archetype 生成阿里分层，领域层约束按 DDD 演进）。

二方库 GAV 规则可直接吸收，但有两条与 yarch 现状冲突需注意：①"起始版本必须 1.0.0"（yarch 规划是 0.1.0，开源生态 0.x 表示 API 不稳定承诺，建议 yarch 保留 0.x 起步并在 1.0 前不承诺兼容）；②"接口返回值禁用枚举"——对二方库 API 正确，但 yarch 的 `GlobalErrorCode` 枚举用于实现 `ErrorCode` 接口且通过 RestResponse 序列化为 int，不违反其精神（枚举未直接跨进程）。

### 5. 单元测试 / 安全 / MySQL

AIR 原则 + BCDE + "src/test/java + assert" 直接吸收为 yarch 测试基座规范；安全 11 条强制直接吸收为 checklist（其中"一切入参必验"与 RestResponse 1001 呼应）；MySQL 章已独立成文（见 [alibaba-mysql-digest.md](alibaba-mysql-digest.md)），将来可升级为 yarch 数据契约基线，`is_deleted`、`is_xxx` 命名与逻辑删除铁律未来会进 archetype 模板。

## 四、建议的吸收清单

> **D1-D6 已全部拍板（2026-09-01）：业界标准优先于阿里手册**。下表"结论"列为最终口径，已反推回 [../../contract/](../../contract/README.md) 各契约文件（API 四件套 v1.0 定稿）。

| # | 内容 | 去处 | 结论（已拍板：业界标准） |
|---|---|---|---|
| D1 | 错误码分段方案：yarch 数字段位 vs 阿里 A/B/C 字符串 | contract/api/error-codes.md | ✅ int32 数字段位 + 标识符（HTTP/gRPC 业界形态）；阿里 A/B/C 不采用 |
| D2 | 是否引入 `userTip`（用户提示与排障信息分离） | contract/api/rest-response.md | ✅ 单 message，不引入 userTip（Stripe/GitHub/Google 同为单 message） |
| D3 | URL 命名：kebab-case（草案）vs 下划线（阿里） | contract/api/rest-conventions.md | ✅ kebab-case（业界 REST 主流） |
| D4 | 时间格式：ISO-8601 UTC（草案，推荐）vs `yyyy-MM-dd HH:mm:ss` | contract/api/rest-conventions.md | ✅ ISO-8601 UTC（RFC 3339，业界通行） |
| D5 | 版本位置：路径 `/v1/`（草案，推荐）vs HTTP 头（阿里参考条） | contract/api/rest-conventions.md | ✅ 路径版本（Stripe/GitHub 同） |
| D6 | 分页越界：报 1001（草案）vs 收敛到边界页（阿里） | contract/api/rest-conventions.md | ✅ 业界口径：越界返回 200 + 空 list + 真实 total（GitHub/Stripe 同；参数非法仍报 1001）——草案与阿里方案均不采用 |
| A1 | 错误码治理原则（追加兼容/优先复用/C→B 转义/不直接当提示） | contract/error-codes.md | 直接吸收 |
| A2 | URL ≤2048 字节；body 大小意识 | contract/rest-conventions.md | 直接吸收 |
| A3 | 日志纪律（门面/15 天/敏感 6 个月/禁 JSON 序列化打日志/两要素） | 新增 docs/java/logging 段 | 直接吸收 |
| A4 | 分层模型与分层异常规约、DO/DTO/BO/Query/VO | stacks/java 规范（archetype 蓝本） | 吸收，与 DDD 路线调和 |
| A5 | 二方库 GAV/版本/仲裁规则 | stacks/java 规范 | 吸收，0.x 起步例外声明 |
| A6 | 单测 AIR/BCDE、安全 11 条 | yarch 测试基座与安全 checklist | 直接吸收 |
| A7 | 远程调用必须超时、OOM dump、Xms=Xmx | stacks/java 服务器段 | 直接吸收 |

## 五、来源

- 下载：`https://github.com/alibaba/p3c`（master 分支 `Java开发手册(黄山版).pdf`）
- 版本历史见手册附 1：1.0.0 正式版(2017) → 华山(2019) → 泰山(2020) → 嵩山(2020) → 黄山(2022)，黄山新增 11 条（浮点后缀大写、枚举字段私有不可变、配置密码加密等）
