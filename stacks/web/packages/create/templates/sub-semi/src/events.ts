import { defineAppEvents } from "@yarch/contract";

/** 事件上行/广播封装（九-1/九-3）：事件名自动 `{应用名}:` 前缀，业务代码只写「动词-名词」 */
export const appEvents = defineAppEvents("{{appName}}");
