import { describe, expect, it } from "vitest";

import { demos } from "../src/demos";

// component-library.md 三-1 机检（包内实现）：manifest 结构约定
const KEbab = /^[a-z0-9]+(-[a-z0-9]+)*$/;

describe("demo manifest（component-library.md 三）", () => {
  it("demos 非空且结构合法（id kebab-case 唯一 / source 非空 / render 可调用）", () => {
    expect(demos.length).toBeGreaterThan(0);
    const ids = demos.map((d) => d.id);
    for (const d of demos) {
      expect(KEbab.test(d.id), `id ${d.id} 非 kebab-case`).toBe(true);
      expect(d.title).toBeTruthy();
      expect(d.source.trim().length).toBeGreaterThan(10);
      expect(typeof d.render).toBe("function");
    }
    expect(new Set(ids).size).toBe(ids.length);
  });

  it("render 可重入：同容器连续渲染不泄漏（三-2 清理约定）", () => {
    const container = document.createElement("div");
    document.body.appendChild(container);
    try {
      for (const d of demos) {
        for (let i = 0; i < 2; i++) {
          const cleanup = d.render(container);
          cleanup?.();
        }
        expect(container.children.length, `demo ${d.id} 清理后应无残留 DOM`).toBe(0);
      }
    } finally {
      container.remove();
    }
  });
});
