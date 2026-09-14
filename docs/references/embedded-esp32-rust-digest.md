# 嵌入式 ESP32 Rust 解析（esp-idf-hal std vs esp-hal no_std 选型）

> **定位**：服务于 `embedded/esp32` 规约评审的解析材料。**规范性条文一律以 [../../contract/](../../contract/README.md) 为准**，本文件不承载权威条文。
> 调研日期：2026-09-14；决策清单 **E1-E8** 见文末（唯一登记处 contract/README.md 关键架构决策登记表）。
> 依据：architecture.md 54 行 `embedded/  # 【规划】物理端固件方言：esp32 起步，守 device 领域契约` + contract/README.md microduck 为首个合体验证候选项目——本次触发=用户指令「embedded 开始写，先写出规约再建工程」。

## 一、两条路线的本质区别（选型核心）

Rust on ESP32 有两条官方路线（[Rust on ESP Book](https://esp-rs.github.io/book/)），差异是**运行时模型**而非库 API：

| 维度 | `esp-idf-hal`（std 路线） | `esp-hal`（no_std 路线） |
|---|---|---|
| 底座 | ESP-IDF（乐鑫官方 C 框架）经 Rust 绑定 | 纯 Rust 裸机 HAL |
| 标准库 | 有 std（线程/堆/Vec/网络栈全可用） | 无 std（core + alloc，无 OS） |
| 异步 | FreeRTOS 线程 + 可选 tokio | **embassy**（嵌入式 async 执行器） |
| WiFi/BT | ESP-IDF 原生栈（成熟，WPA2/3、企业认证） | esp-radio（新生态，覆盖基本场景） |
| OTA / 分区表 | ESP-IDF 原生支持（app0/app1 + rollback） | 自行实现或半成品 |
| 日志 | ESP-IDF log 宏（串口） | **defmt**（高效二进制日志，probe-rs 链） |
| 构建 | `espup` 工具链 + `ldproxy` 链接器 | 标准 rustup target（`xtensa-esp32-espidf` 或 riscv32imc） |
| 成熟度 | 0.46.2（2026-03，6 年演进，80 万下载） | 1.2.1（2026-09，1.x 线，发布活跃） |

**判断**：**std 路线（esp-idf-hal）更贴合 yarch 定位**——理由：
1. yarch 嵌入式场景（microduck 桌面机器人）需要 **WiFi + MQTT + OTA + TLS**——这四个全是 ESP-IDF 成熟能力，no_std 路线要么没有要么半成品；
2. `contract/domains/device.md`（规划）要求遥测/OTA——OTA 依赖分区表与回滚，ESP-IDF 原生即得，no_std 需自造轮子；
3. yarch 定位是「工程架构平台」不是「极简资源极致优化」——std 路线开发效率、可调试性、生态完整度远高于 no_std,no_std 的「省内存/硬实时」优势在 microduck 类场景不构成主要约束。

no_std 路线不入册（与 game.md Cocos 默认 + Unity 重度档同构：重度场景将来触发再立）。

## 二、关键生态件（成文时的依赖面）

| 关注点 | 建议栈 | 依据 |
|---|---|---|
| HAL | `esp-idf-hal` 0.46.x（std） | 上文选型 |
| 服务层 | `esp-idf-svc` 0.52.x（WiFi/MQTT/HTTP/NVS/OTA 的 Rust 封装） | 官方配套 |
| 构建工具链 | `espup` + `ldproxy` | 官方安装器 |
| 日志 | ESP-IDF log（`log` crate facade） | std 路线无 defmt 需求 |
| 错误 | anyhow（应用）+ esp-idf 错误码映射 | 对齐全栈错误纪律 |
| 配置 | NVS（ESP-IDF 非易失存储）+ 编译期 env | 设备唯一标识/凭证存 NVS |
| 遥测 | MQTT（esp-idf-svc 的 EspMqttClient），JSON 载荷复用 api/logging-trace 口径 | 对齐 device.md 规划 |
| 测试 | host 端单元测试（std 可跑）+ probe-rs 烧录冒烟 | std 路线可测性优势 |
| 代码质量 | clippy + rustfmt（与云栈同） | 全栈统一 |

## 三、与 device.md 领域契约的关系

`contract/domains/device.md`（规划，未成文）将规定：MQTT topic 约定 / 遥测 schema / OTA 包格式。**embedded/esp32 规约是 device 契约的第一个方言实现方**——按领域契约规则「先立契约评审定稿，再做至少两个语言侧实现」,embedded 规约须等 device.md 成文后才能引用其条文。

**依赖顺序建议**:
1. 先成文 `contract/domains/device.md`(E5 决策，见文末）;
2. 再成文 `embedded/esp32` 规约（引用 device.md 条文 + esp-idf-hal 方言条文）;
3. microduck 合体验证 = 云端 Go（已绿）+ esp32 Rust 固件（本轨）双方言。

## 四、工程形态（参考客户端规约范式）

对偶 `contract/clients/android.md` 的结构范式，embedded/esp32 规约预期章节：

```
一、语言与工具链（Rust + espup + ldproxy，禁手改）
二、构建与工程结构（cargo + esp-idf 组件目录约定）
三、设备身份与配置（NVS 纪律、设备唯一标识 = registry 六节登记对象）
四、网络纪律（WiFi 连接管理、MQTT 会话、TLS 强制——呼应 client-shared 三-2 禁明文）
五、遥测与日志（JSON 载荷复用 logging-trace；traceId 生成规则方言表）
六、OTA（分区表、回滚、签名校验——对齐 device.md）
七、测试（host 单测 + 烧录冒烟分层）
八、基线与兼容性（esp32 系列芯片型号分档：esp32/esp32-c3/esp32-s3）
九、机检与构建（clippy/rustfmt/烧录冒烟 CI）
```

## 五、待评审决策清单（E1-E8）

| # | 决策点 | 建议 | 备注 |
|---|---|---|---|
| E1 | 技术路线 | **esp-idf-hal std 路线**（弃 no_std） | 第一节论证；WiFi/MQTT/OTA/TLS 成熟是决定性因素 |
| E2 | HAL 版本钉法 | `esp-idf-hal = "~0.46"`（次版本锁定，unstable API 护栏） | 对齐 esp-hal 官方建议口径 |
| E3 | 芯片基线 | 首档 esp32（经典款）；esp32-c3/s3 按 microduck 实际硬件触发扩展 | 不做全芯片矩阵（跟 microduck 走） |
| E4 | 工程生成 | `cargo generate` + 模板目录（`embedded/esp32/templates/`） | 与 rust 云栈 R4 同通道，不自研 |
| E5 | device.md 先后 | **先成文 device.md 再成文 embedded/esp32 规约**（领域契约是唯一进门通道） | 见第三节；embedded 规约引用 device 条文 |
| E6 | 发版 | 固件工程不发 crates.io 库；模板随仓走（architecture.md「占位栈不建目录」届时转正） | 客户端库 android/ios 发件模式不适用于固件 |
| E7 | 机检 | clippy + rustfmt + host 单测进 CI（ubuntu + espup 工具链）；烧录冒烟本地手册 | CI 不做真机烧录（无硬件 runner） |
| E8 | 与云栈 rust 的关系 | 独立目录 `embedded/`，与 `stacks/rust` 零共享 crate（运行时模型不同） | 术语表届时同表登记两轨方言名 |

## 六、不在本轨范围

- 云栈 rust（stacks/rust，axum 业务服务）：见 [rust-stack-digest.md](rust-stack-digest.md)；
- no_std 极致优化场景（传感器节点、电池供电硬实时）：不入册，触发式；
- 非 esp32 芯片（STM32/nRF52/RP2040)：不入册，microduck 已定 esp32。

## 来源

- [Rust on ESP Book](https://esp-rs.github.io/book/) / [github.com/esp-rs/esp-hal](https://github.com/esp-rs/esp-hal) / [github.com/esp-rs/esp-idf-hal](https://github.com/esp-rs/esp-idf-hal)
- [crates.io/crates/esp-idf-hal](https://crates.io/crates/esp-idf-hal)（0.46.2，2026-03）/ [esp-idf-svc 0.52.1](https://crates.io/crates/esp-idf-svc)
- [embassy-executor 0.10.0](https://crates.io/crates/embassy-executor)（no_std 路线参考，不入册）
