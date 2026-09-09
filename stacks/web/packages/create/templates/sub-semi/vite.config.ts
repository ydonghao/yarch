import { fileURLToPath } from "node:url";
import react from "@vitejs/plugin-react";
import { defineConfig, type Plugin } from "vite";

/**
 * 双构建模式（十一-1/十一-2）：
 *   独立（默认）  = 全量自足，pnpm dev / build；
 *   集成（integrated）= 供给层切 __YARCH_SUPPLY__ 实现；react 系与 @yarch/contract
 *                      由基座经 window 全局提供（八-1/八-2）——集成产物 dist-micro/ 中框架
 *                      与 contract 体积消失（十三表八-1/八-2 机检的构建口径）。
 * 集成模式不挂 plugin-react（fast-refresh preamble 与 micro-app 沙箱互扰；esbuild 按 tsconfig 自动转 JSX）。
 */

/**
 * 框架运行时 shim（集成构建专用，八-2）：构建期从真实安装的包内省全部导出名，
 * 生成 `export const X = globalThis.__YARCH_REACT__["<pkg>"].X` 虚拟模块——
 * 覆盖面与所装 react 系版本逐名全等（三方库如 semi-ui 的任意具名 import 均可解析）。
 * 类型检查不受影响（tsconfig 仍指向真实包类型）。
 */
const RUNTIME_SPECS: Record<string, string> = {
  // import 说明符 → 基座 __YARCH_REACT__ 下注册名（见 base-semi src/micro/shared.ts）
  react: "react",
  "react-dom": "react-dom",
  "react-dom/client": "react-dom/client",
  "react/jsx-runtime": "react/jsx-runtime",
  "react/jsx-dev-runtime": "react/jsx-dev-runtime",
  "react-router": "react-router",
  "react-router-dom": "react-router-dom",
};
const VIRTUAL_PREFIX = "\0yarch-runtime-shim:";

function yarchRuntimeShims(): Plugin {
  return {
    name: "yarch-runtime-shims",
    // 解析不走插件 resolveId（bare import 在 alias 之后已被 vite 内置解析器接管）——
    // 由下方 resolve.alias 把说明符改写为虚拟 id，本插件只负责 load 生成完整导出面。
    async load(id) {
      if (!id.startsWith(VIRTUAL_PREFIX)) return null;
      const spec = id.slice(VIRTUAL_PREFIX.length);
      const globalName = RUNTIME_SPECS[spec];
      const exported = await import(spec);
      // CJS 互操作会泄漏 "module.exports" 等非标识符键（cjs-module-lexer 伪影），只保留合法导出名
      const names = Object.keys(exported).filter((n) => n !== "default" && /^[A-Za-z_$][A-Za-z0-9_$]*$/.test(n));
      const lines = [
        `const ns = (globalThis).__YARCH_REACT__?.[${JSON.stringify(globalName)}];`,
        `if (!ns) throw new Error(${JSON.stringify(
          `集成构建须运行在基座内：__YARCH_REACT__[${globalName}] 未注册（八-2 框架运行时由基座统一提供）`,
        )});`,
        ...names.map((n) => `export const ${n} = ns[${JSON.stringify(n)}];`),
        ...("default" in exported ? ["export default ns.default;"] : []),
      ];
      return lines.join("\n");
    },
  };
}

const contractShim = fileURLToPath(new URL("./src/supply/shims/contract.ts", import.meta.url));

export default defineConfig(({ mode }) => ({
  plugins: mode === "integrated" ? [yarchRuntimeShims()] : [react()],
  // 十二-4：产物资源挂在本应用前缀下（独立模式 URL 形态与集成一致，四-1 全站无方言）
  base: "/{{appName}}/",
  server: {
    host: "127.0.0.1", // 钉死 IPv4 回环：与默认入口 URL/CI 同口径（vite 默认 localhost 在 macOS 为 ::1 only）
    port: {{port}},
    cors: true, // 基座跨域加载本应用 dev 产物（micro-app fetch 入口与资源）
    proxy: {
      "/api/v1": {
        target: "{{proxyTarget}}",
        changeOrigin: true,
      },
    },
  },
  resolve: {
    alias: [
      // 供给层实现按构建模式切换（vite 不读 tsconfig paths，须显式 alias；tsconfig paths 仅供类型检查）
      {
        find: /^@supply$/,
        replacement: fileURLToPath(
          new URL(mode === "integrated" ? "./src/supply/integrated.ts" : "./src/supply/standalone.ts", import.meta.url),
        ),
      },
      ...(mode === "integrated"
        ? [
            ...Object.keys(RUNTIME_SPECS).map((spec) => ({
              find: new RegExp(`^${spec.replace("/", "\\/")}$`),
              replacement: VIRTUAL_PREFIX + spec,
            })),
            { find: /^@yarch\/contract$/, replacement: contractShim },
          ]
        : []),
    ],
  },
  build: { outDir: mode === "integrated" ? "dist-micro" : "dist" },
  preview: { cors: true }, // 基座跨域加载集成产物（micro-app fetch，与 dev server.cors 同口径）
}));
