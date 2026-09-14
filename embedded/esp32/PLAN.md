# embedded/esp32/ · 固件施工计划

> **状态：规约已立（spec.md v1.0），工程未动工**——按「规约先行」铁律，本文件是施工入口。
> 决策清单 E1-E8（拍板口径 2026-09-14，唯一登记处 [../contract/README.md](../contract/README.md) 关键架构决策登记表；调研见 [../docs/references/embedded-esp32-rust-digest.md](../docs/references/embedded-esp32-rust-digest.md)）。
> 前置依赖（硬）：[../contract/domains/device.md](../contract/domains/device.md)（本规约引用其 topic/遥测/OTA 条文）。

## 一、既定约束（来自规约与拍板，本计划不再议）

1. 技术路线：**esp-idf-hal std 路线**（E1；WiFi/MQTT/OTA/TLS 走 ESP-IDF 原生，no_std esp-hal 不入册）。
2. 守 [../contract/domains/device.md](../contract/domains/device.md)：topic 六段定式 / 遥测 ndjson 四字段 / OTA 双分区回滚 / 影子 + 命令回执信封哲学。
3. 与 `stacks/rust` 云栈**零共享 crate**（E8：tokio 全功能 vs esp-idf std，运行时模型不同）。
4. 一行命令建工程（全局脚手架铁律）：`cargo generate` + 模板目录（E4，生态标准通道，不自研渲染引擎）。

## 二、与既有栈范式对齐

| 维度 | java | golang | python | web/clients | **embedded/esp32（本轨）** |
|---|---|---|---|---|---|
| 工程形态 | Maven reactor | 单 module 多 package | uv workspace | pnpm/SPM | **cargo 单 crate（固件二进制）** |
| 构建 | mvn | go build | uv build | vite/gradle | **cargo build（espup 工具链 + ldproxy）** |
| 模板资产 | archetype（Velocity） | `_template/`+archetype.json | `_template/`（wheel） | `templates/`（{{var}}） | **`templates/`（cargo-generate Liquid）** |
| 生成器 | archetype:generate | yarch-init | yarch-init | create-admin/yarch-init-app | **cargo generate --path** |
| 发版 | tag→Central | tag→proxy | tag→PyPI | tag→npm/SPM | **固件不发库，模板随仓**（E6） |
| 机检 | ArchUnit+Spotless | golangci depguard | ruff+import-linter | depcruise/ktlint | **clippy+rustfmt+host 单测**（E7） |

## 三、目标形态

```
embedded/esp32/
├── PLAN.md / spec.md
└── templates/
    └── firmware/                    # cargo-generate 模板（microduck 形态反向沉淀）
        ├── Cargo.toml               # esp-idf-hal 0.46 / esp-idf-svc 0.52 钉版（E2）
        ├── build.rs / sdkconfig.defaults / partitions.csv
        ├── rust-toolchain.toml      # esp 工具链钉版
        └── src/
            ├── main.rs              # 入口：wifi → mqtt → ota 任务编排
            ├── identity.rs          # 设备标识 + NVS 凭证（device.md 一）
            ├── topic.rs             # topic 六段构造器（device.md 二）
            ├── telemetry.rs         # 遥测 ndjson 载荷（device.md 三）
            ├── mqtt.rs              # EspMqttClient 封装（TLS 强制）
            └── ota.rs               # 双分区 + 回滚 + 验签（device.md 四）
```

## 四、决策 E1-E8（拍板口径，引用 digest）

| # | 决策 | 结论 |
|---|---|---|
| E1 | 技术路线 | esp-idf-hal std 路线（WiFi/MQTT/OTA/TLS 成熟是决定性因素），no_std 不入册 |
| E2 | HAL 钉版 | `esp-idf-hal = "~0.46"` / `esp-idf-svc = "~0.52"`（次版本锁定护栏） |
| E3 | 芯片基线 | 首档 esp32 经典款；c3/s3 按 microduck 实际硬件触发扩展 |
| E4 | 工程生成 | cargo generate + 模板目录（生态标准通道） |
| E5 | device.md 先后 | **device.md 已成文**（2026-09-14，本规约引用其条文） |
| E6 | 发版 | 固件不发 crates.io 库；模板随仓（architecture.md 触发登记制转正） |
| E7 | 机检 | clippy + rustfmt + host 单测进 CI（ubuntu + espup 工具链）；烧录冒烟本地手册（无硬件 runner） |
| E8 | 与云栈 rust | 独立目录零共享 crate；术语表同表登记两轨方言名 |

## 五、施工批次

1. **第一批（本批）**：spec.md 成文（v1.0）+ PLAN.md 落位；模板骨架（Cargo.toml/build.rs/partitions.csv/rust-toolchain.toml + main.rs 最小可编译）——host 端 `cargo check --target xtensa-esp32-espidf` 走 CI。
2. **第二批**：模板五模块落地（identity/topic/telemetry/mqtt/ota）+ host 单测（topic 构造器/遥测 schema/命令幂等去重——device.md 七表条目）；microduck 真机联调冒烟（本地手册）。
3. **第三批**：与云端 Go 接入侧合体验证（microduck 全链路：telemetry 上报→影子→cmd 下行→resp 回执→ota 触发）。

## 六、验收口径

1. 模板 `cargo generate` 生成后 `cargo check`（CI ubuntu + espup）全绿；
2. device.md 七表可机检条目全部有对应单测（topic/schema/幂等/回滚 mock）；
3. 真机冒烟（microduck）：WiFi 连通 → MQTT 8883 TLS 握手 → 遥测上报云端落库 → OTA 全量包升级+回滚演示。

## 七、已知风险

1. xtensa 工具链（esp32 经典款是 Xtensa 非 RISC-V）编译依赖 espup 预编译 LLVM——CI 拉取慢（一次缓存）；
2. OTA 验签（Ed25519/ECDSA）在固件侧的性能与 flash 布局需实测（microduck 首刷时调）；
3. 真机联调依赖 microduck 硬件到位——host 单测与 CI 冒烟先行，真机批次不阻塞前两批。
