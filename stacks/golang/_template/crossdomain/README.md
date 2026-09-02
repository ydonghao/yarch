# crossdomain · 域间防腐层

域与域禁止直接依赖（coze-studio 同构铁律）：跨域调用必须经本层契约。
模式：`contract/`（接口）+ `impl/`（聚合 domain 服务实现）+ `model/`（跨域 DTO，不泄漏领域实体）。

示例：将来新增 second 域需要查询用户名称时，在此定义

```go
// crossdomain/user/contract.go
type UserGateway interface {
    UserName(ctx context.Context, id string) (string, error) // 只暴露跨域所需最小面
}
```

second 域只 import crossdomain/user，不 import domain/user。
