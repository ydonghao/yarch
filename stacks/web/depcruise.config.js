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
  ],
};
