import React from "react";
import ReactDOM from "react-dom/client";
import { createRoot } from "react-dom/client";
import { semiGlobal } from "@douyinfe/semi-ui";
// React 19 下 Semi 命令式渲染（Toast/Notification 等）须显式注入 createRoot，否则弹层静默不渲染
semiGlobal.config.createRoot = createRoot;
import { BrowserRouter } from "react-router-dom";
import microApp from "@micro-zoe/micro-app";
import App from "./app/router";
import { registerSharedRuntimes } from "./micro/shared";

// 基座先行注册共享运行时（八-1/八-2），再启动载器——子应用集成构建依赖 window 全局
registerSharedRuntimes();
microApp.start();

ReactDOM.createRoot(document.getElementById("root")!).render(
  <React.StrictMode>
    <BrowserRouter>
      <App />
    </BrowserRouter>
  </React.StrictMode>
);
