// Package errcode 实现契约「错误码全局段位表」（contract/api/error-codes.md v1.0）。
// yarch 只拥有 0、1xxx、2xxx；3xxx-8xxx 由业务仓注册后方可使用，9xxx 预留不得使用。
package errcode

import (
	"fmt"
	"sync"
)

// Code 业务码。0 = 成功；非 0 见错误码段位表。同一 code 的语义与 HTTP 映射全栈一致。
type Code int32

// 通用段 1xxx（yarch 内置，各栈必须等价实现）。
const (
	OK Code = 0

	InternalError       Code = 1000 // INTERNAL_ERROR / 内部错误 / 500
	InvalidArgument     Code = 1001 // INVALID_ARGUMENT / 参数校验失败 / 400
	MalformedBody       Code = 1002 // MALFORMED_BODY / 请求体格式错误 / 400
	NotFound            Code = 1004 // NOT_FOUND / 资源不存在 / 404
	Conflict            Code = 1005 // CONFLICT / 资源冲突 / 409
	RateLimited         Code = 1006 // RATE_LIMITED / 触发限流 / 429
	IdempotencyConflict Code = 1007 // IDEMPOTENCY_CONFLICT / 幂等冲突：重复提交 / 409
	UpstreamTimeout     Code = 1008 // UPSTREAM_TIMEOUT / 上游依赖超时 / 504
	Unavailable         Code = 1009 // UNAVAILABLE / 服务暂不可用 / 503
)

// 认证与权限段 2xxx（yarch 内置）。
const (
	Unauthorized       Code = 2001 // UNAUTHORIZED / 未认证 / 401
	CredentialsExpired Code = 2002 // CREDENTIALS_EXPIRED / 凭证已过期 / 401
	Forbidden          Code = 2003 // FORBIDDEN / 权限不足 / 403
	AccountDisabled    Code = 2004 // ACCOUNT_DISABLED / 账号已禁用 / 403
)

type meta struct {
	identifier string
	message    string
	http       int
}

var builtin = map[Code]meta{
	InternalError:       {"InternalError", "内部错误", 500},
	InvalidArgument:     {"InvalidArgument", "参数校验失败", 400},
	MalformedBody:       {"MalformedBody", "请求体格式错误", 400},
	NotFound:            {"NotFound", "资源不存在", 404},
	Conflict:            {"Conflict", "资源冲突", 409},
	RateLimited:         {"RateLimited", "触发限流", 429},
	IdempotencyConflict: {"IdempotencyConflict", "幂等冲突：重复提交", 409},
	UpstreamTimeout:     {"UpstreamTimeout", "上游依赖超时", 504},
	Unavailable:         {"Unavailable", "服务暂不可用", 503},
	Unauthorized:        {"Unauthorized", "未认证", 401},
	CredentialsExpired:  {"CredentialsExpired", "凭证已过期", 401},
	Forbidden:           {"Forbidden", "权限不足", 403},
	AccountDisabled:     {"AccountDisabled", "账号已禁用", 403},
}

var (
	mu       sync.RWMutex
	business = map[Code]meta{}
)

// Register 登记业务码（3xxx-8xxx；在业务仓 docs 登记后方可使用）。
// message 为默认文案，允许调用方以「默认文案：细节」规则追加；HTTP 必须落在
// rest-conventions.md 状态码白名单内。重复登记同一 code 且元信息不同将 panic（启动期暴露）。
func Register(c Code, identifier, message string, http int) {
	if c < 3000 || c > 8999 {
		panic(fmt.Sprintf("errcode: business codes must be within 3000-8999, got %d", c))
	}
	if !httpAllowed(http) {
		panic(fmt.Sprintf("errcode: http status %d not in whitelist", http))
	}
	mu.Lock()
	defer mu.Unlock()
	if m, ok := business[c]; ok && (m.identifier != identifier || m.message != message || m.http != http) {
		panic(fmt.Sprintf("errcode: code %d already registered as %+v", c, m))
	}
	business[c] = meta{identifier, message, http}
}

func lookup(c Code) (meta, bool) {
	if m, ok := builtin[c]; ok {
		return m, true
	}
	mu.RLock()
	defer mu.RUnlock()
	m, ok := business[c]
	return m, ok
}

// Identifier 返回码表标识符（golang 方言驼峰，如 InternalError）；未登记返回空串。
func (c Code) Identifier() string { m, ok := lookup(c); if !ok { return "" }; return m.identifier }

// Message 返回默认文案；未登记 code 视为实现缺陷，返回内部错误文案兜底。
func (c Code) Message() string {
	if c == OK {
		return "成功"
	}
	if m, ok := lookup(c); ok {
		return m.message
	}
	return builtin[InternalError].message
}

// HTTP 返回该 code 固定的 HTTP 状态码映射（以契约码表为准，服务端不得自行发挥）。
func (c Code) HTTP() int {
	if c == OK {
		return 200
	}
	if m, ok := lookup(c); ok {
		return m.http
	}
	return 500
}

// Valid 报告该 code 是否已登记（内置或业务注册）。
func (c Code) Valid() bool { _, ok := lookup(c); return ok }

// httpAllowed：rest-conventions.md 允许业务 API 使用的状态码白名单。
func httpAllowed(status int) bool {
	switch status {
	case 200, 201, 400, 401, 403, 404, 409, 429, 500, 503, 504:
		return true
	}
	return false
}
