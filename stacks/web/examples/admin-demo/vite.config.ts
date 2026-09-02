import { fileURLToPath } from "node:url";
import react from "@vitejs/plugin-react";
import { defineConfig } from "vite";

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5174,
    proxy: {
      // 对接 java examples-ddd（port 8081）
      "/api/v1": {
        target: "http://localhost:8081",
        changeOrigin: true,
      },
      "/actuator": {
        target: "http://localhost:8081",
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
