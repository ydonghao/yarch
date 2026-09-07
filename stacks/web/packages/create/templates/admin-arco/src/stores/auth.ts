const TOKEN_KEY = "yarch_token";

export const authStore = {
  get token() { return localStorage.getItem(TOKEN_KEY) ?? ""; },
  set token(v: string) { localStorage.setItem(TOKEN_KEY, v); },
  clear() { localStorage.removeItem(TOKEN_KEY); },
};
