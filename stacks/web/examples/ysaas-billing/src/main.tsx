import { supply } from "@supply";
import { createRoot } from "react-dom/client";
import { semiGlobal } from "@douyinfe/semi-ui";
// React 19 下 Semi 命令式渲染（Toast/Notification 等）须显式注入 createRoot，否则弹层静默不渲染
semiGlobal.config.createRoot = createRoot;
import { mountApp } from "./app/root";

/**
 * 双模式唯一入口（十一-2）：挂载参数全部来自供给层，业务代码零分支。
 * 独立模式：vite dev 直开浏览器；集成模式：被基座经 micro-app 加载，supply 已切 integrated 实现。
 */
let unmount: (() => void) | null = null;

function mount() {
  const props = supply.getMountProps();
  unmount = mountApp(props);
}

function unmountApp() {
  unmount?.(); // 五-2：unmount 彻底清理（定时器/监听/动态 DOM——root.unmount 卸掉整棵树）
  unmount = null;
}

mount();
supply.onUnmount(unmountApp);          // 五-2：卸载清理成对
supply.onRemount(() => {               // 五-3：mount/unmount 幂等——来回切换不崩不重挂
  if (unmount === null) mount();
});
