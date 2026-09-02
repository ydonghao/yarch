// yarch-init 工程生成器（cookiecutter / maven-archetype 模式的 golang 对偶）：
// 模板（_template/，带 {{.Var}} 占位的声明式资产，不要求自身可编译）
// + 通用渲染引擎（本程序，text/template）+ 变量声明（archetype.json）。
// 工程正确性由「生成后冒烟」保证（CI：生成 → tidy → build → test）。
//
// 使用（本仓未发版阶段）：
//
//	cd yarch/stacks/golang
//	go run ./cmd/yarch-init -module github.com/you/your-svc -out ~/code/your-svc
//
// yarch-go 发 tag 后无需 clone 本仓：
//
//	go run github.com/yuandonghao/yarch/stacks/golang/cmd/yarch-init@vX.Y.Z -module … -out …
package main

import (
	"encoding/json"
	"flag"
	"fmt"
	"io/fs"
	"os"
	"path/filepath"
	"regexp"
	"strings"
	"text/template"
)

// 渲染时跳过的模板资产（变量声明与说明，不进生成工程）。
var skipFiles = map[string]bool{
	"archetype.json": true,
}

// Variables archetype.json 的变量声明（对偶 archetype-metadata.xml）。
type Variables struct {
	Description string `json:"description"`
	Variables   []struct {
		Name     string `json:"name"`
		Desc     string `json:"desc"`
		Required bool   `json:"required"`
		Default  string `json:"default"`
	} `json:"variables"`
}

var servicePattern = regexp.MustCompile(`^[a-z][a-z0-9-]{1,31}$`)

// 禁裸通用词（registry.md 一-1 口径摘录；完整表以 contract/registry.md 为准）。
var genericWords = map[string]bool{
	"api": true, "app": true, "service": true, "server": true, "backend": true,
	"web": true, "admin": true, "main": true, "common": true, "system": true, "demo": true,
}

// vars 渲染变量集（模板 {{.Module}} / {{.Service}} / {{.ServiceSnake}} /
// {{.YarchVersion}} / {{.ReplaceLine}}）。
type vars struct {
	Module       string
	Service      string
	ServiceSnake string
	YarchVersion string
	ReplaceLine  string
}

func main() {
	var (
		module = flag.String("module", "", "新工程 module path（必填，如 github.com/you/your-svc）")
		out    = flag.String("out", "", "输出目录（必填，须不存在或为空）")
		src    = flag.String("src", "./_template", "模板目录（默认本仓 stacks/golang/_template）")
	)
	flag.Parse()

	if *module == "" || *out == "" {
		flag.Usage()
		os.Exit(2)
	}
	service := filepath.Base(*module)
	if !servicePattern.MatchString(service) {
		fatal("服务名 %q 须过 registry.md 一-1 校验：^[a-z][a-z0-9-]{1,31}$", service)
	}
	if genericWords[service] {
		fatal("服务名 %q 是裸通用词，禁止使用（见 contract/registry.md 一-1）", service)
	}
	if entries, _ := os.ReadDir(*out); len(entries) > 0 {
		fatal("输出目录 %s 非空", *out)
	}
	if _, err := os.Stat(*src); err != nil {
		fatal("模板目录不可达：%v（在 yarch 仓 stacks/golang/ 下运行，或用 -src 指定）", err)
	}

	absSrc, _ := filepath.Abs(*src)
	v := vars{
		Module:       *module,
		Service:      service,
		ServiceSnake: strings.ReplaceAll(service, "-", "_"),
		YarchVersion: "v0.0.0",
		// 发版前 replace 指向本机平台源码；正式发版后由 -replace 换 tag（见下）
		ReplaceLine: "\nreplace github.com/yuandonghao/yarch/stacks/golang => " + filepath.Dir(absSrc),
	}

	n, err := render(*src, *out, v)
	if err != nil {
		fatal("生成失败：%v", err)
	}

	fmt.Printf(`✅ 已生成 %s（%d 个文件）—— %s

下一步：
  1. cd %s && docker compose up -d && go mod tidy && go run .
  2. service 已置为 %q——去 yarch 仓 contract/registry.md 登记
  3. types/errno 业务码段（3xxx+）在你的仓库 docs 登记后方可使用
  4. yarch-go 正式发版后：删除 go.mod 的 replace 行、版本改正式 tag（升级 = go get 升版）
`, *out, n, loadDescription(*src), *out, service)
}

// render 通用渲染引擎：遍历模板树，逐文件 text/template 渲染。
// 模板中未命中变量的文件原样复制；archetype.json 不进生成物。
func render(src, dst string, v vars) (int, error) {
	tmpl, err := template.New("yarch").Parse("")
	if err != nil {
		return 0, err
	}
	n := 0
	err = filepath.WalkDir(src, func(path string, d fs.DirEntry, err error) error {
		if err != nil {
			return err
		}
		rel, _ := filepath.Rel(src, path)
		target := filepath.Join(dst, rel)
		if d.IsDir() {
			return os.MkdirAll(target, 0o755)
		}
		if skipFiles[d.Name()] {
			return nil
		}
		b, err := os.ReadFile(path)
		if err != nil {
			return err
		}
		t, err := tmpl.Parse(string(b))
		if err != nil {
			return fmt.Errorf("%s: %w", rel, err)
		}
		var sb strings.Builder
		if err := t.Execute(&sb, v); err != nil {
			return fmt.Errorf("%s: %w", rel, err)
		}
		if err := os.WriteFile(target, []byte(sb.String()), 0o644); err != nil {
			return err
		}
		n++
		return nil
	})
	return n, err
}

func loadDescription(src string) string {
	b, err := os.ReadFile(filepath.Join(src, "archetype.json"))
	if err != nil {
		return ""
	}
	var vs Variables
	if json.Unmarshal(b, &vs) == nil && vs.Description != "" {
		return vs.Description
	}
	return ""
}

func fatal(format string, args ...any) {
	fmt.Fprintf(os.Stderr, "yarch-init: "+format+"\n", args...)
	os.Exit(1)
}
