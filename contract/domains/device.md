# device · 设备接入领域契约（v1.0 已定稿）

> **状态：已定稿**（2026-09-14 经 Dv1-Dv6 拍板口径成文，决策清单见 [embedded-esp32-rust-digest.md](../../docs/references/embedded-esp32-rust-digest.md) E 系）。
> 等级：**【强制】**违反即缺陷；**【推荐】**默认遵守、评审后可豁免；**【参考】**正向引导。
> 约束对象：yarch 体系全部「物理端设备」与「云端接入侧」双方——设备（embedded 固件）与云端接入服务（stacks 业务栈）同表实现，单一实现不成领域。
> 协作参考（软）：[api/logging-trace.md](../api/logging-trace.md)（JSON 口径）、[registry.md](../registry.md)（设备名/产品族登记）、[infra/redis.md](../infra/redis.md)（影子缓存 key 前缀）。

## 一、设备身份与登记

1. 【强制】设备身份两段：**产品族**（product）+ **设备名**（device），拼成全局唯一设备标识 `{product}/{device}`（MQTT topic 首两段）。
2. 【强制】产品族格式同服务名（registry.md 一-1：小写短横线）；设备名格式 `^[a-z][a-z0-9-]{1,31}$`，由「产品族-序号/用途」构成（正例 `microduck-01`）。
3. 【强制】设备标识一经烧录/注册即冻结（证书、影子、遥测历史都锚定其上）；改名 = 新设备登记 + 旧登记退役。
4. 【强制】设备凭证（TLS 私钥 / 接入 token）**禁入固件镜像**——出厂烧录进设备安全存储（ESP32 NVS/flash 加密区），镜像内只含产品族公钥。

## 二、MQTT topic 约定

1. 【强制】topic 六段定式：`{env}/{product}/{device}/{channel}/{verb}[/{qualifier}]`，全小写短横线；`env ∈ {dev, staging, prod}`，环境前缀单点（呼应 client-shared 三-5 环境注入）。

| channel | 方向 | 语义 | 例 |
|---|---|---|---|
| `telemetry` | 设备→云 | 遥测上报（周期/触发） | `prod/microduck/microduck-01/telemetry/report` |
| `state` | 设备→云 | 影子状态上报 | `prod/microduck/microduck-01/state/report` |
| `cmd` | 云→设备 | 下行命令 | `prod/microduck/microduck-01/cmd/reboot` |
| `resp` | 设备→云 | 命令回执（对齐 REST 信封） | `prod/microduck/microduck-01/resp/reboot` |
| `ota` | 双向 | 固件升级通道 | `…/ota/notify`（云→设备）/ `…/ota/progress`（设备→云） |

2. 【强制】`cmd` 与 `resp` 以 `verb` 一一对应（`cmd/reboot` 的应答发 `resp/reboot`）；命令携带 `requestId`（ULID 或 UUID v4），回执原样回带——异步配对唯一键。
3. 【强制】通配订阅只允许在 `cmd/+` 级（设备订阅自身全部命令）；禁 `#` 多级通配（生产事故面）。
4. 【推荐】QoS：telemetry 0（可丢）、state 1（至少一次，影子须终态收敛）、cmd/resp 1、ota 1；QoS 2 仅计费级场景经评审引入。

## 三、遥测 schema

1. 【强制】遥测载荷 JSON 单行（ndjson 口径，复用 api/logging-trace 三-1 字段哲学）：

```json
{ "ts": "2026-09-14T08:00:00Z", "traceId": "0af7651916cd43dd8448eb211c80319c", "metrics": { "battery": 87, "temperature": 36.5 }, "fw": "1.2.0" }
```

2. 【强制】字段定式：`ts`（ISO-8601 UTC，对齐 API D4）、`traceId`（32 hex，对齐 API D2；设备侧可本地生成，贯穿设备→云→日志链）、`metrics`（业务自定义键值）、`fw`（固件版本）。
3. 【强制】遥测是**观察数据**不是状态事实——设备当前状态以 `state`（影子）为准，遥测历史供分析（OLAP 层消费，入 ClickHouse 走 kafka 管道）。
4. 【推荐】上报周期默认 30s；高频遥测（<5s）须经容量评审。

## 四、OTA 包格式与升级

1. 【强制】固件包三段：manifest（JSON，版本/目标产品族/SHA-256/签名）+ payload（固件二进制）+ signature（manifest+payload 的 Ed25519/ECDSA 签名，产品族公钥验签）。
2. 【强制】双分区（app0/app1）+ 回滚：新固件首启自检失败自动回滚旧分区（ESP-IDF `esp_ota_ops` 原生语义）；升级进度经 `ota/progress` 上报。
3. 【强制】版本号语义化（`MAJOR.MINOR.PATCH`）；同版本禁重复刷入（manifest version ≤ 当前 version 即拒）。
4. 【推荐】差分升级（bsdiff）为优化项，v1 全量包即可。

## 五、影子（state）与命令回执

1. 【强制】设备影子 = 云侧缓存的「设备最新自述状态」（Redis key `{product}:shadow:{device}`，TTL 按产品族定）；设备上下线以影子 last-seen 判活。
2. 【强制】命令回执信封对齐 REST 四字段哲学（code/message/data/traceId 映射为 code/message/result/requestId）——`code` 0 成功、非 0 失败，错误码段位复用 api/error-codes 段位语义（设备业务错 3xxx+ 段位在业务仓登记）。
3. 【强制】命令幂等：同一 `requestId` 重复到达只执行一次（设备侧去重缓存，TTL ≥ 命令超时时长）。

## 六、安全

1. 【强制】传输 TLS 强制（对齐 client-shared 三-4 禁明文）；MQTT 8883 端口 + 双向证书（设备证书 + 服务端证书）。
2. 【强制】设备凭证粒度=一设备一证书；吊销以证书黑名单/到期为准。
3. 【推荐】命令通道（cmd）鉴权在云端接入层完成（签发方验签），设备侧只验 TLS 与 topic 归属。

## 七、可机检条文清单

> 对齐 [../README.md](../README.md)「机检路线」。落地载体：云端接入侧单测 + 固件侧 host 单测 + CI 冒烟。

| 条文 | 机检手段 | 阶段 |
|---|---|---|
| 一-1 设备标识两段 | 云端接入层解析器单测（非法 topic 拒） | 单测 |
| 二-1 topic 六段定式 | topic 构造器单测 + 静态扫描禁裸字符串拼接 topic | CI |
| 二-3 禁 `#` 通配 | 订阅配置扫描 | CI |
| 三-1 遥测四字段 | 载荷 schema 校验（JSON Schema，对齐 contract/dist 远期） | 单测 |
| 四-2 回滚语义 | 固件侧自检失败路径单测（mock ota partition） | 单测 |
| 五-3 命令幂等 | 同 requestId 重放用例（设备侧去重断言） | 单测 |
| 六-1 TLS 强制 | 构建期断言禁 1883 明文端口配置 | CI |

---

## 附：拍板记录

- 拍板口径（Dv1-Dv6，2026-09-14 用户指令「开始动工」按建议落地，正式登记见 [../README.md](../README.md) 关键架构决策登记表）：
  - Dv1 设备标识 = `{product}/{device}` 两段；Dv2 topic 六段定式（env 前缀单点）；Dv3 遥测 ndjson 复用 logging-trace 口径 + traceId 贯穿；Dv4 OTA 双分区+回滚+签名（ESP-IDF 原生语义）；Dv5 影子 = 云侧 Redis 缓存 + 命令回执对齐信封哲学；Dv6 TLS 强制 + 一设备一证书。
- 首个双方言验证项目：microduck（云端 Go 接入侧已绿 + esp32 Rust 固件侧，见 [embedded/esp32/PLAN.md](../../embedded/esp32/PLAN.md)）。
