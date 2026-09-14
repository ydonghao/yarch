# embedded/esp32 固件开发规约（v1.0 已定稿）

> **状态：已定稿**（2026-09-14 经 E1-E8 拍板口径成文）。
> 等级：**【强制】**违反即缺陷；**【推荐】**默认遵守、评审后可豁免；**【参考】**正向引导。
> 前置依赖（硬）：[../../contract/domains/device.md](../../contract/domains/device.md)——topic/遥测/OTA/影子/安全条文以它为准，本文件只写 esp32 + Rust 方言条文。
> 技术路线：**esp-idf-hal std 路线**（E1 拍板；no_std esp-hal 不入册）。调研见 [embedded-esp32-rust-digest.md](../../docs/references/embedded-esp32-rust-digest.md)。
> 约束对象：yarch 体系全部 esp32 固件工程（Rust + ESP-IDF 技术栈）。C/C++ 仅限 ESP-IDF 底层组件，禁新固件业务代码用 C/C++（对齐全域拍板「C/C++ 不设业务栈」）。

## 一、语言与工具链

1. 【强制】Rust 是唯一固件开发语言；工具链由 [espup](https://github.com/esp-rs/espup) 安装（xtensa-esp32-espidf target + ldproxy 链接器），版本在 `rust-toolchain.toml` 钉死——**禁手改 sdkconfig / 链接脚本**（配置即代码，全部入仓）。
2. 【强制】代码风格由 rustfmt 承接（`cargo fmt --check` 进 CI）；禁手排格式。
3. 【强制】lint 由 clippy 承接（`cargo clippy -- -D warnings` 进 CI）；豁免就地 `#[allow(clippy::rule)]` + 注释理由，**禁 crate 级全局 allow**（对齐豁免流程）。

## 二、工程结构与生成

1. 【强制】工程由 `cargo generate --path <模板>` 一行命令生成（E4，对齐全局脚手架铁律）；`cp -r` 既有固件工程当模板不可接受。
2. 【强制】目录定式（模板即此形态）：

```
src/
├── main.rs        # 入口：wifi → mqtt → ota 任务编排，业务模块在此组装
├── identity.rs    # 设备标识 + NVS 凭证（device.md 一）
├── topic.rs       # topic 六段构造器（device.md 二-1）——topic 一律经此构造，禁裸字符串拼接
├── telemetry.rs   # 遥测载荷构造（device.md 三-1 四字段）
├── mqtt.rs        # EspMqttClient 封装（TLS 强制，device.md 六-1）
└── ota.rs         # 双分区 + 回滚 + 验签（device.md 四）
```

3. 【强制】`partitions.csv`（双分区 app0/app1 + ota data + nvs）与 `sdkconfig.defaults` 入仓；构建产物（`target/`）不入仓。
4. 【强制】芯片型号在 `Cargo.toml` feature 或模板变量声明（E3 首档 esp32）；同一固件工程服务单一芯片型号，跨型号走模板分档（触发式）。

## 三、设备身份与配置（device.md 一的方言落地）

1. 【强制】设备标识 `{product}/{device}` 经 `identity.rs` 单点读取：product 编译期注入（模板变量/env），device 从 NVS 读（出厂烧录）。
2. 【强制】凭证（TLS 私钥 / MQTT token）存 NVS/flash 加密区；**禁硬编码进源码或镜像**（device.md 一-4）——固件镜像仅含产品族公钥（OTA 验签用）。
3. 【强制】环境（dev/staging/prod）经编译期 feature 注入（device.md 二-1 env 前缀单点）；**禁运行时字符串拼接环境**。

## 四、网络纪律（呼应 client-shared 三）

1. 【强制】WiFi 连接管理单点（重连退避、掉线事件入遥测）；业务模块不直接操作 WiFi 驱动。
2. 【强制】MQTT 一律 TLS（8883 端口 + 双向证书，device.md 六-1）；**禁 1883 明文**（构建期断言，七表）。
3. 【强制】MQTT 会话封装在 `mqtt.rs`：订阅只挂 `cmd/+`（device.md 二-3），发布经 `topic.rs` 构造；QoS 按 device.md 二-4 表。
4. 【推荐】TLS 证书校验用 ESP-IDF 内置 bundle 或显式 CA 钉入；跳过证书校验（`insecure`）仅限 dev 环境且须评审登记。

## 五、遥测与日志（device.md 三的方言落地）

1. 【强制】遥测载荷经 `telemetry.rs` 构造（四字段：ts/traceId/metrics/fw）；**禁业务代码手工拼 JSON 上报**。
2. 【强制】traceId 32 hex 设备侧本地生成（device.md 三-2），贯穿设备→云→日志链；日志输出对齐 ESP-IDF log 宏（`log::info!` 等），关键事件携带 traceId 字段。
3. 【推荐】上报周期默认 30s；高频（<5s）走容量评审（device.md 三-4）。

## 六、OTA（device.md 四的方言落地）

1. 【强制】双分区 + 回滚用 ESP-IDF `esp_ota_ops` 原生语义：新固件首启 `esp_ota_mark_app_valid_cancel_rollback()` 须在自检通过后调用；自检失败自动回滚。
2. 【强制】固件包验签（manifest+payload 签名，产品族公钥）在写入 ota 分区前完成；验签失败不写入。
3. 【强制】版本比较单调递增（manifest version ≤ 当前 version 拒刷，device.md 四-3）。
4. 【强制】升级进度经 `ota/progress` 上报（device.md 二-1 表）。

## 七、影子与命令（device.md 五的方言落地）

1. 【强制】影子状态（`state/report`）在启动连通后与关键状态变更时上报；命令回执发 `resp/{verb}` 并原样回带 `requestId`。
2. 【强制】命令幂等去重（device.md 五-3）：`requestId` 进设备侧去重缓存（环形缓冲或 NVS，TTL ≥ 命令超时），重复到达只执行一次。
3. 【强制】命令处理失败回执 `code` 非 0 + `message`；设备业务错误码 3xxx+ 段位在业务仓登记（device.md 五-2）。

## 八、测试

1. 【强制】host 端单元测试（`cargo test --target x86_64-unknown-linux-gnu` 或 macOS 等价）：topic 构造器 / 遥测 schema / 命令幂等去重 / OTA 版本比较——全部纯逻辑下沉 host 可测层。
2. 【强制】device.md 七表可机检条目全部有对应 host 单测（本文件各章「经 xx.rs 构造」即该模块的单测锚点）。
3. 【强制】硬件相关代码经 trait 抽象（WiFi/MQTT/OTA 端口），host 测试用 mock 实现——**禁业务逻辑直接调 esp-idf API**（可测性前提，对齐 client-shared 可测性分层）。
4. 【推荐】烧录冒烟（probe-rs / espflash）本地手册化（步骤入模板 README）；CI 不做真机烧录（E7，无硬件 runner）。

## 九、机检与构建

1. 【强制】CI（ubuntu runner）三段全绿是合并门槛：

| 段 | 命令 | 职责 |
|---|---|---|
| fmt | `cargo fmt --check` | 格式 |
| lint | `cargo clippy -- -D warnings` | 代码味 |
| check/test | `cargo check --target xtensa-esp32-espidf` + `cargo test`（host） | 交叉编译可行性 + 逻辑断言 |

2. 【强制】`espup` 工具链版本与 `rust-toolchain.toml` 一致进 CI（版本漂移即构建失败）。
3. 【强制】构建期断言：sdkconfig 无 `CONFIG_ESPTOOLPY_FLASHMODE` 明文 WiFi/MQTT 非 TLS 配置残留（1883 端口）——脚本扫描进 CI。

## 十、可机检条文清单

> 对齐 [../../contract/README.md](../../contract/README.md)「机检路线」。落地载体：模板 CI + host 单测。

| 条文 | 机检手段 | 阶段 |
|---|---|---|
| 一-1 工具链钉版 | rust-toolchain.toml 存在性 + CI 版本核对 | CI |
| 二-1 一行命令生成 | cargo generate 冒烟（生成 → check） | CI |
| 三-2 凭证禁硬编码 | 字面量扫描（私钥/token 模式） | CI |
| 四-2 禁 1883 明文 | sdkconfig/源码扫描 | CI |
| 五-1 遥测四字段 | telemetry.rs host 单测 | 单测 |
| 六-3 版本单调 | ota.rs host 单测（版本比较用例） | 单测 |
| 七-2 命令幂等 | 去重缓存 host 单测（重放用例） | 单测 |
| 八-3 硬件经 trait 抽象 | 依赖解析：业务模块禁直接 `use esp_idf_hal` | CI |

---

## 附：来源与拍板记录

- 技术路线 esp-idf-hal std（E1）：WiFi/MQTT/OTA/TLS 走 ESP-IDF 原生成熟栈；no_std esp-hal（embassy/defmt 生态）为极致资源场景保留，microduck 类场景不构成主要约束，不入册。逐项论证见 [embedded-esp32-rust-digest.md](../../docs/references/embedded-esp32-rust-digest.md)。
- 拍板口径（E1-E8，2026-09-14 用户指令「开始动工」按建议落地）：std 路线 / HAL 次版本锁定 / 首档 esp32 / cargo-generate 模板 / device.md 先行（已成文）/ 固件不发库 / clippy+rustfmt+host 单测 CI / 与云栈 rust 零共享。
- 与 [clients/](../../clients/README.md) 的关系：clients 是交互端（人用的 App），embedded 是物理端（设备固件）——同守 client-shared 网络纪律哲学（超时/禁明文/取消传导），但传输层是 MQTT 非 HTTP，故独立成规约引用 device.md 而非 client-shared。
