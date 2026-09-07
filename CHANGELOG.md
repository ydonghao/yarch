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

- **修复**：depcruise 规则锚定失效——`pages-are-thin` / `no-reverse-deps` 两条规则因路径前缀不匹配从未命中（机检实际是摆设）；admin-demo 两页直调域 api 的违规随之修复（收进 feature hooks 层）

### 仓库

- 开源治理基线：CONTRIBUTING / SECURITY / issue 与 PR 模板
- 根 README 同步 python 栈（四栈快速开始/构件表/机检表/状态），徽章换为 CI 活链接
- 契约锚点表对齐已落地栈：rust 除名（触发式预留）、python 补位
- 内部施工计划文档（docs/superpowers/）移出公开树

## [0.1.0] - 2026-09-03（java / web）

- Java 13 件上 Maven Central：parent/BOM + common + 8 starter + 双 archetype（DDD 七包 / 阿里五层）
- Web 三包上 npm：@yarch/contract + @yarch/react + @yarch/vue + @yarch/create-admin（Semi/antd/arco 三档模板）
- 契约层 24 份 v1.0 定稿（api 四件套 + infra 20 份 + registry）

## python 栈第一批 - 2026-09-07（未发版，tag 待推送）

- uv workspace 双发行版：`yarch-python` 平台构件（契约内核/logx/中间件/persist/redix/httpx/celeryx/testx）+ `yarch-init` 生成器（DDD 七包模板）
- 103 tests（含中间件组合语义回归）；CI 双矩阵 3.12/3.13 + 生成后冒烟
- contract/web 微前端规约 v1.0 定稿（规约层共 25 份）
