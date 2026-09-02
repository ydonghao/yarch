package main

import (
	"os"
	"path/filepath"
	"strings"
	"testing"
)

// 生成器冒烟：完整生成一份工程——module/service 替换、目录结构齐全。
func TestGenerate(t *testing.T) {
	out := t.TempDir() + "/order-svc"
	n, err := generate("../../template", out, "github.com/alice/order-svc", "order-svc")
	if err != nil {
		t.Fatalf("generate: %v", err)
	}
	if n < 20 {
		t.Fatalf("generated files = %d, too few", n)
	}

	// 结构：coze-studio 同构七包 + 根 main.go
	for _, dir := range []string{"api", "application", "domain", "crossdomain", "infra", "pkg", "types", "conf"} {
		if fi, err := os.Stat(filepath.Join(out, dir)); err != nil || !fi.IsDir() {
			t.Fatalf("缺少目录 %s（%v）", dir, err)
		}
	}
	if _, err := os.Stat(filepath.Join(out, "main.go")); err != nil {
		t.Fatal("缺少根 main.go（单入口）")
	}

	// 替换：import 路径 + 服务名
	b, err := os.ReadFile(filepath.Join(out, "main.go"))
	if err != nil {
		t.Fatal(err)
	}
	s := string(b)
	if strings.Contains(s, "yarch-go/template") || strings.Contains(s, "my-svc") {
		t.Fatalf("替换不完全:\n%s", s)
	}
	if !strings.Contains(s, `const service = "order-svc"`) {
		t.Fatalf("服务名未注入:\n%.200s", s)
	}
	h, _ := os.ReadFile(filepath.Join(out, "api", "handler", "user.go"))
	if !strings.Contains(string(h), "github.com/alice/order-svc/domain") {
		t.Fatal("handler import 未替换")
	}
}

func TestServiceValidation(t *testing.T) {
	valid := []string{"order-svc", "ab", "ysaas-billing"}
	for _, s := range valid {
		if !servicePattern.MatchString(s) || genericWords[s] {
			t.Errorf("%q 应合法", s)
		}
	}
	invalid := []string{"MySvc", "my_svc", "1abc", "x", "a-very-long-service-name-over-32-chars"}
	for _, s := range invalid {
		if servicePattern.MatchString(s) {
			t.Errorf("%q 应被格式拒绝", s)
		}
	}
	for _, s := range []string{"api", "service", "backend", "demo"} {
		if !genericWords[s] {
			t.Errorf("%q 应在禁用通用词表", s)
		}
	}
}
