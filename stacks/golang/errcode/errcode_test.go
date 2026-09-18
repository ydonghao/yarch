package errcode_test

import (
	"encoding/json"
	"os"
	"strings"
	"testing"

	"github.com/ydonghao/yarch/stacks/golang/errcode"
)

// 契约断言：14 码全表唯一权威 = contract/dist/error-codes.json（由 contract/api/error-codes.md 派生，
// CI 拒双向漂移）——四栈读同一份 json 断言，不再各养手抄表（P1 契约机器可读出口）。
// dist 文件不在场（消费方模块独立测试环境）则跳过本表断言，CI 仓内必跑。
func TestBuiltinTable(t *testing.T) {
	raw, ioErr := os.ReadFile("../../../contract/dist/error-codes.json")
	if ioErr != nil {
		t.Skipf("contract dist json 不在场（%v），跳过同源断言", ioErr)
	}
	var dist struct {
		Codes []struct {
			Code    int    `json:"code"`
			Key     string `json:"key"`
			Message string `json:"message"`
			HTTP    int    `json:"http"`
		} `json:"codes"`
	}
	if err := json.Unmarshal(raw, &dist); err != nil {
		t.Fatalf("dist json 解析失败: %v", err)
	}
	if len(dist.Codes) != 14 {
		t.Fatalf("dist 14 码表异常: %d 条", len(dist.Codes))
	}
	for _, row := range dist.Codes {
		code := errcode.Code(row.Code)
		if !code.Valid() {
			t.Errorf("code %d 在 errcode 表中缺失", row.Code)
			continue
		}
		// dist key 为 UPPER_SNAKE（md 标识列），golang 方言为 CamelCase——归一化对照
		if got := code.Identifier(); got != camelCase(row.Key) {
			t.Errorf("code %d identifier = %q, want %q（dist key %s）", row.Code, got, camelCase(row.Key), row.Key)
		}
		if got := code.Message(); got != row.Message {
			t.Errorf("code %d message = %q, want %q", row.Code, got, row.Message)
		}
		if got := code.HTTP(); got != row.HTTP {
			t.Errorf("code %d http = %d, want %d", row.Code, got, row.HTTP)
		}
	}
}

// camelCase INTERNAL_ERROR → InternalError（方言标识符对照用）
func camelCase(upperSnake string) string {
	parts := strings.Split(strings.ToLower(upperSnake), "_")
	for i, p := range parts {
		if p == "" {
			continue
		}
		parts[i] = strings.ToUpper(p[:1]) + p[1:]
	}
	return strings.Join(parts, "")
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
