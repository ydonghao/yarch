/**
 * CI 发布脚本（miniprogram.md 一-2：构建发布唯一走 miniprogram-ci）
 * 用法：
 *   node scripts/ci.js preview   # 预览（生成二维码）
 *   node scripts/ci.js upload    # 上传体验版/正式版
 * 环境变量：WX_APPID / WX_PRIVATE_KEY（私钥文件路径或内容）
 * CI 口径（miniprogram.md 五 / S3）：tsc 类型检查 + packNpm npm 构建 + 包体体积断言
 */
const { Project } = require("miniprogram-ci");
const path = require("path");

const mode = process.argv[2];
if (mode !== "preview" && mode !== "upload") {
  console.error("Usage: node scripts/ci.js [preview|upload]");
  process.exit(1);
}

const appId = process.env.WX_APPID || "{{appId}}";
const privateKeyPath = process.env.WX_PRIVATE_KEY || "";

if (!appId || appId === "{{appId}}") {
  console.error("WX_APPID 环境变量未设置");
  process.exit(1);
}

const project = new Project({
  appid: appId,
  type: "miniProgram",
  projectPath: path.resolve(__dirname, ".."),
  privateKeyPath,
  ignores: ["node_modules/**", "scripts/**"],
});

async function run() {
  if (mode === "preview") {
    // 预览：CI 体积断言在产物后跑（主包 ≤2MB / 整包 ≤30MB，miniprogram.md 二-1）
    const result = await miniprogramCi.preview({
      project,
      desc: `preview ${new Date().toISOString()}`,
      setting: { es6: true, minify: true },
      onProgressUpdate: () => {},
    });
    console.log("预览完成", result.something);
  } else {
    const uploadResult = await miniprogramCi.upload({
      project,
      version: process.env.npm_package_version || "0.0.0",
      desc: `upload ${new Date().toISOString()}`,
      setting: { es6: true, minify: true },
    });
    console.log("上传完成", uploadResult.subPackageInfo || "ok");
  }
}

const miniprogramCi = require("miniprogram-ci");
run().catch((err) => {
  console.error(err);
  process.exit(1);
});
