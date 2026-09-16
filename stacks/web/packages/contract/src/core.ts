/**
 * 三端同源 core 出口（MP4）：零微前端依赖、零 DOM 运行时依赖——web（fetch 版另有 ./fetch 出口）、
 * 微信小程序/小游戏（./wx 出口）、Node 测试环境通吃。主入口 `@yarch/contract` = 本出口 + 微前端运行时件。
 */
export * from "./error-codes";
export * from "./rest-response";
export * from "./api-error";
export * from "./trace-id";
export * from "./storage";
export * from "./transport";
