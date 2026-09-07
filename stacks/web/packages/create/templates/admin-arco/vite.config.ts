import react from "@vitejs/plugin-react";
import { defineConfig } from "vite";

export default defineConfig({
  plugins: [react()],
  server: {
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
