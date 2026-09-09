import { defineConfig } from "@playwright/test";

/**
 * 双模式冒烟之「被基座集成加载」路径（micro-frontend.md 十一-3/十三）：
 * 基座（ysaas-console dist）+ 子应用（ysaas-billing dist-micro 集成产物）各起 preview，
 * 真浏览器断言：沙箱加载/菜单装配/事件上行/contract 单例/storage 前缀/重挂幂等。
 * 构建前置：examples 双方 build + billing build:micro（CI 与本地同序）。
 */
export default defineConfig({
  testDir: ".",
  timeout: 30_000,
  retries: process.env.CI ? 1 : 0,
  // 全链路钉死 127.0.0.1：vite 默认绑 localhost（macOS 上常为 ::1 only），CI/本地
  // IPv4/IPv6 解析不确定会造成 webServer 就绪探活与浏览器侧访问不一致——显式 host 消除该分叉
  use: { baseURL: "http://127.0.0.1:4573" },
  webServer: [
    {
      command: "pnpm exec vite preview --host 127.0.0.1 --port 5174 --strictPort --outDir dist-micro",
      cwd: "../examples/ysaas-billing",
      url: "http://127.0.0.1:5174/ysaas-billing/",
      reuseExistingServer: false,
    },
    {
      command: "pnpm exec vite preview --host 127.0.0.1 --port 4573 --strictPort",
      cwd: "../examples/ysaas-console",
      url: "http://127.0.0.1:4573/",
      reuseExistingServer: false,
    },
  ],
});
