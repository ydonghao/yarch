// 三载体同源断言（component-library.md 四-3 机检）：tokens.json（唯一权威）↔ tokens.css ↔ TS 导出
// 键集合一致；schema 约束（五组必备/kebab-case/色值/单位）。
import { readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";
import rawJson from "../tokens.json";

const raw = rawJson as unknown as Record<string, unknown>;
const css = readFileSync(new URL("../src/tokens.css", import.meta.url), "utf-8");

type Leaf = { path: string; value: string };

/** 展平为「组前缀路径 + 叶值」：color.semantic.success → { path: "color-semantic-success" } */
function flatten(obj: Record<string, unknown>, prefix = ""): Leaf[] {
  return Object.entries(obj).flatMap(([k, v]) => {
    const path = prefix ? `${prefix}-${k}` : k;
    if (v && typeof v === "object" && !Array.isArray(v)) {
      return flatten(v as Record<string, unknown>, path);
    }
    return [{ path, value: String(v) }];
  });
}

function colorLeaf(path: string): string {
  const leaf = flatten(raw.color as Record<string, unknown>, "color").find((l) => l.path === path);
  if (!leaf) throw new Error(`color 键不存在: ${path}`);
  return leaf.value;
}
function spacingLeaf(path: string): string {
  const leaf = flatten(raw.spacing as Record<string, unknown>, "spacing").find((l) => l.path === path);
  if (!leaf) throw new Error(`spacing 键不存在: ${path}`);
  return leaf.value;
}

const KEbab = /^[a-z0-9]+(-[a-z0-9]+)*$/;
const Color = /^#[0-9a-fA-F]{6}([0-9a-fA-F]{2})?$/;
const Unit = /^[0-9.]+(px|rem)$/;

const TOKEN_GROUPS = ["color", "typography", "spacing", "radius", "shadow"] as const;
const allLeaves: Leaf[] = TOKEN_GROUPS.flatMap((g) => flatten(raw[g] as Record<string, unknown>, g));

describe("tokens schema（component-library.md 四-1/四-2）", () => {
  it("五组必备 + meta", () => {
    for (const group of ["meta", ...TOKEN_GROUPS]) {
      expect(raw[group], `缺少 ${group} 组`).toBeDefined();
    }
  });

  it("键名 kebab-case 且值形态合法", () => {
    expect(allLeaves.length).toBeGreaterThan(15);
    for (const { path } of allLeaves) {
      expect(KEbab.test(path), `键 ${path} 非 kebab-case`).toBe(true);
    }
    for (const { path, value } of allLeaves) {
      if (path.startsWith("color-")) {
        expect(Color.test(value), `色值 ${path}=${value} 形态非法`).toBe(true);
      }
      if (path.startsWith("spacing-") || path.startsWith("radius-")) {
        expect(Unit.test(value), `尺寸 ${path}=${value} 须带 px/rem 单位`).toBe(true);
      }
    }
  });

  it("modes.dark 只覆盖 color 组", () => {
    const modes = raw.modes as Record<string, Record<string, unknown>>;
    expect(Object.keys(modes.dark)).toEqual(["color"]);
    expect(flatten(modes.dark.color as Record<string, unknown>, "color").map((l) => l.path).sort()).toEqual(
      flatten(raw.color as Record<string, unknown>, "color").map((l) => l.path).sort(),
    );
  });
});

describe("三载体同源（四-3）", () => {
  const jsonVars = allLeaves.map((l) => `--yarch-${l.path}`);

  it("tokens.css :root 覆盖全部键（无缺无多）", () => {
    const rootBlock = css.split('[data-theme="dark"]')[0];
    const cssVars = [...rootBlock.matchAll(/(--yarch-[a-z0-9-]+)\s*:/g)].map((m) => m[1]);
    expect(cssVars.sort()).toEqual(jsonVars.slice().sort());
  });

  it("暗色块只重定义 color 组且键集合一致", () => {
    const darkBlock = css.split('[data-theme="dark"]')[1];
    expect(darkBlock).toBeDefined();
    const darkVars = [...darkBlock.matchAll(/(--yarch-[a-z0-9-]+)\s*:/g)].map((m) => m[1]);
    const colorVars = flatten(raw.color as Record<string, unknown>, "color").map((l) => `--yarch-${l.path}`);
    expect(darkVars.sort()).toEqual(colorVars.sort());
  });

  it("TS 导出与 json 同源（抽查锚点）", async () => {
    const { tokens, darkColor } = await import("../src/index.ts");
    expect(tokens.color.primary).toBe(colorLeaf("color-primary"));
    expect(tokens.spacing.xl).toBe(spacingLeaf("spacing-xl"));
    expect(darkColor.neutral.bg).toBe(
      (raw.modes as { dark: { color: { neutral: Record<string, string> } } }).dark.color.neutral.bg,
    );
  });

  it("css 变量值与 json 值一致（抽查锚点）", () => {
    const darkBlock = css.split('[data-theme="dark"]')[1];
    expect(css).toContain(`--yarch-color-primary: ${colorLeaf("color-primary")};`);
    expect(darkBlock).toContain(
      `--yarch-color-primary: ${(raw.modes as { dark: { color: Record<string, string> } }).dark.color.primary};`,
    );
  });
});
