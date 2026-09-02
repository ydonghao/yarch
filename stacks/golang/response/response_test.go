package response_test

import (
	"encoding/json"
	"testing"

	"github.com/yuandonghao/yarch/stacks/golang/errcode"
	"github.com/yuandonghao/yarch/stacks/golang/response"
)

// 契约断言：信封四字段 camelCase、字段顺序、data 空值语义（rest-response.md v1.0）。
func TestEnvelopeShape(t *testing.T) {
	r := response.Ok(map[string]string{"any": "thing"}, "0af7651916cd43dd8448eb211c80319c")
	b := response.JSON(r)
	want := `{"code":0,"message":"成功","data":{"any":"thing"},"traceId":"0af7651916cd43dd8448eb211c80319c"}`
	if string(b) != want {
		t.Fatalf("envelope = %s, want %s", b, want)
	}
}

func TestFailDataNull(t *testing.T) {
	r := response.Fail[struct{}](errcode.InvalidArgument, "参数校验失败：pageSize 必须 ≤ 100", "tid")
	b := response.JSON(r)
	want := `{"code":1001,"message":"参数校验失败：pageSize 必须 ≤ 100","data":null,"traceId":"tid"}`
	if string(b) != want {
		t.Fatalf("fail envelope = %s, want %s", b, want)
	}
}

func TestOkNilDataNull(t *testing.T) {
	r := response.OkNil[struct{}]("tid")
	b := response.JSON(r)
	want := `{"code":0,"message":"成功","data":null,"traceId":"tid"}`
	if string(b) != want {
		t.Fatalf("ok-nil envelope = %s, want %s", b, want)
	}
}

// 契约断言：分页负载形状——list 空集合必须是 [] 不是 null（rest-conventions.md 数据表示）。
func TestPageData(t *testing.T) {
	p := response.NewPageData[struct{}](nil, 7, 2, 20)
	b, err := json.Marshal(p)
	if err != nil {
		t.Fatal(err)
	}
	want := `{"list":[],"total":7,"page":2,"pageSize":20}`
	if string(b) != want {
		t.Fatalf("pagedata = %s, want %s", b, want)
	}

	// 游标通道：nextCursor 空串省略（缺失即没有下一页）
	p2 := response.PageData[int]{List: []int{1}, Total: 1, Page: 1, PageSize: 20, NextCursor: ""}
	b2, _ := json.Marshal(p2)
	if want2 := `{"list":[1],"total":1,"page":1,"pageSize":20}`; string(b2) != want2 {
		t.Fatalf("pagedata cursor = %s, want %s", b2, want2)
	}

	p3 := response.PageData[int]{List: []int{1}, Total: 1, Page: 1, PageSize: 20, NextCursor: "cz_1"}
	b3, _ := json.Marshal(p3)
	if want3 := `{"list":[1],"total":1,"page":1,"pageSize":20,"nextCursor":"cz_1"}`; string(b3) != want3 {
		t.Fatalf("pagedata cursor = %s, want %s", b3, want3)
	}
}
