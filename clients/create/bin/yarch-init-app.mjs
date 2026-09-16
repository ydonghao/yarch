#!/usr/bin/env node
// yarch 客户端工程生成器（clients 第三批，C10）—— web create-admin.mjs 引擎范式对偶。
// 支持 --platform android|ios|both|miniprogram|game-cocos
// 模板资产（templates/*，{{var}} 占位）+ 交互问答 + 通用渲染引擎 + registry 五/六节校验。
//
// 用法（本仓本地）：
//   node clients/create/bin/yarch-init-app.mjs ysaas-shop --platform miniprogram
//   node clients/create/bin/yarch-init-app.mjs ysaas-game --platform game-cocos --yes
//   node clients/create/bin/yarch-init-app.mjs ysaas-companion --platform both --yes
//
// 发版后（npm create = npm exec）：
//   npm create @yarch/app@latest ysaas-shop -- --platform miniprogram

import { readFileSync, writeFileSync, readdirSync, mkdirSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, join, resolve } from "node:path";
import readline from "node:readline/promises";
import { parseRegistryMd } from "./registry.mjs";

const PKG_ROOT = resolve(dirname(fileURLToPath(import.meta.url)), "..");

const PLATFORMS = {
  android: "Android 原生（Kotlin + Compose，Google 官方基准，minsdk26 默认 / 24 扩展）",
  ios: "iOS 原生（Swift + SwiftUI，MVVM + @Observable，iOS 17 默认 / 16 扩展）",
  both: "双端同源（android + ios，包标识一致互为派生，registry 五节一次登记）",
  miniprogram: "微信小程序（原生 + TypeScript + Vant Weapp，@yarch/contract/wx）",
  "game-cocos": "Cocos Creator 游戏（TypeScript，@yarch/contract/cocos 运行时自动检测）",
};

const YARCH_CONTRACT_VERSION = "^0.3.0";

const servicePattern = /^[a-z][a-z0-9-]{1,31}$/;
const genericWords = new Set([
  "api", "app", "service", "server", "backend", "web", "admin",
  "main", "common", "system", "demo", "user", "gateway", "game", "shop",
]);

const SKIP_FILES = new Set(["archetype.json"]);
const SKIP_DIRS = new Set(["node_modules", "dist", "build", ".build", ".gradle", ".swiftpm", "library", "temp", "local", "profiles"]);
const PLACEHOLDER = /\{\{\s*([a-zA-Z][a-zA-Z0-9]*)\s*\}\}/g;

function usage() {
  return [
    "用法：yarch-init-app <工程名> --platform <android|ios|both|miniprogram|game-cocos>",
    "              [--registry <registry.md>] [--desc <描述>] [--appId <微信AppID>]",
    "              [--baseUrl <API地址>] [--baseline <双基线档>] [--deps file|version] [--src <模板根>] [--out <目录>] [--yes]",
    "  --platform  生成目标平台（必填）",
    "  --registry  registry.md 路径（默认用 bin/registry-snapshot.json）",
    "  --baseline  android/ios 双基线档覆盖（android-minsdk26|android-minsdk24 / ios17|ios16；默认取高版）",
    "  --deps      @yarch/contract 依赖形态：version（默认 ^0.3.0）| file（发版前本地过渡）",
    "  --yes       非交互：缺省项全走默认值（CI 用）",
  ].join("\n");
}

function parseArgs(argv) {
  const opts = { _: [] };
  for (let i = 0; i < argv.length; i++) {
    const a = argv[i];
    if (a === "--yes" || a === "-y") {
      opts.yes = true;
    } else if (a.startsWith("--")) {
      opts[a.slice(2)] = argv[i + 1];
      i++;
    } else {
      opts._.push(a);
    }
  }
  return opts;
}

function validateName(name) {
  if (!servicePattern.test(name)) {
    return "须过 registry.md 一-1 校验：^[a-z][a-z0-9-]{1,31}$";
  }
  if (genericWords.has(name)) {
    return "裸通用词禁止单独成名，须带项目/领域前缀（如 ysaas-shop）";
  }
  if (name.includes('"') || name.includes("\\")) {
    return '不得包含双引号与反斜杠';
  }
  return null;
}

function loadRegistry(registryArg) {
  if (registryArg) {
    return parseRegistryMd(readFileSync(resolve(registryArg), "utf8"));
  }
  try {
    const snapshot = JSON.parse(
      readFileSync(join(PKG_ROOT, "bin", "registry-snapshot.json"), "utf8"),
    );
    return {
      serviceNames: new Set(snapshot.serviceNames || []),
      appNames: new Set(snapshot.appNames || []),
      mobileAppNames: new Set(snapshot.mobileAppNames || []),
      clientNames: new Set(snapshot.clientNames || []),
    };
  } catch {
    return null;
  }
}

// 五节校验（android/ios/both）：首段=已登记服务名 + 名不在五节已登记
function validateMobileAppName(name, registry) {
  const baseErr = validateName(name);
  if (baseErr) return baseErr;
  if (!registry) return "无法核对 registry.md（缺快照且未传 --registry）";
  if (!name.includes("-")) return "App 名须由「服务名-用途」两段构成（registry 五-1）";
  const service = name.split("-")[0];
  if (!registry.serviceNames.has(service)) {
    return `首段 ${service} 不在 registry.md 二节已登记服务名表`;
  }
  if (registry.mobileAppNames.has(name)) return `App 名 ${name} 已在 registry.md 五节登记`;
  return null;
}

// 六节校验（miniprogram/game-cocos）：首段=已登记服务名 + 名不在六节已登记
function validateClientName(name, registry) {
  const baseErr = validateName(name);
  if (baseErr) return baseErr;
  if (!registry) return "无法核对 registry.md（缺快照且未传 --registry）";
  if (!name.includes("-")) return "名称须由「服务名-用途」两段构成（registry 六-1）";
  const service = name.split("-")[0];
  if (!registry.serviceNames.has(service)) {
    return `首段 ${service} 不在 registry.md 二节已登记服务名表`;
  }
  if (registry.clientNames.has(name)) return `名称 ${name} 已在 registry.md 六节登记`;
  return null;
}

async function askText(rl, question, { fallback = "", validate = null, optional = false } = {}) {
  for (;;) {
    const hint = fallback ? `（默认 ${fallback}）` : optional ? "（可空）" : "";
    const answer = (await rl.question(`${question}${hint}: `)).trim();
    const value = answer || fallback;
    if (!value && !optional) { console.log("  ✗ 必填"); continue; }
    if (value && validate) { const err = validate(value); if (err) { console.log(`  ✗ ${err}`); continue; } }
    return value;
  }
}

async function askSelect(rl, question, options, defaultIndex) {
  console.log(question);
  options.forEach((label, i) => console.log(`  ${i + 1}) ${label}${i === defaultIndex ? "（默认）" : ""}`));
  for (;;) {
    const answer = (await rl.question(`选择 [${defaultIndex + 1}]: `)).trim();
    const idx = answer ? Number.parseInt(answer, 10) - 1 : defaultIndex;
    if (Number.isInteger(idx) && idx >= 0 && idx < options.length) return idx;
    console.log("  ✗ 无效选项");
  }
}

function renderStr(text, vars) {
  return text.replace(PLACEHOLDER, (_, key) => (key in vars ? vars[key] : `{{${key}}}`));
}

// 通用渲染引擎：遍历模板树，文件名与内容逐个 {{var}} 替换；未命中变量的占位符保留，
// 由 render 后的残留扫描兜底报错（防模板与变量集漂移）。archetype.json 不进生成物。
// WXML 残留扫描豁免：微信小程序 WXML 用 {{ }} 做运行期数据绑定，非生成期变量。
const SKIP_LEFTOVER_EXTS = new Set([".wxml"]);
function renderTree(src, dst, vars, leftovers) {
  let count = 0;
  for (const entry of readdirSync(src, { withFileTypes: true })) {
    if (entry.isDirectory()) {
      if (SKIP_DIRS.has(entry.name)) continue;
      count += renderTree(join(src, entry.name), join(dst, renderStr(entry.name, vars)), vars, leftovers);
    } else if (entry.isFile()) {
      if (SKIP_FILES.has(entry.name)) continue;
      const target = join(dst, renderStr(entry.name, vars));
      mkdirSync(dirname(target), { recursive: true });
      const rendered = renderStr(readFileSync(join(src, entry.name), "utf8"), vars);
      const ext = entry.name.slice(entry.name.lastIndexOf("."));
      if (!SKIP_LEFTOVER_EXTS.has(ext)) {
        for (const m of rendered.matchAll(PLACEHOLDER)) {
          leftovers.push(`${target}: {{${m[1]}}}`);
        }
      }
      writeFileSync(target, rendered);
      count++;
    }
  }
  return count;
}

function fatal(message) {
  console.error(`yarch-init-app: ${message}`);
  process.exit(1);
}

// packageName → PascalCase（如 ysaas-shop → YsaasShop）
function toPascalCase(name) {
  return name.split("-").map((s) => s.charAt(0).toUpperCase() + s.slice(1)).join("");
}
// packageName → 去短横线（如 ysaas-shop → ysaasshop）
function toPackageSegment(name) {
  return name.replace(/-/g, "");
}

// android/ios 派生变量集
function mobileVars(name, platform) {
  const seg = toPackageSegment(name);
  const cls = toPascalCase(name);
  const base = {
    appPackageSegment: seg,
    appClassName: cls,
  };
  if (platform === "android") {
    return {
      ...base,
      appApplicationId: `io.github.ydonghao.${seg}`,
      appNamespace: `io.github.ydonghao.${seg}`,
      yarchClientPath: resolve(PKG_ROOT, "../android"),
    };
  }
  // ios
  return {
    ...base,
    bundleIdPrefix: "io.github.ydonghao",
    appBundleId: `io.github.ydonghao.${seg}`,
    yarchClientPath: resolve(PKG_ROOT, "../ios"),
  };
}

// 平台→模板目录映射（android/ios 有 min 基线双档，默认取高版）
function templateDirFor(platform, srcArg) {
  if (srcArg) return resolve(srcArg);
  const map = {
    android: "android-minsdk26",
    ios: "ios17",
    miniprogram: "miniprogram",
    "game-cocos": "game-cocos",
  };
  if (platform === "both") {
    return null; // both 走两次：android + ios
  }
  return resolve(PKG_ROOT, "templates", map[platform]);
}

async function generatePlatform(platform, name, vars, outDir, templateSrc) {
  const td = templateDirFor(platform, templateSrc);
  if (!td) { fatal(`both 平台不支持单次生成，须拆分调用`); }
  try { readFileSync(join(td, "archetype.json")); }
  catch { fatal(`模板目录不可达或缺少 archetype.json：${td}`); }

  const leftovers = [];
  const n = renderTree(td, outDir, vars, leftovers);
  if (leftovers.length > 0) {
    fatal(`模板存在未声明变量（模板与变量集漂移）：\n  ${leftovers.join("\n  ")}`);
  }
  return n;
}

async function main() {
  const args = parseArgs(process.argv.slice(2));
  const platform = args.platform;
  if (!platform || !(platform in PLATFORMS)) {
    fatal(`--platform 必填，须为 ${Object.keys(PLATFORMS).join("|")}\n${usage()}`);
  }
  const nameArg = args._.length > 0 ? args._[0] : null;
  const interactive = !args.yes && process.stdin.isTTY;
  if (!nameArg && !interactive) fatal(`工程名必填\n${usage()}`);

  const registry = loadRegistry(args.registry);
  const isMobile = platform === "android" || platform === "ios" || platform === "both";
  const validator = isMobile ? validateMobileAppName : validateClientName;
  const registrySection = isMobile ? "五" : "六";

  const rl = interactive ? readline.createInterface({ input: process.stdin, output: process.stdout }) : null;
  try {
    let name = nameArg;
    let description = args.desc;
    let appId = args.appId || "";
    let baseUrl = args.baseUrl || "https://api.example.com";

    if (interactive) {
      console.log("yarch 客户端工程生成器（clients/create）\n");
      if (!name) {
        name = await askText(rl, `应用名（registry ${registrySection} 节登记；首段=已登记服务名）`, { validate: (v) => validator(v, registry) });
      } else {
        const err = validator(name, registry);
        if (err) fatal(err);
      }
      if (!description) {
        description = await askText(rl, "工程描述", {
          fallback: `${name}：基于 yarch 脚手架生成的${PLATFORMS[platform].split("（")[0]}工程`,
        });
      }
      if (platform === "miniprogram" || platform === "game-cocos") {
        if (!appId) appId = await askText(rl, "微信 AppID（registry 六节登记；H5 档可空）", { optional: true });
        if (interactive) baseUrl = await askText(rl, "API base URL", { fallback: baseUrl });
      }
    }

    if (!name) fatal(`工程名缺失\n${usage()}`);
    const nameErr = validator(name, registry);
    if (nameErr) fatal(nameErr);
    description ??= `${name}：基于 yarch 脚手架生成的工程`;
    const service = name.split("-")[0];

    const depsMode = args.deps === "file" ? "file" : "version";
    const yarchContractDep = depsMode === "version"
      ? YARCH_CONTRACT_VERSION
      : `file:${resolve(PKG_ROOT, "../../stacks/web/packages/contract")}`;

    // --baseline：android/ios 双基线档覆盖（模板冒烟用；both 不支持单档覆盖）
    const BASELINES = { android: ["android-minsdk26", "android-minsdk24"], ios: ["ios17", "ios16"] };
    let templateSrc = args.src;
    if (args.baseline) {
      if (!(platform in BASELINES)) fatal("--baseline 仅支持 android / ios（双基线平台）");
      if (!BASELINES[platform].includes(args.baseline)) {
        fatal(`--baseline 须为 ${BASELINES[platform].join("|")}`);
      }
      templateSrc = resolve(PKG_ROOT, "templates", args.baseline);
    }

    const commonVars = {
      packageName: name,
      appName: name,
      service,
      description,
      yarchContractDep,
    };

    const outDir = resolve(args.out || name);
    let existing;
    try { existing = readdirSync(outDir); } catch { existing = null; }
    if (existing && existing.length > 0) fatal(`输出目录 ${outDir} 非空`);

    if (platform === "both") {
      // 双端：android + ios，同输出目录下 android/ ios/ 子目录
      const androidOut = join(outDir, "android");
      const iosOut = join(outDir, "ios");
      const androidVars = { ...commonVars, ...mobileVars(name, "android") };
      const iosVars = { ...commonVars, ...mobileVars(name, "ios") };
      const nA = await generatePlatform("android", name, androidVars, androidOut, templateSrc);
      const nI = await generatePlatform("ios", name, iosVars, iosOut, templateSrc);
      console.log(`✅ 已生成 ${outDir}（android ${nA} 文件 + ios ${nI} 文件，both 双端）`);
      console.log(`\n下一步：\n  1. cd ${outDir}/android && ./gradlew build\n  2. cd ${outDir}/ios && xcodegen generate && swift build\n  3. registry.md ${registrySection} 节登记 ${name}（双端包标识一致）`);
    } else {
      const platformVars = { ...commonVars };
      if (platform === "miniprogram" || platform === "game-cocos") {
        platformVars.appId = appId;
        platformVars.baseUrl = baseUrl;
      }
      if (platform === "game-cocos") {
        platformVars.cocosVersion = "3.8.0";
      }
      if (platform === "android" || platform === "ios") {
        Object.assign(platformVars, mobileVars(name, platform));
      }
      const n = await generatePlatform(platform, name, platformVars, outDir, templateSrc);
      console.log(`✅ 已生成 ${outDir}（${n} 个文件，${platform} 档）—— ${PLATFORMS[platform]}`);
      console.log(`\n下一步：`);
      if (platform === "miniprogram") {
        console.log(`  1. cd ${outDir} && npm install && npx tsc --noEmit`);
        console.log(`  2. 微信开发者工具 → 打开 → 工具 → 构建 npm（packNpm）`);
        console.log(`  3. registry.md 六节登记 ${name}（小程序主体，AppID=${appId || "待填"}）`);
      } else if (platform === "game-cocos") {
        console.log(`  1. cd ${outDir} && npm install && npx tsc --noEmit`);
        console.log(`  2. Cocos Creator ${platformVars.cocosVersion} 打开本目录`);
        console.log(`  3. registry.md 六节登记 ${name}（小游戏主体，AppID=${appId || "待填"}）`);
      } else if (platform === "android") {
        console.log(`  1. cd ${outDir} && ./gradlew :app:assembleDebug`);
        console.log(`  2. registry.md 五节登记 ${name}（android）`);
      } else if (platform === "ios") {
        console.log(`  1. cd ${outDir} && xcodegen generate`);
        console.log(`  2. cd ${outDir} && swift build`);
        console.log(`  3. registry.md 五节登记 ${name}（ios）`);
      }
      console.log(`  ${platform === "miniprogram" || platform === "game-cocos" ? "4" : "3"}. @yarch/contract 为 ${depsMode === "version" ? `${YARCH_CONTRACT_VERSION} 版本依赖` : "file: 本地依赖（发版后改 version）"}`);
    }
  } finally {
    if (rl) rl.close();
  }
}

main().catch((err) => fatal(err.message));
