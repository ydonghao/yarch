package errcode_test

import (
	"testing"

	"github.com/yuandonghao/yarch/stacks/golang/errcode"
)

// 契约断言：错误码 13 码全表（code / 标识符 / 默认文案 / HTTP 映射），
// 与 contract/api/error-codes.md 逐行对照，防实现漂移。
func TestBuiltinTable(t *testing.T) {
	table := []struct {
		code       errcode.Code
		identifier string
		message    string
		http       int
	}{
		{errcode.InternalError, "InternalError", "内部错误", 500},
		{errcode.InvalidArgument, "InvalidArgument", "参数校验失败", 400},
		{errcode.MalformedBody, "MalformedBody", "请求体格式错误", 400},
		{errcode.NotFound, "NotFound", "资源不存在", 404},
		{errcode.Conflict, "Conflict", "资源冲突", 409},
		{errcode.RateLimited, "RateLimited", "触发限流", 429},
		{errcode.IdempotencyConflict, "IdempotencyConflict", "幂等冲突：重复提交", 409},
		{errcode.UpstreamTimeout, "UpstreamTimeout", "上游依赖超时", 504},
		{errcode.Unavailable, "Unavailable", "服务暂不可用", 503},
		{errcode.Unauthorized, "Unauthorized", "未认证", 401},
		{errcode.CredentialsExpired, "CredentialsExpired", "凭证已过期", 401},
		{errcode.Forbidden, "Forbidden", "权限不足", 403},
		{errcode.AccountDisabled, "AccountDisabled", "账号已禁用", 403},
	}
	for _, row := range table {
		if got := row.code.Identifier(); got != row.identifier {
			t.Errorf("code %d identifier = %q, want %q", row.code, got, row.identifier)
		}
		if got := row.code.Message(); got != row.message {
			t.Errorf("code %d message = %q, want %q", row.code, got, row.message)
		}
		if got := row.code.HTTP(); got != row.http {
			t.Errorf("code %d http = %d, want %d", row.code, got, row.http)
		}
		if !row.code.Valid() {
			t.Errorf("code %d should be valid", row.code)
		}
	}
	if len(table) != 13 {
		t.Fatalf("builtin table must have 13 codes, got %d", len(table))
	}
}

func TestOK(t *testing.T) {
	if errcode.OK.Message() != "成功" || errcode.OK.HTTP() != 200 {
		t.Fatalf("OK = %q/%d, want 成功/200", errcode.OK.Message(), errcode.OK.HTTP())
	}
}

func TestUnregistered(t *testing.T) {
	c := errcode.Code(9999)
	if c.Valid() {
		t.Fatal("9999 预留段不得视为已登记")
	}
	if c.Message() != "内部错误" || c.HTTP() != 500 {
		t.Fatalf("unregistered code must fall back to internal error, got %q/%d", c.Message(), c.HTTP())
	}
}

func TestBusinessRegister(t *testing.T) {
	errcode.Register(3999, "DemoBusy", "示例繁忙", 409)
	if got := errcode.Code(3999).Message(); got != "示例繁忙" {
		t.Fatalf("registered message = %q", got)
	}
	errcode.Register(3999, "DemoBusy", "示例繁忙", 409) // 幂等重复登记放行

	mustPanic(t, "below range", func() { errcode.Register(2999, "X", "x", 400) })
	mustPanic(t, "above range", func() { errcode.Register(9000, "X", "x", 400) })
	mustPanic(t, "http not allowed", func() { errcode.Register(4000, "X", "x", 204) })
	mustPanic(t, "conflict meta", func() { errcode.Register(3999, "Other", "其他", 409) })
}

func mustPanic(t *testing.T, name string, fn func()) {
	t.Helper()
	defer func() {
		if recover() == nil {
			t.Errorf("%s: expected panic", name)
		}
	}()
	fn()
}
