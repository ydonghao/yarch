/** 前端版 ArchUnit：依赖方向机检（W3）—— pages 薄入口、features 归业务、禁反向依赖
 *  path 匹配的是以仓库相对路径传入的完整模块路径（如 examples/admin-demo/src/pages/x.tsx），
 *  规则锚点因此用 (^|/)src/… 命中任意工程前缀下的 src 目录。 */
module.exports = {
  forbidden: [
    {
      name: "pages-are-thin",
      comment: "pages 只组合 features，不得直调域 api",
      severity: "error",
      from: { path: "(^|/)src/pages/" },
      to: { path: "(^|/)src/features/[^/]+/api\\.ts$" },
    },
    {
      name: "no-reverse-deps",
      comment: "ui/components 不得反向依赖 features/pages",
      severity: "error",
      from: { path: "(^|/)src/(ui|components)/" },
      to: { path: "(^|/)src/(features|pages)/" },
    },
    {
      name: "contract-stays-framework-free",
      comment: "@yarch/contract 零框架依赖（React/Vue 通吃的前提）",
      severity: "error",
      from: { path: "^packages/contract" },
      to: { path: "(react|vue)" },
    },
    {
      name: "micro-loader-only-in-base",
      comment: "微前端载器只属基座（micro-frontend.md 三-2/十一-2）——子应用模板/工程禁 import @micro-zoe/micro-app",
      severity: "error",
      from: { path: "(templates/sub-semi|examples/ysaas-billing)/src/" },
      to: { path: "^(node_modules/)?@micro-zoe/micro-app" },
    },
    {
      name: "micro-navigator-register-only-in-base",
      comment: "导航端口注册唯一归基座（十-2）——子应用业务层禁 import @yarch/react（供给层 supply/ 独立模式注册除外）",
      severity: "error",
      from: { path: "(templates/sub-semi|examples/ysaas-billing)/src/(app|features|pages|layouts|ui|events)/" },
      to: { path: "^(node_modules/)?@yarch/react" },
    },
    {
      name: "micro-no-cross-app-imports",
      comment: "子应用禁直连基座源码（十二-2 应用边界）——共享只经基座 window 全局供给层（八-1/八-2）",
      severity: "error",
      from: { path: "(templates/sub-semi|examples/ysaas-billing)/src/" },
      to: { path: "(templates/base-semi|examples/ysaas-console)/src/" },
    },
  ],
};
