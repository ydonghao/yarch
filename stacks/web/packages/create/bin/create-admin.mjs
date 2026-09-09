#!/usr/bin/env node
// @yarch/create-admin 工程生成器（W6-W9）—— maven archetype / cookiecutter 模式的 web 对偶，
// 与 golang cmd/yarch-init 同构：
//   模板资产（templates/admin-{semi,antd,arco}，{{var}} 占位，不要求自身可安装）
//   + 交互问答（W7）+ 通用渲染引擎（W8：目录/文件/内容全量替换）+ archetype.json 变量声明（W9）。
// 工程正确性由「生成后冒烟」保证（CI：生成 → install → tsc → build）。
//
// 用法（本仓未发版阶段，本地）：
//
//	node stacks/web/packages/create/bin/create-admin.mjs ysaas-console
//	node stacks/web/packages/create/bin/create-admin.mjs ysaas-console --ui antd --yes
//
// @yarch/create-admin 发版后无需 clone 本仓（npm create = npm exec）：
//
//	npm create @yarch/admin@latest ysaas-console -- --ui semi

import { readFileSync, writeFileSync, readdirSync, mkdirSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, join, resolve } from "node:path";
import readline from "node:readline/promises";
import { parseRegistryMd } from "./registry.mjs";

const PKG_ROOT = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const UI_TIERS = {
  semi: "Semi Design（抖音系，默认档 W1）",
  antd: "antd（蚂蚁系）",
  arco: "Arco Design（字节系）",
};
// 微前端模板档（W10）：载器 micro-app（contract/README 登记表默认档），UI 档本批固定 semi，其余档触发式。
const MICRO_KINDS = {
  base: "微前端基座（micro-frontend.md 一-1：全局唯一，承载布局/菜单/登录态/路由分发/错误兜底）",
  sub: "微前端子应用（micro-frontend.md 一-3：独立·集成双运行形态，只做域内页面）",
};
// @yarch 底座版本依赖（--deps version 形态）；与本仓发版版本同步 bump。
const YARCH_VERSION = "^0.2.0";

const servicePattern = /^[a-z][a-z0-9-]{1,31}$/;
// 禁裸通用词（registry.md 一-1/一-2 口径摘录，与 golang yarch-init 同款；完整表以 contract/registry.md 为准）。
const genericWords = new Set([
  "api", "app", "service", "server", "backend", "web", "admin",
  "main", "common", "system", "demo", "user", "gateway",
]);

// 渲染时跳过的模板资产（变量声明不进生成物；构建产物与依赖不应存在于资产，防御性跳过）。
const SKIP_FILES = new Set(["archetype.json"]);
const SKIP_DIRS = new Set(["node_modules", "dist"]);
const PLACEHOLDER = /\{\{\s*([a-zA-Z][a-zA-Z0-9]*)\s*\}\}/g;

function usage() {
  return [
    "用法：create-admin <工程名|输出目录> [--name <名>] [--ui semi|antd|arco] [--micro base|sub]",
    "              [--registry <registry.md 路径>] [--desc <描述>] [--port <端口>] [--proxy <目标>]",
    "              [--group <GitLab分组>] [--deps file|version] [--src <模板根>] [--out <目录>] [--yes]",
    "  --micro   微前端模板档（W10）：base=基座 / sub=子应用；缺省=单应用 admin（不受微前端规约约束）",
    "  --registry  微前端名称核对用的 contract/registry.md 路径（默认用包内快照 bin/registry-snapshot.json）",
    "  --yes     非交互：缺省项全走默认值（CI 用）",
    "  --deps    @yarch 底座依赖形态：version（默认，^0.2.0，需 @yarch 已发 npm）| file（发版前本地过渡）",
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
    return "须过 registry.md 一-1 校验：^[a-z][a-z0-9-]{1,31}$（小写字母数字短横线，字母开头，2~32 字符）";
  }
  if (genericWords.has(name)) {
    return "裸通用词禁止单独成名，须带项目/领域前缀或后缀消歧（如 ysaas-admin）——见 registry.md 一-2";
  }
  if (name.includes('"') || name.includes("\\")) {
    return '不得包含双引号与反斜杠（要写进 package.json）';
  }
  return null;
}

// —— 微前端应用名校验（micro-frontend.md 二-1/二-4，十三表「创建期」机检）——

// 快照来源：--registry 指向的 registry.md（CI/内网用最新表）优先，否则用随包分发的快照。
function loadRegistry(registryArg) {
  if (registryArg) {
    return parseRegistryMd(readFileSync(resolve(registryArg), "utf8"));
  }
  try {
    const snapshot = JSON.parse(readFileSync(join(PKG_ROOT, "bin", "registry-snapshot.json"), "utf8"));
    return {
      serviceNames: new Set(snapshot.serviceNames),
      appNames: new Set(snapshot.appNames),
    };
  } catch {
    return null;
  }
}

function validateMicroName(name, registry) {
  const baseErr = validateName(name);
  if (baseErr) return baseErr;
  if (!registry) {
    return "无法核对 registry.md（缺 bin/registry-snapshot.json 且未传 --registry <path>）——微前端应用名必须核对登记表（二-1）";
  }
  if (!name.includes("-")) {
    return "微前端应用名须由「服务名-用途/域」两段及以上构成（micro-frontend.md 二-1，正例 ysaas-billing）";
  }
  const service = name.split("-")[0];
  if (!registry.serviceNames.has(service)) {
    return `首段 ${service} 不在 registry.md 二节已登记服务名表（二-1）；若快照过期，用 --registry 指向最新 contract/registry.md`;
  }
  if (registry.appNames.has(name)) {
    return `应用名 ${name} 已在 registry.md 四节登记（二-4：跨项目不得重名）`;
  }
  return null;
}

async function askText(rl, question, { fallback = "", validate = null, optional = false } = {}) {
  for (;;) {
    const hint = fallback ? `（默认 ${fallback}）` : optional ? "（可空）" : "";
    const answer = (await rl.question(`${question}${hint}: `)).trim();
    const value = answer || fallback;
    if (!value && !optional) {
      console.log("  ✗ 必填，请输入");
      continue;
    }
    if (value && validate) {
      const err = validate(value);
      if (err) {
        console.log(`  ✗ ${err}`);
        continue;
      }
    }
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
      for (const m of rendered.matchAll(PLACEHOLDER)) {
        leftovers.push(`${target}: {{${m[1]}}}`);
      }
      writeFileSync(target, rendered);
      count++;
    }
  }
  return count;
}

function fatal(message) {
  console.error(`create-admin: ${message}`);
  process.exit(1);
}

async function main() {
  const args = parseArgs(process.argv.slice(2));
  const nameArg = args.name || (args._.length > 0 && !args._[0].startsWith("/") ? args._[0] : null);

  let micro = args.micro;
  if (micro !== undefined && !(micro in MICRO_KINDS)) {
    fatal(`--micro 须为 ${Object.keys(MICRO_KINDS).join("|")}，当前：${micro}`);
  }
  const ui = args.ui ?? "semi";
  if (!(ui in UI_TIERS)) fatal(`--ui 须为 ${Object.keys(UI_TIERS).join("|")}，当前：${ui}`);
  if (micro && ui !== "semi") {
    fatal("微前端模板档本批仅 semi（W1 默认档）；其余 UI 档触发式扩展（PLAN.md W10）");
  }

  const interactive = !args.yes && process.stdin.isTTY;
  if (!nameArg && !interactive) fatal(`工程名必填（或用 --yes 走交互外模式）：\n${usage()}`);

  // 微前端形态才需要登记表核对（二-1 首段=已登记服务名；单应用仍走一-1/一-2 正则+禁裸词）。
  const needsRegistry = micro !== undefined || interactive; // 交互形态问答后可能选 micro
  const registry = needsRegistry ? loadRegistry(args.registry) : null;

  const rl = interactive ? readline.createInterface({ input: process.stdin, output: process.stdout }) : null;
  try {
    let name = nameArg;
    let description = args.desc;
    let port = args.port;
    let proxyTarget = args.proxy;
    let group = args.group;

    if (interactive) {
      console.log("yarch web 工程生成器（@yarch/create-admin）\n");
      if (micro === undefined) {
        const kindIdx = await askSelect(
          rl,
          "工程形态（micro-frontend.md 一：单应用不受微前端规约约束；基座全局唯一、子应用双运行形态）",
          [
            "单应用 admin（默认，npm create @yarch/admin 常规形态）",
            "微前端基座 base（--micro base）",
            "微前端子应用 sub（--micro sub）",
          ],
          0,
        );
        micro = kindIdx === 0 ? undefined : kindIdx === 1 ? "base" : "sub";
      }
      const validator = micro ? (v) => validateMicroName(v, registry) : validateName;
      const nameLabel = micro
        ? "应用名（= 路由前缀 = storage/事件前缀，二-2 一名三用；首段=已登记服务名）"
        : "工程名（= npm 包名 = registry 服务名）";
      if (!name) name = await askText(rl, nameLabel, { validate: validator });
      else if (validator(name)) fatal(validator(name));
      if (!description) {
        description = await askText(rl, "工程描述", {
          fallback: `${name}：基于 yarch web 脚手架生成的${micro ? (micro === "base" ? "微前端基座" : "微前端子应用") : "中后台"}工程`,
          validate: (v) => (v.includes('"') || v.includes("\\") ? "不得包含双引号与反斜杠（要写进 package.json）" : null),
        });
      }
      if (!port) {
        port = await askText(rl, "dev 端口", {
          fallback: micro === "sub" ? "5174" : "5173",
          validate: (v) => (/^\d{4,5}$/.test(v) && Number(v) >= 1024 && Number(v) <= 65535 ? null : "1024~65535 的数字端口号"),
        });
      }
      if (!proxyTarget) {
        proxyTarget = await askText(rl, "API 反向代理目标（/api/v1）", {
          fallback: "http://localhost:8080",
          validate: (v) => (/^https?:\/\/.+/.test(v) ? null : "须为 http(s):// 开头"),
        });
      }
      if (group === undefined) group = await askText(rl, "GitLab 分组（用于登记提示）", { optional: true });
    }

    if (!name) fatal(`工程名缺失。\n${usage()}`);
    const nameErr = micro ? validateMicroName(name, registry) : validateName(name);
    if (nameErr) fatal(`工程名 ${JSON.stringify(name)} ${nameErr}`);
    description ??= `${name}：基于 yarch web 脚手架生成的${micro ? (micro === "base" ? "微前端基座" : "微前端子应用") : "中后台"}工程`;
    port ??= micro === "sub" ? "5174" : "5173";
    proxyTarget ??= "http://localhost:8080";
    group ??= "";
    if (!/^\d{4,5}$/.test(port) || Number(port) < 1024 || Number(port) > 65535) {
      fatal(`端口 ${port} 无效：须为 1024~65535 的数字`);
    }
    if (!/^https?:\/\/.+/.test(proxyTarget)) {
      fatal(`代理目标 ${proxyTarget} 无效：须为 http(s):// 开头`);
    }

    // @yarch 底座依赖形态（W8）：version = 版本依赖（标准形态，@yarch/contract、@yarch/react 已发 npm）；
    // file: 绝对路径 = golang replace 行对偶（发版前本地过渡，yarch 源码变更后重跑 pnpm install 刷新）。
    const depsMode = args.deps === "file" ? "file" : "version";
    const yarchContractDep = depsMode === "version" ? YARCH_VERSION : `file:${resolve(PKG_ROOT, "../contract")}`;
    const yarchReactDep = depsMode === "version" ? YARCH_VERSION : `file:${resolve(PKG_ROOT, "../react")}`;

    const vars = {
      packageName: name,
      appName: name,
      service: name.split("-")[0],
      description,
      port,
      proxyTarget,
      yarchContractDep,
      yarchReactDep,
    };

    const outDir = resolve(args.out || (args._.length > 0 ? args._[args._.length - 1] : name));
    let existing;
    try {
      existing = readdirSync(outDir);
    } catch {
      existing = null; // 不存在，直接生成
    }
    if (existing && existing.length > 0) fatal(`输出目录 ${outDir} 非空`);

    const templateDir = resolve(
      args.src || join(PKG_ROOT, "templates", micro ? `${micro}-semi` : `admin-${ui}`),
    );
    try {
      readFileSync(join(templateDir, "archetype.json"));
    } catch {
      fatal(`模板目录不可达或缺少 archetype.json：${templateDir}（在 yarch 仓 stacks/web 下运行，或用 --src 指定）`);
    }

    const leftovers = [];
    const n = renderTree(templateDir, outDir, vars, leftovers);
    if (leftovers.length > 0) {
      fatal(`模板存在未声明变量（模板与变量集漂移）：\n  ${leftovers.join("\n  ")}`);
    }

    const tierDesc = JSON.parse(readFileSync(join(templateDir, "archetype.json"), "utf8")).description;
    const groupHint = group ? `（属主建议：${group}/${name}）` : "";
    const tierLabel = micro ? `${micro}-semi 档` : `admin-${ui} 档`;
    const registryHint = micro
      ? `2. 应用名已置为 ${JSON.stringify(name)}——去 yarch 仓 contract/registry.md 四节前端应用名登记表登记${groupHint}
  3. ${micro === "base"
        ? "基座：micro-apps.config.ts 是子应用入口登记表（十二-2，git 纳管）；子应用接入只改此表"
        : "子应用：在基座仓 micro-apps.config.ts 登记入口（十二-2）；pnpm dev 独立运行 / pnpm dev:micro 被基座加载（十一）"}`
      : `2. 服务名已置为 ${JSON.stringify(name)}——去 yarch 仓 contract/registry.md 登记表登记${groupHint}`;
    console.log(`✅ 已生成 ${outDir}（${n} 个文件，${tierLabel}）—— ${tierDesc}

下一步：
  1. cd ${outDir} && pnpm install && pnpm dev        # http://localhost:${port}
${registryHint}
  ${micro ? "4" : "3"}. @yarch 底座为 ${depsMode === "version" ? `${YARCH_VERSION} 版本依赖（升级 = pnpm update @yarch/contract @yarch/react）` : "file: 本地依赖（发版前过渡；正式发版后改用默认 version 形态重新生成或手动替换）"}
`);
  } finally {
    if (rl) rl.close();
  }
}

main().catch((err) => fatal(err.message));
