package redix_test

import "testing"

import "github.com/yuandonghao/yarch-go/redix"

// 契约断言：key 首段=服务名（redis.md：key 前缀即租户边界）。
func TestKeysShape(t *testing.T) {
	k, err := redix.NewKeys("mysvc")
	if err != nil {
		t.Fatal(err)
	}
	if got := k.K("user", "42"); got != "mysvc:user:42" {
		t.Fatalf("key = %q", got)
	}
	if k.Service() != "mysvc" {
		t.Fatalf("service = %q", k.Service())
	}
}

func TestKeysServiceValidation(t *testing.T) {
	for _, bad := range []string{"", "a", "MySvc", "my_svc", "1abc", "mysvc-with-a-very-long-name-exceeding-32"} {
		if _, err := redix.NewKeys(bad); err == nil {
			t.Errorf("service %q should be rejected", bad)
		}
	}
}
