// yarch-init-app 引擎级单测（node:test，零依赖）——与 stacks/web/packages/create 引擎范式同构。
import { test } from "node:test";
import assert from "node:assert/strict";
import { mkdtempSync, writeFileSync, mkdirSync, readFileSync, existsSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { spawnSync } from "node:child_process";
import { parseRegistryMd } from "./registry.mjs";

const BIN = new URL("./yarch-init-app.mjs", import.meta.url).pathname;

function run(args, cwd) {
  const r = spawnSync("node", [BIN, ...args], { encoding: "utf8", cwd });
  return { code: r.status, out: r.stdout + r.stderr };
}

test("registry.md 解析：二/四/五/六节同表命中", () => {
  const md = [
    "## 二、服务名登记表",
    "| `ysaas` | ... |",
    "| `yagent` | ... |",
    "## 四、前端应用名登记",
    "| `ysaas-console` | ... |",
    "## 五、移动 App 登记",
    "| `ysaas-companion` | ... |",
    "## 六、小程序/游戏登记",
    "| `ysaas-shop` | ... |",
  ].join("\n");
  const r = parseRegistryMd(md);
  assert.ok(r.serviceNames.has("ysaas"));
  assert.ok(r.appNames.has("ysaas-console"));
  assert.ok(r.mobileAppNames.has("ysaas-companion"));
  assert.ok(r.clientNames.has("ysaas-shop"));
  assert.ok(!r.mobileAppNames.has("ysaas-console"));
});

test("生成后冒烟：android/ios/both 三口径零残留占位符 + 身份同源", () => {
  // 用真实仓内 registry.md（yarch 本仓，ysaas/yagent 已登记）
  const registryPath = new URL("../../../contract/registry.md", import.meta.url).pathname;
  const tmp = mkdtempSync(join(tmpdir(), "yarch-init-"));
  for (const platform of ["android", "ios"]) {
    const out = join(tmp, platform);
    const r = run(["ysaas-demo", "--platform", platform, "--yes", "--registry", registryPath, "--out", out]);
    assert.equal(r.code, 0, `${platform} 生成失败：${r.out}`);
    // 零残留占位符（仅文本文件；wrapper jar 等二进制不作占位符扫描）
    const leftovers = spawnSync(
      "grep",
      ["-rlI", "--include=*.kts", "--include=*.kt", "--include=*.xml", "--include=*.yml", "--include=*.toml", "--include=*.json", "--include=*.md", "--include=*.swift", "{{", out],
      { encoding: "utf8" },
    ).stdout.trim();
    assert.equal(leftovers, "", `${platform} 残留占位符：${leftovers}`);
  }
  // both 口径：一次生成 android+ios，身份同源
  const both = join(tmp, "both");
  const r = run(["ysaas-companion", "--platform", "both", "--yes", "--registry", registryPath, "--out", both]);
  assert.equal(r.code, 0, `both 生成失败：${r.out}`);
  const androidGradle = readFileSync(join(both, "android/app/build.gradle.kts"), "utf8");
  assert.match(androidGradle, /applicationId = "io\.github\.ydonghao\.ysaascompanion"/);
  assert.match(androidGradle, /namespace = "io\.github\.ydonghao\.ysaascompanion"/);
  const iosYml = readFileSync(join(both, "ios/project.yml"), "utf8");
  assert.match(iosYml, /bundleIdPrefix: io\.github\.ydonghao/);
  assert.ok(existsSync(join(both, "android/app/src/main/kotlin/io/github/ydonghao/ysaascompanion/YsaasCompanion.kt")));
});

test("registry 五-1：未登记服务名首段被拒（非挂起）", () => {
  const registryPath = new URL("../../../contract/registry.md", import.meta.url).pathname;
  const tmp = mkdtempSync(join(tmpdir(), "yarch-init-"));
  const out = join(tmp, "x");
  const r = run(["unknown-app", "--platform", "android", "--yes", "--registry", registryPath, "--out", out]);
  assert.notEqual(r.code, 0);
  assert.match(r.out, /不在 registry\.md 二节已登记服务名表/);
});

test("裸通用词拒绝", () => {
  const r = run(["app", "--platform", "android", "--yes", "--out", "/tmp/x"]);
  assert.notEqual(r.code, 0);
  assert.match(r.out, /裸通用词/);
});
