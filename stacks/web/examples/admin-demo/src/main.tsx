import React from "react";
import ReactDOM from "react-dom/client";
import { createRoot } from "react-dom/client";
import { semiGlobal } from "@douyinfe/semi-ui";
// React 19 下 Semi 命令式渲染（Toast/Notification 等）须显式注入 createRoot，否则弹层静默不渲染
semiGlobal.config.createRoot = createRoot;
import { BrowserRouter } from "react-router-dom";
import App from "./app/router";

ReactDOM.createRoot(document.getElementById("root")!).render(
  <React.StrictMode>
    <BrowserRouter>
      <App />
    </BrowserRouter>
  </React.StrictMode>
);
