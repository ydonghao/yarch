// 生成 registry 快照（从 contract/registry.md 提取五六节名表，供 npm 消费方核对）。
// 用法：node bin/gen-registry-snapshot.mjs [registry.md 路径]
import { readFileSync, writeFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, join, resolve } from "node:path";
import { parseRegistryMd } from "./registry.mjs";

const PKG_ROOT = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const registryPath = process.argv[2] || resolve(PKG_ROOT, "../../contract/registry.md");
const md = readFileSync(registryPath, "utf8");
const { serviceNames, appNames, mobileAppNames, clientNames } = parseRegistryMd(md);

const snapshot = {
  source: "contract/registry.md",
  generatedAt: new Date().toISOString(),
  serviceNames: [...serviceNames].sort(),
  appNames: [...appNames].sort(),
  mobileAppNames: [...mobileAppNames].sort(),
  clientNames: [...clientNames].sort(),
};

const outPath = join(PKG_ROOT, "bin", "registry-snapshot.json");
writeFileSync(outPath, JSON.stringify(snapshot, null, 2) + "\n");
console.log(`快照已生成 ${outPath}（服务名 ${snapshot.serviceNames.length} / 前端 ${snapshot.appNames.length} / 移动 App ${snapshot.mobileAppNames.length} / 小程序+游戏 ${snapshot.clientNames.length}）`);
