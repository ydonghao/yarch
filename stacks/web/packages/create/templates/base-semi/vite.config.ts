import react from "@vitejs/plugin-react";
import { defineConfig } from "vite";

export default defineConfig({
  plugins: [react()],
  server: {
    host: "127.0.0.1", // 钉死 IPv4 回环：与默认入口 URL/CI 同口径（vite 默认 localhost 在 macOS 为 ::1 only）
    port: {{port}},
    proxy: {
      // 后端 API 反向代理（对接 yarch java/golang 栈的信封接口）
      "/api/v1": {
        target: "{{proxyTarget}}",
        changeOrigin: true,
      },
    },
  },
});
