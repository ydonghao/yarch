package main

import (
	"os"
	"path/filepath"
	"strings"
	"testing"
)

// 渲染引擎冒烟：完整渲染一份工程——变量注入齐全、七包结构、archetype.json 不进生成物。
func TestRender(t *testing.T) {
	out := t.TempDir() + "/order-svc"
	v := vars{
		Module:       "github.com/alice/order-svc",
		Service:      "order-svc",
		ServiceSnake: "order_svc",
		YarchVersion: "v0.0.0",
		ReplaceLine:  "\nreplace github.com/ydonghao/yarch/stacks/golang => /abs/yarch/stacks/golang",
	}
	n, err := render("../../_template", out, v)
	if err != nil {
		t.Fatalf("render: %v", err)
	}
	if n < 20 {
		t.Fatalf("rendered files = %d, too few", n)
	}

	// 结构：coze-studio 同构七包 + 根 main.go
	for _, dir := range []string{"api", "application", "domain", "crossdomain", "infra", "pkg", "types", "conf"} {
		if fi, err := os.Stat(filepath.Join(out, dir)); err != nil || !fi.IsDir() {
			t.Fatalf("缺少目录 %s（%v）", dir, err)
		}
	}
	if _, err := os.Stat(filepath.Join(out, "main.go")); err != nil {
		t.Fatal("缺少根 main.go")
	}
	if _, err := os.Stat(filepath.Join(out, "archetype.json")); err == nil {
		t.Fatal("archetype.json 是模板资产，不得进生成工程")
	}

	// 变量注入
	b, _ := os.ReadFile(filepath.Join(out, "main.go"))
	s := string(b)
	if strings.Contains(s, "{{") {
		t.Fatalf("占位符未渲染:\n%s", s)
	}
	if !strings.Contains(s, `const service = "order-svc"`) {
		t.Fatalf("服务名未注入:\n%.200s", s)
	}
	h, _ := os.ReadFile(filepath.Join(out, "api", "handler", "user.go"))
	if !strings.Contains(string(h), "github.com/alice/order-svc/domain") {
		t.Fatal("handler import 未渲染")
	}
	g, _ := os.ReadFile(filepath.Join(out, "go.mod"))
	gs := string(g)
	if !strings.Contains(gs, "module github.com/alice/order-svc") ||
		!strings.Contains(gs, "replace github.com/ydonghao/yarch/stacks/golang => /abs/yarch/stacks/golang") {
		t.Fatalf("go.mod 渲染错误:\n%s", gs)
	}
	d, _ := os.ReadFile(filepath.Join(out, "docker-compose.yml"))
	if !strings.Contains(string(d), "order_svc") {
		t.Fatalf("compose 服务名蛇形未渲染:\n%s", d)
	}
}

func TestServiceValidation(t *testing.T) {
	for _, s := range []string{"order-svc", "ab", "ysaas-billing"} {
		if !servicePattern.MatchString(s) || genericWords[s] {
			t.Errorf("%q 应合法", s)
		}
	}
	for _, s := range []string{"MySvc", "my_svc", "1abc", "x", "a-very-long-service-name-over-32-chars"} {
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
