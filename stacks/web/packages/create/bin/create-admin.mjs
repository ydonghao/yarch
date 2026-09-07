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

const PKG_ROOT = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const UI_TIERS = {
  semi: "Semi Design（抖音系，默认档 W1）",
  antd: "antd（蚂蚁系）",
  arco: "Arco Design（字节系）",
};

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
    "用法：create-admin <工程名|输出目录> [--name <名>] [--ui semi|antd|arco] [--desc <描述>]",
    "              [--port <端口>] [--proxy <目标>] [--group <GitLab分组>] [--deps file|version]",
    "              [--src <模板根>] [--out <目录>] [--yes]",
    "  --yes     非交互：缺省项全走默认值（CI 用）",
    "  --deps    @yarch 底座依赖形态：version（默认，^0.1.0，需 @yarch 已发 npm）| file（发版前本地过渡）",
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

  const ui = args.ui ?? "semi";
  if (!(ui in UI_TIERS)) fatal(`--ui 须为 ${Object.keys(UI_TIERS).join("|")}，当前：${ui}`);

  const interactive = !args.yes && process.stdin.isTTY;
  if (!nameArg && !interactive) fatal(`工程名必填（或用 --yes 走交互外模式）：\n${usage()}`);

  const rl = interactive ? readline.createInterface({ input: process.stdin, output: process.stdout }) : null;
  try {
    let name = nameArg;
    let description = args.desc;
    let port = args.port;
    let proxyTarget = args.proxy;
    let group = args.group;

    if (interactive) {
      console.log("yarch web 工程生成器（@yarch/create-admin）\n");
      if (!name) name = await askText(rl, "工程名（= npm 包名 = registry 服务名）", { validate: validateName });
      else if (validateName(name)) fatal(validateName(name));
      if (!description) {
        description = await askText(rl, "工程描述", {
          fallback: `${name}：基于 yarch web 脚手架生成的中后台工程`,
          validate: (v) => (v.includes('"') || v.includes("\\") ? "不得包含双引号与反斜杠（要写进 package.json）" : null),
        });
      }
      if (!port) {
        port = await askText(rl, "dev 端口", {
          fallback: "5173",
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
    const nameErr = validateName(name);
    if (nameErr) fatal(`工程名 ${JSON.stringify(name)} ${nameErr}`);
    description ??= `${name}：基于 yarch web 脚手架生成的中后台工程`;
    port ??= "5173";
    proxyTarget ??= "http://localhost:8080";
    group ??= "";
    if (!/^\d{4,5}$/.test(port) || Number(port) < 1024 || Number(port) > 65535) {
      fatal(`端口 ${port} 无效：须为 1024~65535 的数字`);
    }
    if (!/^https?:\/\/.+/.test(proxyTarget)) {
      fatal(`代理目标 ${proxyTarget} 无效：须为 http(s):// 开头`);
    }

    // @yarch 底座依赖形态（W8）：version = ^0.1.0（标准形态，@yarch/contract、@yarch/react 已发 npm）；
    // file: 绝对路径 = golang replace 行对偶（发版前本地过渡，yarch 源码变更后重跑 pnpm install 刷新）。
    const depsMode = args.deps === "file" ? "file" : "version";
    const yarchContractDep =
      depsMode === "version" ? "^0.1.0" : `file:${resolve(PKG_ROOT, "../contract")}`;
    const yarchReactDep = depsMode === "version" ? "^0.1.0" : `file:${resolve(PKG_ROOT, "../react")}`;

    const vars = { packageName: name, appName: name, description, port, proxyTarget, yarchContractDep, yarchReactDep };

    const outDir = resolve(args.out || (args._.length > 0 ? args._[args._.length - 1] : name));
    let existing;
    try {
      existing = readdirSync(outDir);
    } catch {
      existing = null; // 不存在，直接生成
    }
    if (existing && existing.length > 0) fatal(`输出目录 ${outDir} 非空`);

    const templateDir = resolve(args.src || join(PKG_ROOT, "templates", `admin-${ui}`));
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
    console.log(`✅ 已生成 ${outDir}（${n} 个文件，admin-${ui} 档）—— ${tierDesc}

下一步：
  1. cd ${outDir} && pnpm install && pnpm dev        # http://localhost:${port}
  2. 服务名已置为 ${JSON.stringify(name)}——去 yarch 仓 contract/registry.md 登记表登记${groupHint}
  3. @yarch 底座为 ${depsMode === "version" ? "^0.1.0 版本依赖（升级 = pnpm update @yarch/contract @yarch/react）" : "file: 本地依赖（发版前过渡；正式发版后改用默认 version 形态重新生成或手动替换）"}
`);
  } finally {
    if (rl) rl.close();
  }
}

main().catch((err) => fatal(err.message));
