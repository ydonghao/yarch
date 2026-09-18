/**
 * yarch 设计 token TS 常量（@yarch/tokens）。
 * 唯一权威 = 包根 tokens.json（component-library.md 四-1）；本文件 re-export 之，
 * 与 tokens.css 的键集合一致性由 test/tokens.test.ts 断言（四-3 机检）。
 */
import tokensJson from "../tokens.json";

export interface ColorTokens {
  primary: string;
  semantic: Record<"success" | "warning" | "error" | "info", string>;
  neutral: Record<
    "bg" | "bg-raised" | "border" | "text" | "text-secondary" | "text-disabled",
    string
  >;
}

export interface TypographyTokens {
  "font-family": string;
  "font-size": Record<"xs" | "sm" | "md" | "lg" | "xl", string>;
  "line-height": Record<"tight" | "normal", string>;
}

export interface SpacingTokens {
  xs: string;
  sm: string;
  md: string;
  lg: string;
  xl: string;
  xxl: string;
}

export interface RadiusTokens {
  sm: string;
  md: string;
  lg: string;
  full: string;
}

export interface ShadowTokens {
  sm: string;
  md: string;
  lg: string;
}

export interface Tokens {
  color: ColorTokens;
  typography: TypographyTokens;
  spacing: SpacingTokens;
  radius: RadiusTokens;
  shadow: ShadowTokens;
}

export const tokens = tokensJson as unknown as Tokens;
export type { Tokens as TokenShape };

/** 暗色模式 color 覆盖集（modes.dark.color；仅 color 组） */
export const darkColor = (tokensJson as unknown as { modes: { dark: { color: ColorTokens } } })
  .modes.dark.color;

/** CSS 变量名推导（--yarch-<组>-<键>，嵌套键连字拼接）——与 src/tokens.css 命名口径同源 */
export function cssVarName(path: string[]): string {
  return `--yarch-${path.join("-")}`;
}
