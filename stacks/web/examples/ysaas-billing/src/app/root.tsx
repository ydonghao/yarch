import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { BrowserRouter } from "react-router-dom";
import type { SubAppMountProps } from "@yarch/contract";
import SubRouter from "./router";
import { setPopupContainer } from "../ui/popup";

/**
 * mount 工厂（五-1）：容器与路由 base 由供给层注入（四-2——子应用不感知完整 URL）；
 * 返回清理函数供 unmount 成对调用（五-2）。
 */
export function mountApp(props: SubAppMountProps): () => void {
  setPopupContainer(props.container); // 六-3：弹层挂载容器指向本应用容器
  const root = createRoot(props.container);
  root.render(
    <StrictMode>
      <BrowserRouter basename={props.base}>
        <SubRouter />
      </BrowserRouter>
    </StrictMode>
  );
  return () => root.unmount();
}
