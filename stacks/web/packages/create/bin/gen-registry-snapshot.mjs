#!/usr/bin/env node
// 从 contract/registry.md 生成包内快照 bin/registry-snapshot.json（发版前跑；npm 消费方无仓可读，
// 快照是 --registry 未传时的核对来源）。CI/内网可随时用 --registry 指最新 registry.md 覆盖快照。
//
// 用法：node bin/gen-registry-snapshot.mjs [yarch 仓根目录]（默认向上定位 ../../../../）
import { readFileSync, writeFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, join, resolve } from "node:path";
import { parseRegistryMd } from "./registry.mjs";

const PKG_ROOT = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const repoRoot = resolve(process.argv[2] ?? join(PKG_ROOT, "../../../.."));
const mdPath = join(repoRoot, "contract", "registry.md");

let md;
try {
  md = readFileSync(mdPath, "utf8");
} catch {
  console.error(`gen-registry-snapshot: 读不到 ${mdPath}（传 yarch 仓根目录作为参数）`);
  process.exit(1);
}

const { serviceNames, appNames } = parseRegistryMd(md);
const snapshot = {
  source: "contract/registry.md",
  generatedAt: new Date().toISOString(),
  serviceNames: [...serviceNames].sort(),
  appNames: [...appNames].sort(),
};
const out = join(PKG_ROOT, "bin", "registry-snapshot.json");
writeFileSync(out, `${JSON.stringify(snapshot, null, 2)}\n`);
console.log(`✅ ${out}：服务名 ${snapshot.serviceNames.length} 个，应用名 ${snapshot.appNames.length} 个`);
