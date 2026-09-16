// registry.md 解析（clients 生成器专用，扩五六节；与 web 栈 registry.mjs 同源模式）。
// 二节=服务名 / 四节=前端应用名 / 五节=移动 App 名 / 六节=小程序与小游戏名。
export function parseRegistryMd(md) {
  const serviceNames = new Set();
  const appNames = new Set();
  const mobileAppNames = new Set();
  const clientNames = new Set(); // 小程序 + 小游戏（registry 六节）
  let section = "";
  for (const line of md.split("\n")) {
    const heading = line.match(/^##\s+(一|二|三|四|五|六|七|八|九|十)、/);
    if (heading) {
      section = heading[1];
      continue;
    }
    const cell = line.match(/^\|\s*`([a-z][a-z0-9-]{1,31})`\s*\|/);
    if (!cell) continue;
    if (section === "二") serviceNames.add(cell[1]);
    else if (section === "四") appNames.add(cell[1]);
    else if (section === "五") mobileAppNames.add(cell[1]);
    else if (section === "六") clientNames.add(cell[1]);
  }
  return { serviceNames, appNames, mobileAppNames, clientNames };
}
