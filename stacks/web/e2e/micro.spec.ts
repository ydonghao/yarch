/**
 * 微前端集成冒烟（micro-frontend.md 十三表「双模式冒烟」行）：
 * 独立构建路径由 web-stack.yml 生成后冒烟覆盖（tsc + vite build 双 mode），
 * 本文件覆盖「被基座集成加载」路径——十一-3 强制条文的两条腿之一。
 */
import { expect, test } from "@playwright/test";

test.describe("ysaas-console 基座 + ysaas-billing 子应用（micro-app 集成档）", () => {
  test("基座可独立运行：登录 + manifest 菜单装配（三-1/三-4）", async ({ page }) => {
    await page.goto("/");
    await expect(page.getByRole("heading", { name: "ysaas-console · 登录" })).toBeVisible();
    await page.getByRole("button", { name: "登录" }).click();
    await expect(page.getByRole("heading", { name: "基座首页" })).toBeVisible();
    // 菜单由 micro-apps.config.ts 声明式装配（三-4），非硬编码
    await expect(page.getByRole("menuitem", { name: "首页" })).toBeVisible();
    await expect(page.getByRole("menuitem", { name: "账单" })).toBeVisible();
  });

  test("登录态 storage key 带应用名前缀（二-2）", async ({ page }) => {
    await page.goto("/");
    await page.getByRole("button", { name: "登录" }).click();
    await expect(page.getByRole("heading", { name: "基座首页" })).toBeVisible();
    const token = await page.evaluate(() => localStorage.getItem("ysaas-console:token"));
    expect(token).toBeTruthy();
  });

  test("子应用被基座加载：集成产物经共享运行时渲染（八-1/八-2 + 四-1 前缀分发）", async ({ page }) => {
    await page.goto("/");
    await page.getByRole("button", { name: "登录" }).click();
    await page.getByRole("menuitem", { name: "账单" }).click();
    // URL 前缀 = 应用名（四-1）；子应用域内页面在沙箱内渲染——其 dist-micro 无 react/contract，
    // 能渲染即证明取用了基座 window 全局（八-1/八-2 运行时口径）
    await expect(page).toHaveURL(/\/ysaas-billing/);
    await expect(page.getByRole("heading", { name: "账单列表" })).toBeVisible({ timeout: 15_000 });
  });

  test("contract 单例标记在基座注册（八-1）", async ({ page }) => {
    await page.goto("/");
    await page.evaluate(() => localStorage.setItem("ysaas-console:token", '"e2e"'));
    await page.goto("/ysaas-billing");
    await expect(page.getByRole("heading", { name: "账单列表" })).toBeVisible({ timeout: 15_000 });
    const shared = await page.evaluate(() => {
      const ns = (globalThis as Record<string, unknown>).__YARCH_CONTRACT__ as Record<string, unknown> | undefined;
      return ns != null && typeof ns.ApiError === "function" && typeof ns.createClient === "function";
    });
    expect(shared).toBe(true);
  });

  test("事件上行：子应用 export-done → 基座 Toast（九-1/九-3）", async ({ page }) => {
    await page.goto("/");
    await page.getByRole("button", { name: "登录" }).click();
    await page.getByRole("menuitem", { name: "账单" }).click();
    await expect(page.getByRole("heading", { name: "账单列表" })).toBeVisible({ timeout: 15_000 });
    await page.getByRole("button", { name: "导出" }).click();
    // Modal 挂在本应用容器内（六-3），确认后事件经共享总线（八-1 同一 contract 实例）上行基座
    await expect(page.getByText("将导出")).toBeVisible();
    await page.getByText("确定", { exact: true }).click();
    await expect(page.getByText(/ysaas-billing 事件上行：export-done/)).toBeVisible({ timeout: 10_000 });
  });

  test("重挂幂等：离开再回来不崩不白屏（五-3）", async ({ page }) => {
    await page.goto("/");
    await page.evaluate(() => localStorage.setItem("ysaas-console:token", '"e2e"'));
    // 注入 token 后 reload：登录态在应用启动时读取（模拟会话恢复），不刷新仍是登录页
    await page.reload();
    await page.getByRole("menuitem", { name: "账单" }).click();
    await expect(page.getByRole("heading", { name: "账单列表" })).toBeVisible({ timeout: 15_000 });
    await page.getByRole("menuitem", { name: "首页" }).click();
    await expect(page.getByRole("heading", { name: "基座首页" })).toBeVisible();
    await page.getByRole("menuitem", { name: "账单" }).click();
    await expect(page.getByRole("heading", { name: "账单列表" })).toBeVisible({ timeout: 15_000 });
  });
});
