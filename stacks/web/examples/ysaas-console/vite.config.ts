import { fileURLToPath } from "node:url";
import react from "@vitejs/plugin-react";
import { defineConfig } from "vite";

// 仓内 workspace 成员扩展（admin-demo 同款）：vite 不读 tsconfig paths，@yarch 源码依赖走 alias
export default defineConfig({
  plugins: [react()],
  server: {
    host: "127.0.0.1", // 钉死 IPv4 回环：与默认入口 URL/CI 同口径（vite 默认 localhost 在 macOS 为 ::1 only）
    port: 5173,
    proxy: {
      // 后端 API 反向代理（对接 yarch java/golang 栈的信封接口）
      "/api/v1": {
        target: "http://localhost:8080",
        changeOrigin: true,
      },
    },
  },
  resolve: {
    alias: {
      "@yarch/contract": fileURLToPath(new URL("../../packages/contract/src/index.ts", import.meta.url)),
      "@yarch/react": fileURLToPath(new URL("../../packages/react/src/index.ts", import.meta.url)),
    },
  },
});
