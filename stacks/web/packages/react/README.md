# @yarch/react

yarch React 适配层——为 `@yarch/contract` 的导航端口注入 react-router 实现（薄适配，依赖倒置的实现侧）。

```ts
import { installNavigator } from "@yarch/react";
import { useNavigate } from "react-router-dom";
// 应用组装时注入一次，契约层的 401 跳转等即可无框架落地
```

配套：`@yarch/contract`（契约 SDK）· 工程脚手架 `npm create @yarch/admin@latest <name>`。
