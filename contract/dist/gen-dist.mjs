#!/usr/bin/env node
// contract/dist/gen-dist.mjs —— 契约机器可读出口生成器（P1 主引擎）。
// 权威仍是 contract/api/*.md；本脚本从 markdown 表格派生 JSON 工件，双向漂移由 CI 拒绝
// （同 registry-snapshot 机制：重跑本脚本后 git diff 非空 = 漂移）。
// 用法：node contract/dist/gen-dist.mjs（零依赖，Node ≥20）

import { readFileSync, writeFileSync, mkdirSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const DIST = dirname(fileURLToPath(import.meta.url));
const API = join(DIST, "..", "api");

function readMd(name) {
  return readFileSync(join(API, name), "utf8");
}

/** 解析 markdown 表格行 → cells 数组（跳过分隔行与表头；`\|` 转义竖线以占位符保护） */
function tableRows(md, { from = 0 } = {}) {
  const rows = [];
  for (const line of md.slice(from).split("\n")) {
    const t = line.trim();
    if (!t.startsWith("|") || !t.endsWith("|")) continue;
    if (/^[\s|:-]+$/.test(t)) continue; // |---|---|
    const cells = t
      .slice(1, -1)
      .replace(/\\\|/g, "\x00")
      .split("|")
      .map((c) => c.trim().replace(/\x00/g, "|"));
    rows.push(cells);
  }
  return rows;
}

function stripCode(s) {
  return s.replace(/`/g, "").trim();
}

// ---------- error-codes.json ----------
function genErrorCodes() {
  const md = readMd("error-codes.md");
  const seg1 = md.indexOf("## 通用段 1xxx");
  const seg2 = md.indexOf("## 认证与权限段 2xxx");
  const impl = md.indexOf("## 实现规则");
  if (seg1 < 0 || seg2 < 0 || impl < 0) throw new Error("error-codes.md 章节结构漂移（通用段/认证段/实现规则）");

  const codes = [];
  const parse = (section, segment, end) => {
    for (const cells of tableRows(md.slice(section, end))) {
      const code = stripCode(cells[0]);
      if (!/^\d{4}$/.test(code)) continue;
      const key = stripCode(cells[1]);
      if (!key || key.startsWith("（")) continue; // 留空段位（1003）不进机器表
      codes.push({
        code: Number(code),
        key,
        message: stripCode(cells[2]),
        http: Number(stripCode(cells[3])),
        segment,
      });
    }
  };
  parse(seg1, "1xxx", seg2);
  parse(seg2, "2xxx", impl);
  if (codes.length !== 13) throw new Error(`13 码全表解析异常：得到 ${codes.length} 条`);

  // 段位分配表的 0 成功行（key 由四栈方言持有：OK / SUCCESS / CODE_SUCCESS）
  const alloc = tableRows(md.slice(0, seg1));
  const row0 = alloc.find((c) => stripCode(c[0]) === "0");
  if (!row0) throw new Error("段位分配表缺少 0 成功行");

  return {
    version: 1,
    source: "contract/api/error-codes.md",
    success: { code: 0, message: stripCode(row0[2]) },
    // key 即稳定标识（EP3-R i18n 就绪位）：error-codes.json 必含 code/key/message 三元组
    codes,
  };
}

// ---------- envelope.schema.json ----------
const TYPE_MAP = {
  "int32": { type: "integer", format: "int32" },
  "int64": { type: "integer", format: "int64" },
  "string": { type: "string" },
  "T | null": { description: "业务负载：任意 JSON；code != 0 时必须为 null（结构断言见各栈 conformance）" },
  "T[]": { type: "array", items: {} },
  "string?": { type: "string" },
};

function parseFieldTable(md, startMarker, endMarker) {
  const start = md.indexOf(startMarker);
  const end = md.indexOf(endMarker, start);
  if (start < 0 || end < 0) throw new Error(`rest-response.md 章节漂移：${startMarker}`);
  const rows = tableRows(md.slice(start, end));
  const fields = [];
  for (const cells of rows) {
    const name = stripCode(cells[0]);
    if (!/^[a-zA-Z][a-zA-Z0-9]*$/.test(name)) continue; // 跳过表头
    const rawType = stripCode(cells[1]);
    const schema = TYPE_MAP[rawType];
    if (!schema) throw new Error(`rest-response.md 未知字段类型：${rawType}（${name}）`);
    const required = cells[2] === "是" || !rawType.endsWith("?");
    fields.push({ name, schema: { ...schema }, required });
  }
  return fields;
}

function toSchema(fields) {
  const properties = {};
  const required = [];
  for (const f of fields) {
    properties[f.name] = f.schema;
    if (f.required) required.push(f.name);
  }
  return { properties, required };
}

function genEnvelopeSchema() {
  const md = readMd("rest-response.md");
  const envelope = toSchema(parseFieldTable(md, "统一为四字段", "## 与 HTTP 状态码的关系"));
  const page = toSchema(parseFieldTable(md, "## 分页负载形状", "## 网关故障面"));
  if (envelope.required.length !== 4) throw new Error("信封必填字段数 != 4（四字段形状漂移）");
  if (page.required.length !== 4) throw new Error("PageData 必填字段数 != 4");

  return {
    $schema: "http://json-schema.org/draft-07/schema#",
    $id: "https://github.com/ydonghao/yarch/contract/dist/envelope.schema.json",
    title: "yarch RestResponse / PageData（由 contract/api/rest-response.md 派生）",
    definitions: {
      RestResponse: {
        type: "object",
        additionalProperties: false,
        required: envelope.required,
        properties: envelope.properties,
        allOf: [
          {
            if: { properties: { code: { type: "integer", not: { const: 0 } } }, required: ["code"] },
            then: { properties: { data: { type: "null" } } },
          },
        ],
      },
      PageData: {
        type: "object",
        additionalProperties: false,
        required: page.required,
        properties: page.properties,
      },
    },
  };
}

// ---------- 输出（确定性序列化：2 空格 + 尾换行，diff 友好） ----------
mkdirSync(DIST, { recursive: true });
const ec = genErrorCodes();
writeFileSync(join(DIST, "error-codes.json"), JSON.stringify(ec, null, 2) + "\n");
const schema = genEnvelopeSchema();
writeFileSync(join(DIST, "envelope.schema.json"), JSON.stringify(schema, null, 2) + "\n");
console.log(`✅ contract/dist 重新生成：error-codes.json（${ec.codes.length} 码 + success）+ envelope.schema.json`);
