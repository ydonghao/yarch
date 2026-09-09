#!/usr/bin/env node
// 微前端登记机检（web-stack CI 门禁；micro-frontend.md 十二-2「登记表是唯一权威」）：
//   1. 扫描仓内示例工程：子应用登记表（micro-apps.config.ts 的 name）+ 自身应用名
//      （stores/auth.ts 的 createAppStorage 首参）——即该项目实际占用的全部应用名；
//   2. 应用名格式（registry.md 一-1）与首段服务名归属（四-1）校验；
//   3. 与 contract/registry.md 第四节登记表比对——未登记即违规；
//   4. 与 create 包快照 registry-snapshot.json 比对——快照漂移即违规。
// 生成工程的命名校验在 create-admin 生成时做（模板含未渲染占位符，不在本机检范围）。
// 用法：node scripts/check-micro.mjs（在 stacks/web 下运行）
import { readFileSync, existsSync } from "node:fs";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { parseRegistryMd } from "../packages/create/bin/registry.mjs";

const WEB_ROOT = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const REPO_ROOT = resolve(WEB_ROOT, "../..");
const APP_NAME_RE = /^[a-z][a-z0-9-]{1,31}$/;

// 应用名提取源：登记表 name 字段 + 基座/子应用自身 storage 前缀
const EXTRACTORS = [
  { file: "src/app/micro-apps.config.ts", re: /name:\s*"([a-z0-9-]+)"/g, what: "子应用登记表" },
  { file: "src/stores/auth.ts", re: /createAppStorage\("([a-z0-9-]+)"\)/g, what: "自身 storage 前缀" },
];

const violations = [];
const declared = new Set();

for (const project of ["examples/ysaas-console", "examples/ysaas-billing"]) {
  for (const { file, re, what } of EXTRACTORS) {
    const abs = join(WEB_ROOT, project, file);
    if (!existsSync(abs)) continue;
    const src = readFileSync(abs, "utf8");
    for (const m of src.matchAll(re)) {
      const name = m[1];
      declared.add(name);
      if (!APP_NAME_RE.test(name)) {
        violations.push(`应用名 ${name}（${project}/${what}）违反 registry.md 一-1 格式`);
      }
    }
  }
}

let md;
try {
  md = readFileSync(join(REPO_ROOT, "contract", "registry.md"), "utf8");
} catch {
  console.error(`check-micro: 读不到 ${join(REPO_ROOT, "contract", "registry.md")}（须在 yarch 仓内运行）`);
  process.exit(1);
}
const { serviceNames, appNames } = parseRegistryMd(md);

for (const name of declared) {
  if (!APP_NAME_RE.test(name)) continue; // 格式违规已报，避免重复
  const owner = name.split("-")[0];
  if (!serviceNames.has(owner)) {
    violations.push(`应用名 ${name} 首段 ${owner} 未在 registry.md 二节登记为服务名（四-1）`);
  }
  if (!appNames.has(name)) {
    violations.push(`应用名 ${name} 未在 contract/registry.md 四节登记——PR 修改登记表即登记`);
  }
}

// 快照漂移检查：仓内 registry.md 变更后必须重新生成快照（防手工改表不同步）
const snapshotPath = join(WEB_ROOT, "packages/create/bin/registry-snapshot.json");
if (existsSync(snapshotPath)) {
  const snap = JSON.parse(readFileSync(snapshotPath, "utf8"));
  const mdNow = { serviceNames: [...serviceNames].sort(), appNames: [...appNames].sort() };
  if (
    JSON.stringify(snap.serviceNames) !== JSON.stringify(mdNow.serviceNames) ||
    JSON.stringify(snap.appNames) !== JSON.stringify(mdNow.appNames)
  ) {
    violations.push("registry-snapshot.json 与 contract/registry.md 不一致——重跑 node packages/create/bin/gen-registry-snapshot.mjs");
  }
}

if (violations.length > 0) {
  console.error(`❌ check-micro ${violations.length} 项违规：`);
  for (const v of violations) console.error(`  - ${v}`);
  process.exit(1);
}
console.log(`✅ check-micro：${declared.size} 个应用名全部已登记且快照一致（${[...declared].sort().join(", ") || "无登记应用"}）`);
