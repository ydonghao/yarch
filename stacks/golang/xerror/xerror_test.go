package xerror_test

import (
	"errors"
	"fmt"
	"testing"

	"github.com/ydonghao/yarch/stacks/golang/errcode"
	"github.com/ydonghao/yarch/stacks/golang/xerror"
)

// 契约断言：message「默认文案：细节」追加规则，默认文案部分不得改写。
func TestMessageRule(t *testing.T) {
	e := xerror.New(errcode.InvalidArgument, "pageSize 必须 ≤ 100")
	if e.Error() != "参数校验失败：pageSize 必须 ≤ 100" {
		t.Fatalf("message = %q", e.Error())
	}
	e2 := xerror.New(errcode.InvalidArgument)
	if e2.Error() != "参数校验失败" {
		t.Fatalf("default message = %q", e2.Error())
	}
	if got := xerror.Newf(errcode.NotFound, "order %s", "o-1").Error(); got != "资源不存在：order o-1" {
		t.Fatalf("Newf message = %q", got)
	}
}

func TestFromError(t *testing.T) {
	be := xerror.New(errcode.Conflict)
	if xerror.FromError(be) != be {
		t.Fatal("BizError 应原样返回")
	}
	wrapped := fmt.Errorf("wrap: %w", be)
	if xerror.FromError(wrapped) != be {
		t.Fatal("wrapped BizError 应解包返回")
	}
	ue := xerror.FromError(errors.New("boom"))
	if ue.Code != errcode.InternalError || ue.Message != "内部错误" {
		t.Fatalf("unexpected error must map to 1000/内部错误, got %d/%q", ue.Code, ue.Message)
	}
	if xerror.FromError(nil) != nil {
		t.Fatal("nil error 应返回 nil")
	}
}
