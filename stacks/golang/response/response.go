// Package response 实现契约「RestResponse 形状」（contract/api/rest-response.md v1.0）。
// 四字段 camelCase（code/message/data/traceId），任何栈不得增删改名；
// code != 0 时 data 必须为 null；成功且无负载时 data 也为 null。
package response

import (
	"encoding/json"

	"github.com/yuandonghao/yarch/stacks/golang/errcode"
)

// Response 统一响应信封。data 用指针承载：nil 序列化为 null，即契约要求的
// 「失败必 null、成功无负载也为 null」。
type Response[T any] struct {
	Code    errcode.Code `json:"code"`
	Message string       `json:"message"`
	Data    *T           `json:"data"`
	TraceID string       `json:"traceId"`
}

// Ok 构造成功信封（data 非空负载）。
func Ok[T any](data T, traceID string) *Response[T] {
	return &Response[T]{Code: errcode.OK, Message: errcode.OK.Message(), Data: &data, TraceID: traceID}
}

// OkNil 构造成功信封（无负载，data = null）。
func OkNil[T any](traceID string) *Response[T] {
	return &Response[T]{Code: errcode.OK, Message: errcode.OK.Message(), TraceID: traceID}
}

// Fail 构造失败信封。message 以码表默认文案为前缀；细节追加规则见 xerror.BizError，
// 此处直接透传（web 层异常兜底统一走 xerror，本构造用于手写分支）。
func Fail[T any](code errcode.Code, message string, traceID string) *Response[T] {
	if message == "" {
		message = code.Message()
	}
	return &Response[T]{Code: code, Message: message, TraceID: traceID}
}

// PageData 分页负载形状（rest-response.md「分页负载形状」）。
// list 可为空数组但不得为 null；nextCursor 缺失或空串表示没有下一页（游标通道可选）。
type PageData[T any] struct {
	List       []T    `json:"list"`
	Total      int64  `json:"total"`
	Page       int32  `json:"page"`
	PageSize   int32  `json:"pageSize"`
	NextCursor string `json:"nextCursor,omitempty"`
}

// NewPageData 构造分页负载；list 为 nil 时落为空数组（空集合用 [] 不用 null，契约「数据表示」）。
func NewPageData[T any](list []T, total int64, page, pageSize int32) *PageData[T] {
	if list == nil {
		list = []T{}
	}
	return &PageData[T]{List: list, Total: total, Page: page, PageSize: pageSize}
}

// 分页参数口径（rest-conventions.md「查询约定」）：1-based；pageSize 默认 20、上限 100。
const (
	DefaultPage     int32 = 1
	DefaultPageSize int32 = 20
	MaxPageSize     int32 = 100
)

// PageQuery 分页查询参数（页码通道）；校验失败 → 1001（由 web.BindPageQuery 产出）。
type PageQuery struct {
	Page     int32
	PageSize int32
}

// JSON 序列化为字节串（字段顺序即结构体声明顺序：code, message, data, traceId）。
func JSON[T any](r *Response[T]) []byte {
	b, err := json.Marshal(r)
	if err != nil {
		// Response 的字段类型决定其必可序列化；此处不可达。
		panic("response: marshal: " + err.Error())
	}
	return b
}
