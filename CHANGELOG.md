# Changelog

本仓各栈独立发版（tag 形如 `stacks/{java,python,web,golang}/vX.Y.Z`）；本文件按栈分组记录对使用者有影响的变更。格式参照 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/)，版本遵循语义化。

## [Unreleased]

### java（stacks/java）

- **安全**：Redis JSON 值反序列化加白名单（默认仅 JDK 基础容器/时间/数值类型）——Redis 数据被污染时不再可能实例化任意类；业务 DTO 类型化回读需登记 `yarch.redis.json-trusted-packages`（逗号分隔包前缀）
- **安全**：`@SignedApi` 落地 nonce 一次性消费防重放（原时间窗校验对窗口内重放无效）；nonce 存储默认进程内，引入 yarch-redis starter 后自动升级 Redis 跨实例
- **修复**：`@RequireRoles` 403 路径 ThreadLocal 不再串号到同线程的下一请求（AuthContext 改为角色校验通过后写入）

### python（stacks/python）

- **修复**：中间件装配顺序 Rate 移至 Idempotency 外层——此前限流 429 会被幂等层捕获落库，同键合法重试在 TTL 内永远回放 429
- **修复**：幂等执行异常路径不再"释放后又写回残缺 done"；5xx 响应不落 done（释放占位允许重试，对齐 java 口径）
- 新增中间件组合语义回归测试（`test_middleware_composition.py`，跨栈 conformance 新维度）

### golang（stacks/golang）

- **修复**：幂等 key 命名空间化 `{service}:idem:{sha256(rawKey)}`——原客户端可控原始 key 直入存储，共享 Redis 实例下可跨服务碰撞；`web.Options` 新增 `Service` 字段（挂幂等时必填）
- **修复**：`auth.RequireAuth` 增设 `auth.SubjectKey`（字符串形态 sub）——操作日志的 `userId` 此前恒为空串（ClaimsKey 存 `*Claims` 结构体，GetString 取不到）

### web（stacks/web）

- **新增**：微前端落地（web v0.2.0 主体）——`@yarch/contract` 新增微前端契约六模块（应用事件总线 / 前缀化 storage / manifest 登记 / 挂载 props / 共享单例标记 / 应用名前缀工具），react·vue 适配层同步导航端口；create 生成器新增 `--micro base|sub` 档（base-semi 基座 / sub-semi 子应用模板，应用名过 registry 登记表核对）；仓内双示例 ysaas-console（基座）+ ysaas-billing（子应用）全链路跑通
- **新增**：微前端集成 e2e（playwright 真浏览器六用例：沙箱加载/事件上行/contract 单例/storage 前缀/重挂幂等）接入 `pnpm test:e2e` 与 CI；登记机检 `pnpm check:micro`（registry.md ↔ 配置 ↔ 快照三方一致）；depcruise 新增载器归属/导航注册归属/跨应用直连三条微前端规则
- **修复**：Semi UI 在 React 19 下命令式弹层（Toast/Notification）静默不渲染——入口统一注入 `semiGlobal.config.createRoot`（三档模板 + 微前端模板 + admin-demo 全量）
- **修复**：基座登录页对已登录访问不再滞留（会话恢复直达 `/login` 一律回首页）；子应用事件上行订阅从首页页面上移至常驻布局壳——路由切换不再丢订阅
- **修复**：e2e 网络口径钉死 127.0.0.1（vite 默认绑 localhost 在 macOS 为 ::1 only，CI/本地探活与浏览器侧访问不一致会造成假失败）

### 仓库

- 开源治理基线：CONTRIBUTING / SECURITY / issue 与 PR 模板
- 根 README 同步 python 栈（四栈快速开始/构件表/机检表/状态），徽章换为 CI 活链接
- 契约锚点表对齐已落地栈：rust 除名（触发式预留）、python 补位
- 内部施工计划文档（docs/superpowers/）移出公开树
- 契约层新设 clients/ 客户端规约：client-shared + android + ios 三份 v1.0 定稿（M1-M7 拍板：Google 官方基准 / 官方 API 设计指南+Airbnb / xcodegen+SPM / Hilt / MVVM+@Observable / targetSdk≥36 硬线）；registry.md 新增第五节移动 App 登记；`ysaas-console`（基座）与 `ysaas-billing`（子应用）正式登记为首批前端应用名

## [0.1.0] - 2026-09-03（java / web）

- Java 13 件上 Maven Central：parent/BOM + common + 8 starter + 双 archetype（DDD 七包 / 阿里五层）
- Web 三包上 npm：@yarch/contract + @yarch/react + @yarch/vue + @yarch/create-admin（Semi/antd/arco 三档模板）
- 契约层 24 份 v1.0 定稿（api 四件套 + infra 20 份 + registry）

## python 栈第一批 - 2026-09-07（未发版，tag 待推送）

- uv workspace 双发行版：`yarch-python` 平台构件（契约内核/logx/中间件/persist/redix/httpx/celeryx/testx）+ `yarch-init` 生成器（DDD 七包模板）
- 103 tests（含中间件组合语义回归）；CI 双矩阵 3.12/3.13 + 生成后冒烟
- contract/web 微前端规约 v1.0 定稿（规约层共 25 份）
