# 契约 · 验证码框架（Captcha · Provider SPI 与消费面）（v1.0 已定稿）

> **状态：已定稿**（2026-09-18 经 CP1-CP10 决策清单拍板；拍板口径见 [../README.md](../README.md) 关键架构决策登记表 CP 行）。
> 等级：**【强制】**违反即缺陷；**【推荐】**默认遵守、评审后可豁免；**【参考】**正向引导。
> 分工：本规约管**人机校验挑战**（challenge 生成/分发/校验/防重放）；登录态与凭证错误归 [error-codes.md](error-codes.md) `2xxx`；存储 key 纪律归 [../infra/redis.md](../infra/redis.md)；REST 信封归 [rest-response.md](rest-response.md)。

## 一、形态与职责（CP2）

1. 【强制】形态 = **Provider SPI + 框架核心**（进程内组件，非独立服务）：渠道差异（图形/OTP/第三方）是框架内多态，不构成服务边界（无独立数据域、无独立伸缩诉求）。
2. 【强制】职责分界：

| 归属 | 职责 |
|---|---|
| 框架核心（与 Provider 无关） | challenge 生命周期（key 生成/存储/TTL/一次性原子消费）、场景路由（四）、生成端限流联动（五-1）、错误语义统一（五-2） |
| Provider（SPI 实现） | 生成挑战（答案 + 分发载体，或透出接入配置）、答案比对规则（本地型）/ 远程校验（远程型） |

3. 【强制】Provider 标识（`id`）kebab-case，系统保留字：`image` / `sms-otp` / `turnstile`；业务自定义 Provider 禁占用保留字（注册表哲学同 [realtime.md](realtime.md) 三-1）。

## 二、Provider 行为契约（CP3）

1. 【强制】Provider 必须声明校验模式，二选一：
   - **LOCAL（本地校验型）**：答案由框架核心托管进 Redis，一次性原子消费（三）；Provider 只产出答案与分发载体、给出比对规则。
   - **REMOTE（远程校验型）**：无服务端答案存储；Provider 透出接入配置（如 `siteKey`）并执行远程校验调用；一次性语义由上游保证。
2. 【强制】`image` 档（LOCAL，默认档）：答案 = 4 位字符，字符集固定为去混淆 32 字符 `ABCDEFGHJKLMNPQRSTUVWXYZ23456789`（去 `I O 0 1`）；比对 = 首尾空白 trim + 大小写不敏感；分发载体 = PNG 裸 base64（**无 `data:` 前缀**，前端自行拼装 dataURL——存量字段形态不动）。渲染样式（噪线/颜色/尺寸）实现自由（CP9：绑各栈图形库能力，不入契约）。
3. 【强制】`sms-otp` 档（LOCAL）：答案 = 6 位数字 OTP（安全随机源）；比对 = 精确匹配（禁宽松比对）；分发 = **发送通道由宿主注入**（`SmsSender`/Notifier SPI）——yarch 不背通知渠道抽象（通知领域契约排队中，EP7，届时对齐）；发送目标（手机号）回显必须脱敏。
4. 【强制】`turnstile` 档（REMOTE）：分发 = `siteKey` 配置透出（前端嵌 widget 自取 token）；校验 = 上游 siteverify 接口（token + secret + remoteip）；服务端不复存 token、禁将 token 落 Redis。
5. 【参考】触发档（首个消费者出现时增补条文）：滑块/行为验证、邮箱 OTP、其他第三方人工验证（reCAPTCHA/hCaptcha 等）。

## 三、challenge 生命周期（CP6/CP7）

1. 【强制】key 由服务端安全随机生成（≥128 bit 熵，UUIDv4 同级及以上），**禁业务可预测值**（用户名/时间戳派生等）。
2. 【推荐】TTL 默认 **120s**、应用可配；OTP 档可放宽至 300s。
3. 【强制】**一次性语义**：对/错/异常均消费该 key（防重放与枚举）——校验失败后同 key 二次提交必须失败。
4. 【强制】**原子消费**：取答案与删除必须原子（`GETDEL`；实例 < Redis 6.2 时用等价 Lua `GET+DEL`）——两步 get→delete 是缺陷（并发双 verify 竞态双双通过）。
5. 【强制】存储 key 遵循 [../infra/redis.md](../infra/redis.md)：`{服务名}:captcha:{provider}:{key}`（首段 = 服务名，共享实例租户边界）；REMOTE 型不产生存储 key。

## 四、场景路由（CP4）

1. 【强制】场景标识（`scene`）为自由 kebab-case 字符串，宿主自定——yarch 不拥有业务场景枚举（同 3xxx-8xxx 治理哲学）；【参考】常用：`login` / `register` / `reset-password`。
2. 【强制】路由配置模型 = **默认 Provider（缺省 `image`）+ scene→Provider 覆盖映射**；未配置的 scene 一律走默认 Provider（不报错）。
3. 【参考】租户级覆盖是宿主应用层扩展位：框架配置面到应用级为止，租户维度的覆盖映射由宿主实现（如查租户配置表后显式指定 Provider）。

## 五、限流与错误（CP5/CP8）

1. 【强制】生成/分发端点默认挂限流，超限报 `1006 RATE_LIMITED`（复用 [error-codes.md](error-codes.md) 通用段与各栈限流件）；【参考】默认阈值 per-IP 固定窗口 60s/10 次。
2. 【强制】本规约唯一新增错误码 **`2005 CAPTCHA_INVALID`（HTTP 400）**：key 无效 / 已消费 / 答案错误**三态合一**，禁细分（细分即向探测方泄露 key 状态）；其余一律复用既有码（限流 1006、参数缺失 1001）。
3. 【推荐】`sms-otp` 的分发按**发送目标**（手机号）维度追加限流（防短信轰炸），阈值宿主定。

## 六、REST 消费面（CP8）

1. 【强制】`GET /api/v1/captcha`（image 默认档；`scene` 可选 query 参数，见四）→ `RestResponse<Challenge>`；Challenge 形状：

| 字段 | 类型 | 语义 |
|---|---|---|
| `provider` | string | Provider 标识（增量字段，存量消费者可忽略） |
| `key` | string | challenge key（提交回传） |
| `imageBase64` | string | PNG 裸 base64（无 `data:` 前缀，前端自行拼装；image 档专属） |

   存量字段 `key` / `imageBase64` 名字与语义不变（存量前端零动作，CP10）。
2. 【强制】**verify 不设独立端点**：校验内联在受保护业务流（login/register 等）的参数对 `captchaKey` / `captchaAnswer`（camelCase，D3 同源）中，由业务端点调框架核心完成——独立 verify 端点会成为探测面。
3. 【强制】REMOTE 型内联参数：`captchaAnswer` = 上游 token（`captchaKey` 省略）。
4. 【参考】`sms-otp` / `turnstile` 的 HTTP 分发端点为**触发档**（编程式调用首发即可用）；触发时增补条文（如 `POST /api/v1/captcha/sms-otp`，含目标脱敏与发送限流）。

## 七、可机检条文清单

> 对齐 [../README.md](../README.md)「机检路线」。落地载体为各栈装配件（实现触发式登记，首批范围见附录）。

| 条文 | 机检手段 | 阶段 |
|---|---|---|
| 二-2 字符集与长度 | 装配件单测（生成答案白名单断言） | 单测 |
| 三-3/三-4 一次性与原子消费 | conformance 向量 V2/V3/V6（见附录） | 单测 |
| 三-5 存储 key 形状 | 装配件单测（key 前缀断言 `服务名:captcha:`） | 单测 |
| 五-2 错误码三态合一 | 四栈 conformance（随 [../dist/error-codes.json](../dist/error-codes.json) 同源断言 2005） | 单测 |
| 六-1 端点与 Challenge 字段 | 装配件端点测试（字段恒在） | 单测 |

## 附：跨栈一致性测试向量（conformance 同源）

> 各栈实现须以同源向量断言行为一致（EP5 HMAC 跨栈验收同款）；java 首批落地，golang/python 触发时同源引用。

| # | 向量 | 期望 |
|---|---|---|
| V1 | issue(image) 后以正确答案 verify | `true`，且同 key 立即失效 |
| V2 | V1 后同 key 以同答案再 verify | `false`（一次性） |
| V3 | 以错误答案 verify | `false`，且 key 已消费（防重放枚举） |
| V4 | 以不存在/过期 key verify | `false` |
| V5 | 答案带首尾空白且大小写不同（image） | 命中（trim + 大小写不敏感） |
| V6 | 并发两个 verify 同 key | 恰一个 `true`（原子消费） |
| V7 | issue 后查存储 TTL | `0 < TTL ≤ 120`（默认档） |
| V8 | sms-otp：issue(目标) → SmsSender 收 6 位数字；错一位 verify | 发送可达；`false` 且已消费 |
| V9 | turnstile：注入 stub 校验端点，通过/失败两分支 | 与 stub 一致；全程零 Redis 存储 key |
| V10 | 场景路由：配置 `scenes` 映射 + 未配置 scene | 映射生效 / 走默认 Provider |
| V11 | 生成端点同 IP 超限流阈值 | `1006 RATE_LIMITED` |

## 附：来源与拍板记录

- 拍板记录（唯一登记处：[../README.md](../README.md) 关键架构决策登记表 CP 行，此处为引用）：2026-09-18 CP1-CP10——独立成文 `contract/api/captcha.md`；SPI + 框架核心形态；**首发三 Provider（image/sms-otp/turnstile）**，OTP 分发通道宿主注入；**框架内置场景路由**（默认 + 覆盖映射，租户覆盖归应用层扩展位）；2005 单码三态合一 + 限流复用 1006；TTL 120s 推荐 + 一次性强制；GETDEL 原子消费（java 存量 get→delete 两步视为缺陷随重构修复）；`GET /api/v1/captcha` + verify 内联业务流 + 存量字段不动；字符集/长度入契约、渲染样式自由；java 首批实现、golang/python 触发档。
- 现状依据：java `yarch-captcha-spring-boot-starter`（图形码 + Redis 一次性 token，2026-09-02 第二批）为存量基准。
- 实现节奏：java 首批已落地（SPI 重构 + 三 Provider + 向量测试）；golang 已对齐（SPI 化 + 场景路由 + 五处漂移清偿：长度/TTL 默认/端点字段/存储 key provider 段/失败码 2005）；python 首批已落地（`yarch_python.captcha` 同源 SPI + 纯 stdlib PNG 渲染）；web/客户端零动作（消费字段不变）。
