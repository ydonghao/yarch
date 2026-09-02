// Package xerror 实现契约业务异常（术语对照：golang xerror.BizError）。
// message 遵循 error-codes.md 实现规则-1：允许在默认文案后追加冒号细节，
// 默认文案部分不得改写。
package xerror

import (
	"errors"
	"fmt"

	"github.com/yuandonghao/yarch/stacks/golang/errcode"
)

// BizError 业务异常：携带错误码与面向用户的 message（默认文案[:细节]）。
// 排障细节只进日志，不进 message。
type BizError struct {
	Code    errcode.Code
	Message string
}

// New 构造业务异常。detail 为空时 message 取码表默认文案；
// 非空时按「默认文案：细节」拼接。
func New(code errcode.Code, detail ...string) *BizError {
	msg := code.Message()
	if len(detail) > 0 && detail[0] != "" {
		msg = msg + "：" + detail[0]
	}
	return &BizError{Code: code, Message: msg}
}

// Newf 构造业务异常，细节按格式化生成。
func Newf(code errcode.Code, format string, args ...any) *BizError {
	return New(code, fmt.Sprintf(format, args...))
}

// Error 实现 error 接口（输出即信封 message，可直接回给前端）。
func (e *BizError) Error() string { return e.Message }

// FromError 将任意 error 归一为 *BizError：已是 BizError 原样返回，
// 否则视为未预期失败（1000，细节只进日志不进 message）。
func FromError(err error) *BizError {
	if err == nil {
		return nil
	}
	var be *BizError
	if errors.As(err, &be) {
		return be
	}
	return New(errcode.InternalError)
}
