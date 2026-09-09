/**
 * 弹层容器（六-3）：Modal/Select/Tooltip 等默认挂 body 的组件必须指定挂载容器为本应用容器，
 * 否则微前端沙箱下弹层逃逸到基座 DOM。页面用法：`<Modal getPopupContainer={getPopupContainer}>`。
 */
let container: HTMLElement | null = null;

export function setPopupContainer(el: HTMLElement): void {
  container = el;
}

export function getPopupContainer(): HTMLElement {
  return container ?? document.body;
}
