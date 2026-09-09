// @yarch/contract shim（集成构建专用）：contract 运行时单例由基座共享（八-1）——
// 两份实例 = 两个 ApiError 类，instanceof 全失效；本 shim 保证子应用取到的就是基座那份。
const ns = (globalThis as Record<string, any>).__YARCH_CONTRACT__;
if (!ns) throw new Error("集成构建须运行在基座内：__YARCH_CONTRACT__ 未注册（八-1 contract 单例由基座共享）");

export const CODE_SUCCESS = ns.CODE_SUCCESS;
export const errorCodes = ns.errorCodes;
export const TRACE_ID_HEADER = ns.TRACE_ID_HEADER;
export const newTraceId = ns.newTraceId;
export const ApiError = ns.ApiError;
export const unwrap = ns.unwrap;
export const unwrapAllowNull = ns.unwrapAllowNull;
export const setNavigator = ns.setNavigator;
export const navigateTo = ns.navigateTo;
export const navigateToLogin = ns.navigateToLogin;
export const createClient = ns.createClient;
export const createAppEventBus = ns.createAppEventBus;
export const getAppEventBus = ns.getAppEventBus;
export const defineAppEvents = ns.defineAppEvents;
export const assertAppEventName = ns.assertAppEventName;
export const createAppStorage = ns.createAppStorage;
export const subAppRoutePrefix = ns.subAppRoutePrefix;
export const SHARED_CONTRACT_KEY = ns.SHARED_CONTRACT_KEY;
export const setSharedContract = ns.setSharedContract;
export const getSharedContract = ns.getSharedContract;
