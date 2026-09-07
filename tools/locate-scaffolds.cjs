#!/usr/bin/env node
/**
 * yarch 模板定位器：给定一个栈名，打印该栈脚手架在本仓的绝对路径。
 * 业务工程与 AI 工具用它定位模板；将来长成 `yarch init` CLI。
 *
 * 用法：
 *   node locate-scaffolds.cjs            # 列出所有栈
 *   node locate-scaffolds.cjs java       # 打印 stacks/java 的绝对路径
 *   node locate-scaffolds.cjs --json     # 机器可读输出
 */

'use strict';

const fs = require('node:fs');
const path = require('node:path');

const STACKS = ['java', 'golang', 'rust', 'web', 'python'];
const CLIENTS = ['mobile', 'miniprogram', 'desktop'];

function repoRoot() {
  let dir = __dirname;
  for (;;) {
    if (fs.existsSync(path.join(dir, 'contract')) && fs.existsSync(path.join(dir, 'stacks'))) {
      return dir;
    }
    const parent = path.dirname(dir);
    if (parent === dir) {
      throw new Error('not inside a yarch checkout');
    }
    dir = parent;
  }
}

function resolveScaffold(name) {
  const root = repoRoot();
  const stackDir = path.join(root, 'stacks', name);
  if (fs.existsSync(stackDir)) return { kind: 'stack', name, path: stackDir };
  const clientDir = path.join(root, 'clients', name);
  if (fs.existsSync(clientDir)) return { kind: 'client', name, path: clientDir };
  return null;
}

function main(argv) {
  const asJson = argv.includes('--json');
  const names = argv.filter((arg) => !arg.startsWith('--'));

  if (names.length === 0) {
    const all = [...STACKS, ...CLIENTS]
      .map(resolveScaffold)
      .filter(Boolean);
    if (asJson) {
      console.log(JSON.stringify(all, null, 2));
    } else {
      console.log('yarch scaffolds:');
      for (const entry of all) {
        console.log(`  [${entry.kind}] ${entry.name} -> ${entry.path}`);
      }
    }
    return 0;
  }

  const misses = [];
  const hits = [];
  for (const name of names) {
    const entry = resolveScaffold(name);
    if (entry) hits.push(entry);
    else misses.push(name);
  }
  if (asJson) {
    console.log(JSON.stringify({ hits, misses }, null, 2));
  } else {
    for (const entry of hits) console.log(entry.path);
  }
  if (misses.length > 0) {
    console.error(`unknown scaffold(s): ${misses.join(', ')}; known: ${[...STACKS, ...CLIENTS].join(', ')}`);
    return 1;
  }
  return 0;
}

process.exit(main(process.argv.slice(2)));
