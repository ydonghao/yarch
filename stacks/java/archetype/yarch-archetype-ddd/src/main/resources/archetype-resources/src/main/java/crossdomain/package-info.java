/**
 * crossdomain：跨领域防腐层（外部系统的接口定义与实现隔离）。 出现对外部服务/遗留系统的依赖时，接口契约放本包 contract/，转义实现放 impl/—— 外部模型不得渗入
 * domain（yarch J1 拍板：DDD 七包，coze-studio 同构）。
 */
package ${package}.crossdomain;
