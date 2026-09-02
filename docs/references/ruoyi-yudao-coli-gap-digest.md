# 脚手架对标解析 —— ruoyi / yudao / coli-architecture（供审阅拍板）

> **定位**：解析与决策材料（非规约）。对标对象：[RuoYi](https://doc.ruoyi.vip/ruoyi/document/kslj.html)（权限管理系统，rbac+代码生成+Quartz）、[芋道 ruoyi-vue-pro](https://doc.iocoder.cn/feature/)（116 项企业级功能：工作流/多租户/支付/商城/CRM/AI/IoT）、coli-architecture（Sa-Token + Nacos + SCG/Sentinel + RocketMQ/Seata + XXL-Job + Knife4j + Flowable + 飞鱼代码生成 + K8s/ArgoCD）。
> **判定框架**：yarch 是"工程架构平台"（契约可执行化 + AI 施工模具），不是"业务中台"。每项缺口按【立即对齐 / 触发式已登记 / 明确不对齐】三档给出建议，逐项拍板后进入施工。

## 一、已对齐（无缺口）

统一响应/异常/错误码段位、日志 + traceId 贯穿、双通道分页（页码 + keyset）、幂等（Idempotency-Key，强于 ruoyi @RepeatSubmit）、逻辑删除 + 审计四列强制、参数校验→1001、API 文档（springdoc）、双模板分层 + ArchUnit 机检、Flyway 迁移（ruoyi 系多为手工 SQL）、Docker 化 + CI、健康检查（actuator）、缓存规约 + 分布式锁、**测试基座**（ruoyi/yudao 的单测与契约断言恰恰是弱项——yarch 的 51 项平台测试 + 生成工程 9×2 验收是反向优势）。

## 二、缺口与对齐决策清单（G 表，待拍板）

### G1【建议立即对齐】接口限流 `@RateLimited`

- 现状：契约已定 1006/RATE_LIMITED/429（error-codes.md），**实现缺位**——"实现与契约不一致即 bug"，这是唯一一处契约有码无实现的缺口。
- 方案：web starter 增加 `@RateLimited(key, qps, window)` 注解（Redis 滑动窗口，复用 redis starter；无 Redis 降级单机窗口）；ruoyi @RateLimiter / yudao @RateLimit 同型。
- 工作量：~半天（含并发压测用例）。

### G2【建议立即对齐】操作日志 `@OperationLog`

- 现状：ruoyi/yudao 标配（AOP 记录操作人/URI/方法/参数/耗时/结果，入库可查）；yarch 只有 request-completed 行（ndjson）。
- 方案：web starter 增加注解 + 切面：默认输出结构化 ndjson（`op`、`action`、`principal`、`costMs`、`result`），提供 `OperationLogStore` SPI（入库实现归业务工程或将来 starter）——不绑死存储，符合横切件分界。
- 工作量：~半天。

### G3【建议顺手对齐】Excel 导入导出版本纳管

- 现状：ruoyi/yudao 靠 EasyExcel 支撑大量管理界面导出；yarch 未纳管版本。
- 方案：yarch-bom 增补 `com.alibaba:easyexcel`（或 `fastexcel`）版本管理 + examples 补一个导出示例。不做成 starter（无架构语义）。
- 工作量：~1 小时。

### G4【触发式已登记，维持】认证与 RBAC

- ruoyi/yudao 开箱即用（用户/角色/菜单/按钮授权/在线用户）；yarch 按 J5 拍板触发式。判断不变：账号/权限模型带业务语义，yarch 只做机制件（`yarch-auth-spring-boot-starter`：JWT 解析 + 2xxx 映射 + 最小 `@RequireRoles`），IAM 需求落地时启动。**缺口确认存在，登记不施工**。

### G5【触发式已登记，维持】定时任务 / MQ / 文件 / ES / 多数据源

- xxl-job、kafka·rocketmq、minio、elasticsearch、dynamic-datasource 均在 PLAN §六 全景表；ruoyi 内置 Quartz 在线管理属管理台体验，xxl-job 契约已选型覆盖。**登记不施工**。

### G6【触发式已登记，维持】熔断/降级（Sentinel/Resilience4j）与分布式事务（Seata）

- coli 全家桶含 Sentinel+Seata；yarch 口径：网关层治理归 higress（契约分工），应用内熔断与分布式事务随微服务化（Nacos 当天）触发。单体 DDD 起步不需要。**登记不施工**。

### G7【明确不对齐】代码生成器（ruoyi/yudao/飞鱼的核心卖点）

- yarch 的同位答案 = **archetype 生成 + AI 施工**（模板管结构、skill 管规范、AI 生成不漂移），将来 `yarch init` CLI。表驱动 CRUD 生成器与"契约可执行化"路线不同，做它会引入第二套结构真理源。**不对齐**；若某业务确有批量 CRUD 需求，由业务工程用 MP 代码生成器局部解决。

### G8【明确不对齐】业务中台模块

- yudao 的工作流（Flowable）、支付（微信/支付宝）、商城、CRM、ERP、IM、微信公众号/小程序、IoT/MQTT、AI 大模型平台、短信/邮件通道——全部是**业务域**不是架构平台；coli 的 Flowable 同理。yarch 铁律：不带业务语义。**不对齐**（各业务工程按需自建，yarch 提供对应的 infra 契约）。

### G9【明确不对齐】多租户 SaaS、数据权限、字典中心、国际化

- 多租户：architecture.md 铁律（归 ysaas）。
- 数据权限（部门/角色数据范围）：强业务语义的查询改写，归业务工程（ysaas-tenant-starter 形态）。
- 字典中心：管理台功能，归业务工程；yarch 契约已定枚举小写序列化即可。
- i18n：2026-09-01 已拍板 v1 不做。
- **不对齐**。

### G10【低优先，部署域登记】系统监控页 / 在线用户 / 缓存监控

- ruoyi 的系统监控页面属管理台 UI；yarch 路线 = actuator（已有）+ Prometheus/Grafana（运维域，deploy 模板将来承接）。**登记不施工**。

## 三、反向优势（我们有、对标对象没有的）

| 能力 | yarch | ruoyi / yudao / coli |
|---|---|---|
| 跨栈统一契约（信封/错误码/日志/traceId） | 24 份规约 + 契约断言机检 | 各自为政 |
| 幂等 | Idempotency-Key 标准语义（回放/1007） | @RepeatSubmit 简单拦截 |
| 深分页 | keyset 游标通道（契约级） | offset 深翻页 |
| 工程规范可执行化 | ArchUnit + 契约断言 + CI 门禁 | 无 |
| 迁移管理 | Flyway 版本化 | 手工 SQL/部分有 |
| 测试 | 容器化契约验收 + AIR/BCDE 基座 | 覆盖率普遍很低 |
| 发版式升级 | parent/BOM/starter 走 version | fork 改码，升级困难 |

## 四、二轮追问：五项能力的自研判定（2026-09-02）

> 用户追问：文档处理（含 Excel）、验证码、IAM、反扒、工作流"是否可以我们自己做"。判定原则：**引擎不造、胶水自研、业务归业务仓、对抗不做**。以下结论细化/取代上文 G3、G4、G8 的对应条目。

### E1 文档处理（Excel）——自研"规范+胶水"，不自研解析器

- Excel 解析引擎（POI 内存模型、xlsx 6000 页规范、公式/样式/SAX 流式）是著名深坑，**不造**；业界正解 FastExcel（EasyExcel 社区延续版）。
- yarch 自研部分：BOM 纳管 FastExcel + web 层"导入导出协议"（导出流式分 sheet 防 OOM；导入错误行回报对齐 1001 信封：`参数校验失败：第 N 行 email …`）+ examples 演示。PDF/Word 同理（PDFBox/poi-tl 按需纳管）。
- 归属：yarch（横切，无业务语义）。工作量 ~1 天。

### E2 验证码——适合自研，yarch 正菜

- 生成 challenge + Redis 存 token（TTL、一次性、失败计数）+ 校验接口 + 与限流联动——与 yarch redis/web 契约天然契合，零业务语义。
- v1 图形/算术码（接口形态对齐业界惯例 `/api/v1/captcha`，返回 key+base64）；v2 可选行为滑块（注意拼图对抗强度有限，定位是挡脚本不是挡黑客）。短信/邮箱 OTP 只做"存储+校验框架"，通道（服务商 SDK）归业务工程。
- 归属：yarch `yarch-captcha-spring-boot-starter`（新）。工作量 1-2 天。

### E3 IAM——拆两层：机制件进 yarch，账号模型归业务仓

- **yarch 层（现在就可做）**：`yarch-auth-spring-boot-starter` = JWT 解析/签发（RS/ES 优先，契约已定）+ 2xxx 错误映射 + `@RequireRoles/@RequirePermissions` 注解拦截——纯机制件。G4 由"触发式"提前。
- **业务层（ysaas 自研，可行）**：用户/组织/角色/菜单/授权的管理模型 + 管理界面，用 auth starter 搭，个人项目规模 2-4 周。**不建议引入 Keycloak**（运维重、多一套身份真理源）；MFA/OAuth2 Server 等强对抗能力待真实需求再议。
- 归属：yarch（机制）+ ysaas（模型）。工作量：yarch 1-2 天；业务侧另计。

### E4 反扒——基线自研，对抗不碰

- **基线层（yarch 自研，性价比高）**：G1 限流（第一道）+ E2 验证码联动（触发阈值后强制人机校验）+ 接口签名（HMAC appKey/appSecret + 时间戳防重放，`@SignedApi` 注解）+ 敏感数据分级出口（脱敏工具）。目标口径：挡住 95% 的脚本访问。
- **对抗层（明确不做）**：设备指纹/行为分析/JS 混淆对抗是军备竞赛，个人项目投入产出比为负；真实风险靠业务设计（数据分级、会员墙、配额）消化。
- 归属：yarch（基线全部横切）。工作量 1-2 天（含 G1）。

### E5 工作流——引擎绝不自研；简单审批用状态机，复杂流程集成 Flowable

- BPMN 执行语义（网关/会签/回退/委派/并行/超时转办/版本迁移）是 Flowable 团队十几年的活，自研简化版必错在边界情况上。**yarch 不做工作流引擎。**
- 分层替代：简单审批 = 领域状态机（examples 的 order.status 已有雏形，可扩"审批链"教学案例）；复杂流程 = 集成 Flowable（coli/yudao 同款）并**立 infra 契约**（flowable.md：状态字段 vs 状态机 vs 流程引擎的三档决策表——这张表本身比引擎更有长期价值）。
- 归属：yarch 立契约（触发式）；examples 可加状态机审批案例（0.5 天）。

### 汇总（建议的"精细打磨第二批"施工清单，合计约一周）

| # | 项 | 归属 | 工作量 | 动作 |
|---|---|---|---|---|
| 1 | G1 限流 `@RateLimited` | yarch/web | 0.5 天 | 新增 |
| 2 | G2 操作日志 `@OperationLog` | yarch/web | 0.5 天 | 新增 |
| 3 | E1 Excel 协议 + FastExcel 纳管 | yarch/bom+web | 1 天 | 新增 |
| 4 | E2 验证码 starter | yarch（新 starter） | 1-2 天 | 新增 |
| 5 | E3 auth 机制件 starter | yarch（新 starter） | 1-2 天 | G4 提前 |
| 6 | E4 接口签名 `@SignedApi` | yarch/web | 0.5-1 天 | 新增 |
| 7 | E5 状态机审批教学案例 | examples | 0.5 天 | 可选 |

## 五、首轮结论（G 表拍板项）

建议本轮施工：**G1 限流 + G2 操作日志 + G3 Excel 纳管**（合计约 1 天，全部平台层横切件、零业务语义）；G4-G6 维持触发式登记；G7-G10 明确不对齐并登记理由（防将来反复讨论）。拍板后随 examples 同步补对应演示与验收用例。

来源：[RuoYi 官方文档](https://doc.ruoyi.vip/ruoyi/document/kslj.html) · [RuoYi GitHub](https://github.com/yangzongzhuan/RuoYi) · [芋道功能列表](https://doc.iocoder.cn/feature/) · [ruoyi-vue-pro](https://github.com/yunaiv/ruoyi-vue-pro)（2026-09-02 检索）；coli-architecture 输入为 2026-09-01 平台文档纪要（内网 submodule 未检出）。
