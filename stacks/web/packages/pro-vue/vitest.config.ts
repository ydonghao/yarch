import { defineConfig } from "vitest/config";

// 无 SFC/JSX——组件为 h() 渲染函数纯 .ts（源码直出形态，消费方零配置）。
// element-plus 及其依赖链内联走 vite 转换：CJS 直转（async-validator）在默认外部化下
// reject 值丢失（interop 缺失 → EP form 校验状态机拿不到 {errors, fields}）。
export default defineConfig({
  test: {
    environment: "jsdom",
    globals: true,
    server: {
      deps: {
        inline: [/element-plus/],
      },
    },
  },
});
