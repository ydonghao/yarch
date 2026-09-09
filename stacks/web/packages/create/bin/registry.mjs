// registry.md 解析（共享模块：create-admin 校验 与 gen-registry-snapshot 快照生成 同源，防漂移）。
// 解析 contract/registry.md：二节服务名登记表 + 四节前端应用名登记表（首列反引号包裹的行）。
export function parseRegistryMd(md) {
  const serviceNames = new Set();
  const appNames = new Set();
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
  }
  return { serviceNames, appNames };
}
