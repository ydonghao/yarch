# 企业级能力缺口解析（应用 / 游戏，2026-09-16）

> **定位**：固化 2026-09-16「面向企业级的应用/游戏，yarch 缺什么」的缺口分析，服务于**定位修订拍板**。**规范性条文一律以 [../../contract/](../../contract/README.md) 为准**，本文不承载权威条文。**EP1-EP12 决策清单在文末**，拍板后修订 [../development-roadmap.md](../development-roadmap.md) 定位节并插入新轨。
> 姊妹篇：[realtime-channel-digest.md](realtime-channel-digest.md)（缺口二的第一优先立项，已随批出调研与 RT 决策清单）。

## 一、一句话结论

yarch 现在是一台**「代码生成 + 契约机检」很强的横切工程底座**（29 份规约、四云栈 + 五端模板一行命令可起、测试基座是 ruoyi/yudao/coli 的反向优势）；面向企业级，缺口集中在三条线——**①从代码到运营**（可观测、部署、容灾几乎为零）、**②游戏的实时与生态面**（只有 HTTP 请求-响应范式）、**③企业采购合规面**（多租户、i18n、审计留存已拍板「不做」，新定位下需重审）——外加一个元缺口：**整套体系尚未被真实生产负载验证过**（registry 26 行登记，唯一消费者 microduck 未跑通）。

## 二、盘面（缺口讨论的对照基线）

| 已有 | 锚点 |
|---|---|
| 29 份 v1.0 契约：api 四件套 / infra 20 / web 微前端 / clients 五份 / domains·device / agent 三份 | [contract/](../../contract/) |
| 四云栈 + starter（含 captcha、auth、限流、幂等、操作日志——G1/G2 已清） | stacks/java 16 模块 · stacks/golang middleware 包 |
| 五端契约内核 + 模板 + 生成器（android/ios/miniprogram/game-cocos） | clients/ |
| 契约断言机检 + 测试基座（同类反向优势） | 各栈 conformance 测试 |
| AI 施工规约层 + 机器可读出口主线（P1 待施工） | contract/agent/ · roadmap 支柱 1 |

## 三、缺口一：从代码到运营（企业级第一显性缺口）

- **可观测三件套只有一件**：ndjson 日志 + traceId 贯穿已有；**metrics（Prometheus 口径）、分布式 tracing（OTel）、告警**全缺，infra 20 份无 prometheus / grafana / otel 相关契约。
- **deploy 轨不存在**：higress/nginx/nacos 契约有，但 G10 拍板的「运维域 deploy 模板将来承接」至今是空头支票——无 k8s/helm、无 CI 到部署的通路、无 dev/staging/prod 多环境分层、无密钥管理、无备份恢复口径。B 端交付第一问就是「怎么部署、怎么容灾」。
- **CI 无安全扫描**：现为 build + test + template-smoke，CodeQL / trivy / Dependabot 类依赖与代码扫描 job 缺位。

## 四、缺口二：游戏实时与生态面（结构性缺口）

- **api/ 只有 REST 四件套**，无长连接推送通道契约（WebSocket 握手鉴权 / 心跳 / 断线重连 / 消息信封 / 离线补投）。当前五端契约内核全是请求-响应范式，一旦立 realtime 契约五端内核都要扩展——**越晚立越贵**。详见姊妹篇 [realtime-channel-digest.md](realtime-channel-digest.md)。
- **埋点与数据上报管道缺**：clickhouse/starrocks/timescale 契约空有基础设施，无客户端埋点 SDK、无上报协议、无 CDC（debezium/canal 类）契约。游戏是运营驱动的生意，这是企业级游戏的命脉。
- **客户端生态件缺**：热更新与资源/AB 包管理（cocos 小游戏基本操作）、微信登录/支付 SDK 协议层（code2session、支付回调验签——纯协议机制件，符合「机制归平台」铁律）。
- **游戏后端领域件无契约**：房间/匹配、排行榜（redis zset 基础设施有、契约无）、GM 指令通道、活动配置。

## 五、缺口三：企业采购合规面

已落地：captcha、auth（JWT + 2xxx）、限流、幂等、操作日志，以及接口签名 `@SignedApi` + 脱敏 Masks（java 栈，2026-09-02 精细打磨第二批）。还缺：

- **审计留存口径**：操作日志现在只出 ndjson + SPI（`OperationLogStore`），无入库/保留期/不可篡改叙事；PII 脱敏与数据分级出口无契约。toB 采购必问，目前空白。
- **已拍板「不做」项需重审**：多租户（G9 归 ysaas）、字典中心/监控页（G9/G10）、i18n（v1 不做）——在「个人项目/架构平台」定位下是对的；「企业级」是新的定位输入，toB SaaS 场景多租户与 i18n 几乎必问。**不直接翻案，先拍边界**。

## 六、薄处点名（维持触发式合理，登记备查）

| 薄处 | 现状 | 建议 |
|---|---|---|
| 通知通道（APNs/FCM/短信/邮件） | E2 拍过「存储校验框架归 yarch、通道 SDK 归业务」，无契约 | 领域契约排队（EP7） |
| 支付协议层（回调验签/幂等/对账） | 无 | 触发式登记（EP8） |
| 桌面端 | clients/desktop 仅 README 占位 | 触发式登记（EP10） |
| 微服务轨（nacos/sentinel/seata 栈层集成件） | 契约已立、栈层无集成件，单体 DDD 起步拍板未变 | 维持触发式 |
| rust / embedded-esp32 | 规约已立待施工 | 已在 roadmap，非新缺口 |
| OpenAPI 导出（N5） | 远期挂档 | 企业级对外集成可能催提前（EP12） |

## EP 决策清单（2026-09-16，待拍板）

| # | 议题 | 选项 | 建议 |
|---|---|---|---|
| EP1 | 定位表述如何吸收「企业级」 | (a) 场景定语：定位句不动，roadmap 定位节补「面向企业级应用/游戏交付」目标场景；(b) 重写定位句；(c) 只调优先级不动文档 | **(a)**——护城河判断（契约底座）不受场景影响，场景只改优先级 |
| EP2 | 多租户 | (a) 维持 G9 不做；(b) 拆两层：立**机制契约**（租户上下文传播：header → MDC → 日志字段 → trace 字段 + 隔离三档决策表 schema/库/行级），模型归业务仓；(c) 完整多租户契约 | **(b)**——上下文传播是横切机制（同 E3 拆层先例），模型仍是业务 |
| EP3 | i18n | (a) 维持 v1 不做；(b) 最小就绪位：错误码表加**稳定 key** + Accept-Language 透传口径，多语言文案不做；(c) 完整 i18n 契约（文案 catalog + 各端框架） | **(b)**——与支柱 1 error-codes.json 天然衔接，加 key 近零成本 |
| EP4 | 审计留存 | (a) 并入可观测轨一次立项（最小审计契约：事件面/保留期/只追加）；(b) 触发式；(c) 不做 | **(a)**——合规可观测本是一体 |
| EP5 | 接口签名/脱敏的跨栈对齐（java 已有 `@SignedApi`+Masks，golang/python 无同位件） | (a) 小施工清偿 golang/python 侧（约 1 天）；(b) 触发式（哪栈接入开放 API 哪栈补） | **(a)**——E4（2026-09-02）即建议过，企业开放 API 刚需，跨栈同位缺口语义上是「实现与契约不一致」的轻度变体 |
| EP6 | CI 安全扫描 | (a) 立即加 Dependabot + CodeQL/trivy job（纯 CI 零契约）；(b) 随可观测轨 | **(a)**——成本最低的企业级信号 |
| EP7 | 通知通道领域契约 `domains/notification.md` | (a) 排队（realtime/可观测/埋点三轨之后）；(b) 触发式登记；(c) 不做 | **(a)** |
| EP8 | 支付协议层契约（回调验签/幂等/对账） | (a) 触发式登记（首个带支付的企业项目触发）；(b) 排队；(c) 不做 | **(a)**——协议是机制件，但强绑定微信/支付宝生态细节，等真实需求定型 |
| EP9 | 新三轨排序 | (a) realtime > 可观测（含审计） > 埋点管道；(b) 可观测 > realtime > 埋点；(c) 自定 | **(a)**——realtime 拖得越久五端内核改造成本越大；可观测/埋点不受实现形态牵连 |
| EP10 | 桌面端（Electron/Tauri） | (a) 触发式登记（registry/README 一行）；(b) 排队施工；(c) 删除占位 | **(a)** |
| EP11 | 试点策略 | (a) microduck 照 roadmap P2 跑 device；realtime 契约成文后立**游戏试点**消费 realtime + 埋点契约；(b) 全部契约齐再试点 | **(a)**——契约信用最终来自生产负载，别等齐 |
| EP12 | OpenAPI 导出（N5） | (a) 维持 P3 视支柱 1 进展；(b) 提前并入 P1 | **(a)**——先看 error-codes.json 落地成本再定 |

## 拍板后动作

1. 修订 [../development-roadmap.md](../development-roadmap.md)：定位节补场景定语；路线图插入新轨（按 EP9 序），运维/可观测轨合并 EP4 审计；
2. 各新轨按机检路线推进：digest → 决策清单 → 拍板 → 条文成文 → contract/README 决策表登记；
3. EP5/EP6 属施工项非契约项，拍板即排入最近施工批。

## 来源

仓内锚点：[contract/](../../contract/)（29 份规约现状）· [registry.md](../../contract/registry.md)（26 行登记）· [ruoyi-yudao-coli-gap-digest.md](ruoyi-yudao-coli-gap-digest.md)（G1-G10 / E1-E5 既有拍板）· [market-landscape-digest.md](market-landscape-digest.md)（N1-N5）· [miniprogram-game-digest.md](miniprogram-game-digest.md)（MP/G 既有拍板）· [../development-roadmap.md](../development-roadmap.md)（四支柱与「不做什么」清单）。缺口分析本体来自 2026-09-16 会话（用户输入：「面向企业级的应用/游戏」）。
