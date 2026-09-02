# Nginx 开发规约（v1.0 已定稿）

> **状态：已定稿**（2026-09-01 评审通过）。供人阅读、供 AI 作为规范上下文使用。
> 等级：**【强制】**违反即缺陷；**【推荐】**默认遵守、评审后可豁免；**【参考】**正向引导。适用 Nginx（stable/mainline）。分工与来源见文末附录。

## 一、定位与配置管理

1. 【强制】Nginx 定位：静态资源服务、四层/七层反向代理与入口负载、TLS 终结；API 网关职责（路由编排/鉴权/插件治理）归 Higress（见 [higress.md](higress.md)），不得在 Nginx 里用 rewrite/if 堆业务路由。
2. 【强制】配置声明式纳管：全部配置进 git/IaC 仓库（与业务仓同评审流程），禁线上手改不留痕。
3. 【强制】配置结构分文件：`conf.d/{服务名}.conf` 一服务一文件，公共段 include 复用；禁巨型单文件。
4. 【强制】变更流程：`nginx -t` 校验通过才可 reload；reload 优先于 restart；变更前备份、可回滚（配置版本化即回滚）。

## 二、安全基线

1. 【强制】对外站点 TLS only（1.2+），HTTP 80 仅 301 跳转；开启 HSTS。
2. 【强制】`server_tokens off`；`autoindex off`；默认 server 块返回 444/404，禁裸 IP 承载业务站点。
3. 【强制】`client_max_body_size` 显式设置（与 [minio-s3.md](minio-s3.md) 三-4 的上传上限对齐），超限 413 直接拒绝。
4. 【强制】限流按需开启：`limit_req`（单 IP QPS）与 `limit_conn`，阈值登记；对外 API 至少单 IP 兜底限流。**前置**：多层代理下按 IP 限流必须配套真实 IP 还原（`set_real_ip_from` 信任链 + `real_ip_header X-Forwarded-For`），否则限的是上游代理 IP，等于没限。
5. 【强制】代理头纪律：`X-Forwarded-For` / `X-Forwarded-Proto` / `Host` 必须传递；`X-Real-IP` 一并传递；traceId 透传（见四-2）。
6. 【推荐】安全响应头集（CSP、X-Content-Type-Options、Referrer-Policy）按站点类型启用。

## 三、代理与上游

1. 【强制】`proxy_pass` 上游用 upstream 块 + 显式 `proxy_connect_timeout` / `proxy_read_timeout` / `proxy_send_timeout`——禁默认 60s 读超挂死连接（与"远程调用必须超时"同源）。
2. 【强制】上游健康检查（被动 + 主动方案）与摘除：max_fails/fail_timeout 起步，关键链路主动探活。
3. 【强制】WebSocket 场景显式 `Upgrade`/`Connection` 头透传。
4. 【强制】`proxy_pass` URL 拼接用户输入必须收敛到已知前缀映射，禁变量直拼路径。
5. 【推荐】上游 keepalive（`keepalive N` + `proxy_http_version 1.1` + `Connection ""`）降低连接开销。
6. 【推荐】大流量静态与 API 分 server 分流，互不挤占 worker。

## 四、可观测

1. 【强制】access log 结构化 JSON（`log_format` 输出 ts/level/server/method/path/status/costMs/upstream/remoteAddr/requestId），字段命名对齐 [../api/logging-trace.md](../api/logging-trace.md)（ts/costMs 等 camelCase 口径）；错误日志 `warn` 级起步。
2. 【强制】入口生成/透传请求 ID：优先透传上游 `X-Trace-Id`，缺省用 `$request_id` 回填响应头——入口必须保证每个响应携带 traceId。
3. 【推荐】status 独立 server/stub_status 暴露指标（active/accepts/handled/requests）；接入监控告警（5xx 比例、P99 延迟、429/413 计数）。

## 五、性能基线

1. 【推荐】`worker_processes auto`；`gzip` on（text 类，level 4~6 起步）；静态资源 `expires`/`Cache-Control` 显式分级。
2. 【推荐】`sendfile`/`tcp_nopush`/`tcp_nodelay` 常开；`keepalive_timeout` 30~60s。
3. 【参考】高并发下调整 `worker_connections`、`reuseport` 按压测证据定，禁拍脑袋翻倍。

## 六、运维基线

1. 【强制】配置变更走评审（涉及路由/TLS/限流的变更等同发布）。
2. 【强制】证书到期监控（提前 30 天告警）与自动续期（ACME）。
3. 【强制】版本与模块清单登记；升级走灰度节点逐台 reload。

---

## 附：分工与来源

- **分工决策（可推翻）**：Nginx=流量入口/静态/TLS/简单代理；Higress=API 网关（路由编排、鉴权、限流插件、AI 网关）。重叠场景（纯前置代理）默认 Nginx，涉及服务治理策略默认 Higress。
- 超时/头透传/限流与 log_format 纪律：Nginx 官方文档通行实践，对齐阿里手册"远程调用必须超时"与 yarch 日志契约。
